(ns notif-test.repository.memory
  "In-memory repository implementations and seed data."
  (:require [notif-test.repository.protocols :as p]
            [notif-test.domain.models :as m]
            [clojure.set :as set]))

(def seed-users
  [{:id 1 :name "Alice" :email "alice@example.com" :phone "+15550000001"
    :subscribed [:sports :finance] :preferred-channels [:email :sms]}
   {:id 2 :name "Bob" :email "bob@example.com" :phone "+15550000002"
    :subscribed [:movies] :preferred-channels [:push]}
   {:id 3 :name "Carol" :email "carol@example.com" :phone "+15550000003"
    :subscribed [:sports :movies] :preferred-channels [:email :push]}
   {:id 4 :name "Dave" :email "dave@example.com" :phone "+15550000004"
    :subscribed [:finance] :preferred-channels [:sms]}])

(defn- validate-users [users]
  (mapv m/->User users))

(defrecord InMemoryUsers [!users]
  p/UserRepository
  (all-users [_] @!users)
  (find-user [_ id] (first (filter #(= (:id %) id) @!users)))
  (users-subscribed-to [_ category]
    (filter (fn [{:keys [subscribed]}]
              (some #{category} subscribed))
            @!users)))

(defn users-repo
  ([] (users-repo seed-users))
  ([users]
   (->InMemoryUsers (atom (validate-users users)))))

(defrecord InMemoryMessages [!next-id !messages]
  p/MessageRepository
  (next-id [_]
    (let [id @!next-id]
      (swap! !next-id inc)
      id))
  (save-message [_ message]
    (swap! !messages conj message)
    message)
  (all-messages [_] @!messages))

(defn messages-repo []
  (->InMemoryMessages (atom 1) (atom [])))

(defrecord InMemoryLogs [!logs]
  p/NotificationLogRepository
  (append-log [_ log]
    (swap! !logs conj log)
    log)
  (all-logs [_] (->> @!logs (sort-by :timestamp) (reverse)))
  (logs-by-message [_ message-id]
    (filter #(= (:message-id %) message-id) @!logs)))

(defn logs-repo []
  (->InMemoryLogs (atom [])))

