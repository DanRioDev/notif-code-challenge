(defproject notif-test "0.1.0-SNAPSHOT"
  :description "Notification system with channels (Email/SMS/Push), strategy pattern, repositories, services, and web UI"
  :url "https://example.com/notif-test"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-jetty-adapter "1.12.2"]
                 [metosin/reitit "0.9.1"]
                 [hiccup "2.0.0-RC3"]
                 [cheshire "5.13.0"]
                 ;; Postgres + JDBC + Pool
                 [com.github.seancorfield/next.jdbc "1.3.939"]
                 [org.postgresql/postgresql "42.7.4"]
                 [com.zaxxer/HikariCP "5.1.0"]
                 ;; Migrations
                 [migratus "1.5.9"]]
  :plugins [[migratus-lein "0.7.3"]
            [lein-environ/lein-environ "1.2.0"]]
  :migratus {:store :database
             :migration-dir "resources/migrations"
             :db {:connection-uri ~(or (System/getenv "DATABASE_URL")
                                       "jdbc:postgresql://localhost:5432/notif_test?user=notif&password=secret")}}
  :profiles {:dev {:dependencies [[cider/cider-nrepl "0.42.1"]]}}
  :aliases {"db:seed"  ["run" "-m" "notif-test.db.seed"]
            "db:clean" ["run" "-m" "notif-test.db.clean"]}
  :repl-options {:init-ns notif-test.core})


