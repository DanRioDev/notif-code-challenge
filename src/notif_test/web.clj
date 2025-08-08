(ns notif-test.web
  (:require [reitit.ring :as ring]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [cheshire.core :as json]
            [clojure.string :as str]
            [notif-test.config :as config]
            [notif-test.repository.postgres :as pg]
            [notif-test.repository.memory :as mem]
            [notif-test.repository.protocols :as p]
            [notif-test.notification.channels :as ch]
            [notif-test.service.notification-service :as svc]
            [notif-test.service.dispatcher :as disp]))

;; Simple JSON helpers
(defn- json-response
  ([data] (json-response data 200))
  ([data status]
   (-> (resp/response (json/generate-string data))
       (resp/status status)
       (resp/header "Content-Type" "application/json; charset=utf-8"))))

(defn- parse-json-body [req]
  (when-let [body (:body req)]
    (let [s (slurp body)]
      (when (not (str/blank? s))
        (json/parse-string s true)))))

;; In-memory application dependencies for dev usage
(defonce !deps
  (atom (let [ds (config/datasource)
              logs-backend (or (System/getenv "LOGS_BACKEND") "memory")
              logs-repo (if (= (str/lower-case logs-backend) "postgres")
                          (pg/logs-repo ds)
                          (mem/logs-repo))
              notifiers (ch/build-registry)]
          {:users (pg/users-repo ds)
           :messages (pg/messages-repo ds)
           :logs logs-repo
           :notifiers notifiers})))

(defn- handle-index [_]
  ;; Serve the SPA index from resources/public
  (or (resp/resource-response "index.html" {:root "public"})
      (-> (resp/response "Not found") (resp/status 404))))

(defn- handle-get-logs [_]
  (let [{:keys [logs]} @!deps
        items (p/all-logs logs)]
    (json-response items)))

(defonce !dispatcher
  ;; Optional async dispatcher controlled by SEND_ASYNC env var
  (let [flag (some-> (System/getenv "SEND_ASYNC") (str/lower-case))
        workers (some-> (System/getenv "SEND_WORKERS") (Integer/parseInt))
        buffer (some-> (System/getenv "SEND_BUFFER") (Integer/parseInt))]
    (when (= flag "true")
      (disp/start-dispatcher {:get-deps #(deref !deps)
                              :workers (or workers 2)
                              :buffer (or buffer 100)}))))

(defn- handle-post-message [req]
  (try
    (let [body (or (parse-json-body req) {})
          ;; accept either :message-body or camelCase :messageBody
          message-body (or (:message-body body) (:messageBody body))
          ;; accept keyword or string category
          category (let [c (:category body)]
                     (cond
                       (keyword? c) c
                       (string? c) (keyword c)
                       :else c))]
      (if !dispatcher
        ;; Async: enqueue and return 202 Accepted with ack
        (do ((:enqueue !dispatcher) {:category category :message-body message-body})
            (json-response {:status "enqueued"} 202))
        ;; Sync: directly submit and return result
        (let [result (svc/submit-message! @!deps {:category category :message-body message-body})]
          (json-response result))))
    (catch clojure.lang.ExceptionInfo e
      (json-response {:error (.getMessage e)
                      :data (ex-data e)} 400))
    (catch Throwable t
      (json-response {:error (.getMessage t)} 500))))

(def router
  (ring/router
   [["/" {:get handle-index}]
    ["/api"
     ["/logs" {:get handle-get-logs}]
     ["/messages" {:post handle-post-message}]]]))

(def app
  ;; Provide static file serving and sensible defaults for non-matched routes
  (ring/ring-handler
   router
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))))

(defn -main
  "Start Jetty HTTP server. Controlled by PORT env var (default 3000)."
  [& _]
  (let [port (try (Integer/parseInt (or (System/getenv "PORT") "3000")) (catch Throwable _ 3000))]
    (jetty/run-jetty app {:port port :join? false})))

