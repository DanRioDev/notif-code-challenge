(ns notif-test.service.notification-service-test
  (:require [clojure.test :refer :all]
            [notif-test.repository.memory :as mem]
            [notif-test.repository.protocols :as p]
            [notif-test.service.notification-service :as svc]
            [notif-test.notification.protocols :as proto]
            [notif-test.notification.channels :as ch]))

(defn fresh-deps []
  {:users (mem/users-repo)
   :messages (mem/messages-repo)
   :logs (mem/logs-repo)})

(deftest submit-message-happy-path
  (let [deps (fresh-deps)
        res (svc/submit-message! deps {:category :sports :message-body "Hello"})]
    (is (= 1 (:message-id (:message res))))
    (is (seq (:results res)))
    (is (every? #(= :success (:status %)) (:results res)))
    (is (> (count (p/all-logs (:logs deps))) 0))))

(deftest invalid-category-throws
  (let [deps (fresh-deps)]
    (is (thrown? Exception (svc/submit-message! deps {:category :unknown :message-body "Hello"}))))
  (let [deps (fresh-deps)]
    (is (thrown? Exception (svc/submit-message! deps {:category :sports :message-body "  "})))))

(deftest filter-by-subscription
  (let [deps (fresh-deps)]
    ;; Submit a movies message so logs capture targeted users
    (svc/submit-message! deps {:category :movies :message-body "Test"})
    (let [targeted-users (->> (p/all-logs (:logs deps)) (map (comp :id :user)) set)]
      ;; Seed users subscribed to movies: Bob(id 2), Carol(id 3)
      (is (= targeted-users #{2 3})))))

(deftest channel-failure-retry
  (let [deps (fresh-deps)
        failing (reify proto/Notifier
                  (send! [_ _ _] {:status :failed :info "fail" :error "simulated"})
                  (channel-type [_] :sms))]
    (with-redefs [ch/notifier-for (fn [_] failing)]
      (let [res (svc/submit-message! deps {:category :finance :message-body "$"})]
        (is (every? #(= :failed (:status %)) (:results res)))))))

