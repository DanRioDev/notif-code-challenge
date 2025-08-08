(ns notif-test.notification.channels
  "Concrete channel implementations. Real integrations can replace the body of send!."
  (:require [notif-test.notification.protocols :as proto]))

(defrecord SMSNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    ;; Simulated send. In real world, integrate SMS provider here.
    {:status :success :info (str "SMS to " (:phone user) ": " message-body) :error nil})
  (channel-type [_] :sms))

(defrecord EmailNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    {:status :success :info (str "Email to " (:email user) ": " message-body) :error nil})
  (channel-type [_] :email))

(defrecord PushNotifier []
  proto/Notifier
  (send! [_ user {:keys [message-body]}]
    {:status :success :info (str "Push to user#" (:id user) ": " message-body) :error nil})
  (channel-type [_] :push))

(defn notifier-for
  "Return a notifier instance for the given channel, or nil if unsupported."
  [channel]
  (case channel
    :sms (->SMSNotifier)
    :email (->EmailNotifier)
    :push (->PushNotifier)
    nil))

