(ns notif-test.service.notification-service
  "Application service for message submission and notification dispatch."
  (:require [notif-test.domain.models :as m]
            [notif-test.repository.protocols :as repo]
            [notif-test.notification.channels :as ch]
            [notif-test.notification.protocols :as proto]
            [clojure.string :as str]))

(def ^:private max-retries 2)
(def ^:private base-backoff-ms 100)

(defn- now [] (java.util.Date.))

(defn- pause-ms
  "Sleep for given milliseconds. Separated for test stubbing."
  [ms]
  (when (pos? ms)
    (Thread/sleep ms)))

(defn- backoff-ms
  "Compute exponential backoff with jitter. attempt starts at 0."
  [attempt]
  (let [exp (* base-backoff-ms (Math/pow 2 attempt))
        jitter (rand-int 50)]
    (long (+ exp jitter))))

(defn- retryable?
  "Classify whether a failed response should be retried. Prefer explicit :retryable? flag; otherwise retry on exceptions/timeouts and 5xx/429 provider codes."
  [{:keys [retryable? info error code]}]
  (let [code (when code (int code))]
    (boolean
     (or retryable?
         (= info "exception")
         ;; Retry on 5xx and 429 Too Many Requests
         (some-> code (or 0) (#(or (>= % 500) (= % 429))))
         ;; Fallback: textual hints
         (when error
           (re-find #"timeout|temporar|5\d\d" (str/lower-case error)))))))

(defn- try-send
  "Attempt sending via a notifier with exponential backoff + jitter. Returns {:status :success|:failed :info :error :attempt n}."
  [notifier user message]
  (loop [attempt 0]
    (let [{:keys [status] :as resp}
          (try
            (proto/send! notifier user message)
            (catch Throwable t
              {:status :failed :info "exception" :error (.getMessage t)}))
          done? (or (= status :success)
                    (>= attempt max-retries)
                    (not (retryable? resp)))]
      (if done?
        (assoc resp :attempt (inc attempt))
        (do (pause-ms (backoff-ms attempt))
            (recur (inc attempt)))))))

(defn submit-message!
  "Validate and persist message, then dispatch to eligible users' preferred channels.
   deps expects keys {:users :messages :logs :notifiers} implementing repository protocols and a notifier registry map.
   Returns {:message <saved-message> :results [ { :user-id .. :channel .. :status .. :log-id .. } ... ]}."
  [{:keys [users messages logs notifiers]} {:keys [category message-body]}]
  ;; Validate input DTO
  (when-not (contains? m/categories category)
    (throw (ex-info "Invalid category" {:category category :allowed m/categories})))
  (when-not (m/non-blank-string? message-body)
    (throw (ex-info "Message body must be non-empty" {})))
  ;; Create and store message
  (let [id (repo/next-id messages)
        message (m/->Message {:message-id id :message-category category :message-body message-body})
        _ (repo/save-message messages message)
        subs (repo/users-subscribed-to users category)
        results (transient [])]
    (doseq [user subs
            channel (:preferred-channels user)]
      (let [; Prefer provided notifiers registry; otherwise fall back to channel lookup
            ; Wrap channel lookup to prevent throws for unsupported channels
            notifier (try
                       (or (get notifiers channel)
                           (ch/notifier-for channel))
                       (catch clojure.lang.ExceptionInfo e
                         (when (= "Unsupported channel" (.getMessage e))
                           nil)))
            ; Do NOT throw on unsupported channels; record a failed attempt instead so processing continues
            send-resp (if (nil? notifier)
                        {:status :failed :info "unsupported-channel" :error "Unsupported channel"}
                        (try-send notifier user message))
            status (:status send-resp)
            log (m/->NotificationLog {:log-id (java.util.UUID/randomUUID)
                                      :message-id (:message-id message)
                                      :category category
                                      :channel channel
                                      :user user
                                      :timestamp (now)
                                      :notification-status status
                                      :error (:error send-resp)})
            stored (repo/append-log logs log)]
        (conj! results {:user-id (:id user)
                        :channel channel
                        :status status
                        :log-id (:id stored)})))
    {:message message
     :results (persistent! results)}))

