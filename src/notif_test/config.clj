(ns notif-test.config
  "Configuration and database wiring.
  Reads environment variables and constructs a shared HikariCP datasource
  and migratus configuration map."
  (:require [next.jdbc :as jdbc])
  (:import (com.zaxxer.hikari HikariConfig HikariDataSource)))

(defn getenv
  "Get environment variable or fallback to default."
  [k default]
  (or (System/getenv k) default))

(def ^:private default-db-url
  "jdbc:postgresql://localhost:5432/notif_test?user=notif&password=secret")

(defn jdbc-url
  "Returns the JDBC URL from env or sensible default for local dev."
  []
  (getenv "DATABASE_URL" default-db-url))

(defn ^HikariDataSource make-datasource
  "Create a HikariCP datasource for the given JDBC URL."
  [jdbc-url]
  (let [cfg (doto (HikariConfig.)
              (.setJdbcUrl jdbc-url)
              (.setMaximumPoolSize 10)
              (.setPoolName "notif-test-pool"))]
    (HikariDataSource. cfg)))

(defonce ^:private datasource*
  (delay (make-datasource (jdbc-url))))

(defn datasource
  "Returns the shared application datasource."
  ^HikariDataSource []
  @datasource*)

(defn close-datasource!
  "Closes the shared datasource. Intended to be called on application shutdown."
  []
  (when (realized? datasource*)
    (.close ^HikariDataSource @datasource*)))

(def migratus-config
  "Migratus configuration map using the same DATABASE_URL."
  {:store :database
   :migration-dir "resources/migrations"
   :db {:connection-uri (jdbc-url)}})
