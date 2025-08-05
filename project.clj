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
                 [cheshire "5.13.0"]]
  :plugins []
  :profiles {:dev {:dependencies [[cider/cider-nrepl "0.42.1"]]}}
  :repl-options {:init-ns notif-test.core})


