(ns notif-test.repository.postgres
  "Postgres-backed repository implementations using next.jdbc."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [notif-test.repository.protocols :as repo]))

;; Helpers
(defn- ->kw
  "Convert a possibly string status/channel to a keyword."
  [x]
  (when x (keyword x)))

(defn- ->vec
  "Ensure value is a Clojure vector (for Postgres arrays)."
  [x]
  (cond
    (nil? x) []
    (vector? x) x
    (sequential? x) (vec x)
    :else [x]))

(defn- to-kw-vec
  "Coerce a DB value (possibly array) into a vector of keywords."
  [xs]
  (->> (->vec xs)
       (map (comp keyword str))
       (vec)))

(defn- row->user
  "Map a SQL row into the domain user shape."
  [{:keys [id name email phone subscribed_categories preferred_channels] :as _row}]
  {:id id
   :name name
   :email email
   :phone phone
   :subscribed (to-kw-vec subscribed_categories)
   :preferred-channels (to-kw-vec preferred_channels)})

(defn- row->message
  "Map a SQL row into the domain message shape."
  [{:keys [id category content] :as _row}]
  {:message-id id
   :message-category (->kw category)
   :message-body content})

;; Users Repository
(defrecord PostgresUsers [ds]
  repo/UserRepository
  (all-users [_]
    (let [rows (jdbc/execute! ds ["SELECT id, name, email, phone, subscribed_categories, preferred_channels FROM users ORDER BY id ASC"])]
      (map row->user rows)))
  (find-user [_ id]
    (when-let [row (jdbc/execute-one! ds ["SELECT id, name, email, phone, subscribed_categories, preferred_channels FROM users WHERE id = ?" id])]
      (row->user row)))
  (users-subscribed-to [_ category]
    (let [rows (jdbc/execute! ds ["SELECT id, name, email, phone, subscribed_categories, preferred_channels FROM users WHERE ? = ANY(subscribed_categories)" (name category)])]
      (map row->user rows))))

(defn users-repo [ds]
  (->PostgresUsers ds))

;; Messages Repository
(defrecord PostgresMessages [ds]
  repo/MessageRepository
  (next-id [_]
    (-> (jdbc/execute-one! ds ["SELECT nextval('messages_id_seq') AS id"]) :id))
  (save-message [_ {:keys [message-id message-category message-body] :as message}]
    (let [row (cond-> {:category (name message-category)
                       :content message-body}
                message-id (assoc :id message-id))]
      (sql/insert! ds :messages row {:return-keys true})
      ;; Return the domain-shaped message we were asked to save
      message))
  (all-messages [_]
    (->> (jdbc/execute! ds ["SELECT id, category, content FROM messages ORDER BY id DESC"])
         (map row->message)
         (vec))))

(defn messages-repo [ds]
  (->PostgresMessages ds))

;; Notification Logs Repository
(defrecord PostgresLogs [ds]
  repo/NotificationLogRepository
  (append-log [_ {:keys [message_id channel status error]}]
    (let [row {:message_id message_id
               :channel (name channel)
               :status (name status)
               :error error}]
      (sql/insert! ds :notification_logs row {:return-keys true})))
  (all-logs [_]
    (let [rows (jdbc/execute! ds ["SELECT id, message_id, channel, status, error, created_at FROM notification_logs ORDER BY id DESC"])]
      (map (fn [r] (-> r (update :channel ->kw) (update :status ->kw))) rows)))
  (logs-by-message [_ message-id]
    (let [rows (jdbc/execute! ds ["SELECT id, message_id, channel, status, error, created_at FROM notification_logs WHERE message_id = ? ORDER BY id DESC" message-id])]
      (map (fn [r] (-> r (update :channel ->kw) (update :status ->kw))) rows))))

(defn logs-repo [ds]
  (->PostgresLogs ds))
