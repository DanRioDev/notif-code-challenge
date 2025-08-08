(ns notif-test.repository.protocols
  "Repository interfaces as protocols to allow multiple implementations (in-memory, SQL, etc.).")

(defprotocol UserRepository
  (all-users [this])
  (find-user [this id])
  (users-subscribed-to [this category]))

(defprotocol MessageRepository
  (save-message [this message])
  (all-messages [this]))

(defprotocol NotificationLogRepository
  (append-log [this log])
  (all-logs [this])
  (logs-by-message [this message-id]))

