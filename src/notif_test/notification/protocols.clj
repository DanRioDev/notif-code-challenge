(ns notif-test.notification.protocols
  "Notification channel abstraction.")

(defprotocol Notifier
  (send! [this user message]
    "Send the message to the user via the underlying channel.
     Must return a map {:status :success|:failed :info "..." :error nil|"..."}.")
  (channel-type [this]
    "Return the keyword of this channel, e.g., :sms, :email, :push."))

