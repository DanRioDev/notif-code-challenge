(ns notif-test.repository.postgres
  "Postgres-backed repository implementations using next.jdbc."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [notif-test.repository.protocols :as repo]
            [cheshire.core :as cheshire]))

;; Helpers
(defn- ->kw
  "Convert a possibly string status/channel to a keyword."
  [x]
  (when x (keyword x)))

(defn- to-kw-vec
  "Coerce a Postgres JSONB column (PGobject/String) into a Clojure vector of keywords."
  [xs]
  (let [json-str (cond
                   (instance? org.postgresql.util.PGobject xs) (.getValue xs)
                   (string? xs) xs
                   (sequential? xs) xs
                   :else nil)]
    (if (or (nil? json-str) (sequential? json-str))
      (mapv keyword (or json-str []))
      (->> (cheshire.core/parse-string json-str)
           (map keyword)
           (vec)))))

(to-kw-vec ["sports", "movies"])

(defn- row->user
  "Map a SQL row into the domain user shape."
  [{:keys [id name email phone subscribed preferred_channels] :as _row}]
  {:id id
   :name name
   :email email
   :phone phone
   :subscribed (to-kw-vec subscribed)
   :preferred-channels (to-kw-vec preferred_channels)})

(defn- row->message
  "Map a SQL row into the domain message shape."
  [{:keys [id user-id category content] :as _row}]
  {:message-id id
   :user-id user-id
   :message-category (->kw category)
   :message-body content})

;; Users Repository
(defrecord PostgresUsers [ds]
  repo/UserRepository
  (all-users [_]
    (let [rows (jdbc/execute! ds ["SELECT id, name, email, phone, subscribed, preferred_channels FROM users ORDER BY id ASC"])]
      (map row->user rows)))
  (find-user [_ id]
    (when-let [row (jdbc/execute-one! ds ["SELECT id, name, email, phone, subscribed, preferred_channels FROM users WHERE id = ?" id])]
      (row->user row)))
  (users-subscribed-to [_ category]
    (let [rows (jdbc/execute! ds ["SELECT id, name, email, phone, subscribed, preferred_channels FROM users WHERE subscribed ?? ?" (name category)])]
      (map row->user rows))))

(defn users-repo [ds]
  (->PostgresUsers ds))

;; Messages Repository
(defrecord PostgresMessages [ds]
  repo/MessageRepository
  (save-message [_ {:keys [user-id message-id message-category message-body]}]
    (let [row (cond-> {:category (name message-category)
                       :content message-body}
                message-id (assoc :id message-id)
                user-id (assoc :id user-id))
          inserted (sql/insert! ds :messages row {:return-keys true})]
      ;; Return the domain-shaped message with the generated UUID
      (row->message inserted)))
  (all-messages [_]
    (->> (jdbc/execute! ds ["SELECT id, category, content FROM messages ORDER BY id DESC"])
         (map row->message)
         (vec))))

(defn messages-repo [ds]
  (->PostgresMessages ds))

;; Notification Logs Repository
(defrecord PostgresLogs [ds]
  repo/NotificationLogRepository
  (append-log [_ {:keys [message-id channel notification-status error]}]
    (let [row {:message_id message-id
               :channel (name channel)
               :status (name notification-status)
               :error error}]
      (sql/insert! ds :notification_logs row {:return-keys true})))
  (all-logs [_]
    (let [rows (jdbc/execute! ds ["SELECT * FROM notification_logs ORDER BY id DESC"])]
      (map (fn [r] (-> r (update :channel ->kw) (update :status ->kw))) rows)))
  (logs-by-message [_ message-id]
    (let [rows (jdbc/execute! ds ["SELECT * FROM notification_logs WHERE message_id = ? ORDER BY id DESC" message-id])]
      (map (fn [r] (-> r (update :channel ->kw) (update :status ->kw))) rows))))

(defn logs-repo [ds]
  (->PostgresLogs ds))
