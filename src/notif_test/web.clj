(ns notif-test.web
  (:require [reitit.ring :as ring]
            [ring.util.response :as resp]
            [cheshire.core :as json]
            [clojure.string :as str]
            [notif-test.config :as config]
            [notif-test.repository.postgres :as pg]
            [notif-test.repository.memory :as mem]
            [notif-test.repository.protocols :as p]
            [notif-test.service.notification-service :as svc]))

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
  (atom (let [ds (config/datasource)]
          {:users (pg/users-repo ds)
           :messages (pg/messages-repo ds)
           :logs (pg/logs-repo ds)
           ;; optional notifier registry; service will fallback to channel registry
           :notifiers nil})))

(defn- handle-index [_]
  ;; Serve the SPA index from resources/public
  (or (resp/resource-response "index.html" {:root "public"})
      (-> (resp/response "Not found") (resp/status 404))))

(defn- handle-get-logs [_]
  (let [{:keys [logs]} @!deps
        items (p/all-logs logs)]
    (json-response items)))

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
                       :else c))
          result (svc/submit-message! @!deps {:category category :message-body message-body})]
      (json-response result))
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

