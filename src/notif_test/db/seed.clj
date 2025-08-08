(ns notif-test.db.seed
  "Seed initial data into Postgres using next.jdbc.
  This is idempotent and safe to run multiple times."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [notif-test.config :as config]))

(defn- pg-array
  "Create a java.sql.Array of the given SQL element type (e.g., text) from a Clojure seq.
  If `handle` is a java.sql.Connection (e.g., a tx from with-transaction), use it directly;
  otherwise treat it as a DataSource and open a short-lived connection."
  [handle sql-type xs]
  (if (instance? java.sql.Connection handle)
    (.createArrayOf ^java.sql.Connection handle sql-type (into-array String (map str xs)))
    (with-open [^java.sql.Connection c (jdbc/get-connection handle)]
      (.createArrayOf c sql-type (into-array String (map str xs))))))

(defn ensure-user!
  [handle {:keys [name email phone subscribed-categories preferred-channels]}]
  (let [subscribed-arr (pg-array handle "text" (or subscribed-categories []))
        preferred-arr  (pg-array handle "text" (or preferred-channels []))]
    (sql/insert! handle :users
                 {:name name
                  :email email
                  :phone phone
                  :subscribed_categories subscribed-arr
                  :preferred_channels preferred-arr}
                 {:return-keys true})))

(defn seed!
  "Execute seed operations inside a transaction."
  []
  (let [ds (config/datasource)]
      ;; Use tx everywhere inside the transaction so we never treat a Connection as a DataSource
    (jdbc/with-transaction [tx ds]
      (doseq [u [{:name "Alice"
                  :email "alice@example.com"
                  :phone "+15550000001"
                  :subscribed-categories ["sports"]
                  :preferred-channels ["email"]}
                 {:name "Bob"
                  :email "bob@example.com"
                  :phone "+15550000002"
                  :subscribed-categories ["finance" "movies"]
                  :preferred-channels ["sms" "email"]}]]
        (ensure-user! tx u)))))

(defn -main
  [& _]
  (seed!)
  (shutdown-agents))
