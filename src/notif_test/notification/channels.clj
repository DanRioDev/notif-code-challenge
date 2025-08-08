(ns notif-test.notification.channels
  "Concrete channel implementations. Real integrations can replace the body of send!."
  (:require [notif-test.notification.protocols :as proto]))

(defrecord SMSNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    ;; Simulated send. In real world, integrate SMS provider here.
    {:status :success :code 200 :info (str "SMS to " (:phone user) ": " message-body) :error nil})
  (channel-type [_] :sms))

(defrecord EmailNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    {:status :success :code 200 :info (str "Email to " (:email user) ": " message-body) :error nil})
  (channel-type [_] :email))

(defrecord PushNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    {:status :success :code 200 :info (str "Push to user#" (:id user) ": " message-body) :error nil})
  (channel-type [_] :push))

(defn notifier-for [channel]
  (case channel
    :sms (->SMSNotifier)
    :email (->EmailNotifier)
    :push (->PushNotifier)
    (throw (ex-info "Unsupported channel" {:channel channel}))))

(defn build-registry
  "Construct a notifier registry from environment configuration. In a real setup, credentials
  (e.g., SMS_API_KEY, EMAIL_SMTP_URL, PUSH_TOKEN) would be wired into concrete implementations.
  For now, we return basic notifiers keyed by channel."
  []
  {:sms (->SMSNotifier)
   :email (->EmailNotifier)
   :push (->PushNotifier)})

