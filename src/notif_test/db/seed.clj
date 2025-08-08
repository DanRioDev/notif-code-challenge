(ns notif-test.db.seed
  "Seed initial data into Postgres using next.jdbc.
  This is idempotent and safe to run multiple times."
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [notif-test.config :as config]
            [cheshire.core :as json])
  (:import (org.postgresql.util PGobject)))


(defn to-jsonb [data]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/generate-string data))))

(defn ensure-user!
  [handle {:keys [name email phone subscribed preferred-channels]}]
  (sql/insert! handle :users
               {:name name
                :email email
                :phone phone
                :subscribed (to-jsonb (or subscribed []))
                :preferred_channels (to-jsonb (or preferred-channels []))}
               {:return-keys true}))

(defn seed!
  "Execute seed operations inside a transaction."
  []
  (let [ds (config/datasource)]
      ;; Use tx everywhere inside the transaction so we never treat a Connection as a DataSource
    (jdbc/with-transaction [tx ds]
      (doseq [u [{:name "Alice"
                  :email "alice@example.com"
                  :subscribed ["sports"]
                  :preferred-channels ["email"]}
                 {:name "Bob"
                  :email "bob@example.com"
                  :subscribed ["finance" "movies"]
                  :preferred-channels ["sms" "email"]}
                 {:name "Carol"
                  :email "carol@example.com" :phone "+15550000003"
                  :subscribed ["sports" "movies"]
                  :preferred-channels ["email" "push"]}
                 {:name "Dave"
                  :email "dave@example.com" :phone "+15550000004"
                  :subscribed ["finance"]
                  :preferred-channels ["sms"]}]]
        (ensure-user! tx u)))))

(defn -main
  [& _]
  (seed!)
  (shutdown-agents))
