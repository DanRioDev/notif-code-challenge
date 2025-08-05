(ns notif-test.service.notification-service
  "Application service for message submission and notification dispatch."
  (:require [notif-test.domain.models :as m]
            [notif-test.repository.protocols :as repo]
            [notif-test.notification.channels :as ch]
            [notif-test.notification.protocols :as proto]))

(def ^:private max-retries 2)

(defn- now [] (java.util.Date.))

(defn- try-send
  "Attempt sending via a notifier with rudimentary retry. Returns {:status :success|:failed :info :error}."
  [notifier user message]
  (loop [attempt 0]
    (let [{:keys [status] :as resp}
          (try
            (proto/send! notifier user message)
            (catch Throwable t
              {:status :failed :info "exception" :error (.getMessage t)}))]
      (if (or (= status :success) (>= attempt max-retries))
        (assoc resp :attempt (inc attempt))
        (recur (inc attempt))))))

(defn submit-message!
  "Validate and persist message, then dispatch to eligible users' preferred channels.
   deps expects keys {:users :messages :logs} implementing repository protocols.
   Returns {:message <saved-message> :results [ { :user-id .. :channel .. :status .. :log-id .. } ... ]}."
  [{:keys [users messages logs]} {:keys [category message-body] :as req}]
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
      (let [notifier (ch/notifier-for channel)
            send-resp (try-send notifier user message)
            status (:status send-resp)
            log (m/->NotificationLog {:id (repo/next-id messages) ; use messages' seq for simplicity
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

