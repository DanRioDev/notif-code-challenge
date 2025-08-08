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

(deftest unsupported-channel-handled
  ;; Use a valid user but make notifier lookup throw Unsupported channel
  (let [users (reify p/UserRepository
                (all-users [_] [])
                (find-user [_ _] nil)
                (users-subscribed-to [_ _]
                  [{:id 999 :name "X" :email "x@ex" :phone "+10000001"
                    :subscribed [:sports] :preferred-channels [:email]}]))
        deps {:users users :messages (mem/messages-repo) :logs (mem/logs-repo)}]
    (with-redefs [ch/notifier-for (fn [_] (throw (ex-info "Unsupported channel" {:channel :email})))]
      (let [res (svc/submit-message! deps {:category :sports :message-body "Hi"})]
        (is (= 1 (count (:results res))))
        (is (= :failed (:status (first (:results res)))))
        ;; log error captured
        (let [logs (p/all-logs (:logs deps))]
          (is (= 1 (count logs)))
          (is (= "Unsupported channel" (:error (first logs)))))))))

(deftest no-subscribers-yields-no-results
  (let [users (reify p/UserRepository
                (all-users [_] [])
                (find-user [_ _] nil)
                (users-subscribed-to [_ _] []))
        deps {:users users :messages (mem/messages-repo) :logs (mem/logs-repo)}
        res (svc/submit-message! deps {:category :movies :message-body "Hi"})]
    (is (empty? (:results res)))
    (is (empty? (p/all-logs (:logs deps))))))

(deftest very-long-message-body
  (let [deps (fresh-deps)
        long-msg (apply str (repeat 50000 "x"))
        res (svc/submit-message! deps {:category :sports :message-body long-msg})]
    (is (= (:message-body (:message res)) long-msg))
    (is (seq (:results res)))))

(deftest retry-stops-at-max
  ;; Custom users repo with a single user/channel to make assertions deterministic
  (let [users (reify p/UserRepository
                (all-users [_] [])
                (find-user [_ _] nil)
                (users-subscribed-to [_ _]
                  [{:id 1 :name "Y" :email "y@ex" :phone "+10000002"
                    :subscribed [:finance] :preferred-channels [:sms]}]))
        deps {:users users :messages (mem/messages-repo) :logs (mem/logs-repo)}
        calls (atom 0)
        failing (reify proto/Notifier
                  (send! [_ _ _] (swap! calls inc) {:status :failed :info "fail" :error "timeout"})
                  (channel-type [_] :sms))
        sleeps (atom 0)]
    (with-redefs [ch/notifier-for (fn [_] failing)
                  notif-test.service.notification-service/pause-ms (fn [_] (swap! sleeps inc))]
      (let [_ (svc/submit-message! deps {:category :finance :message-body "retry"})]
        ;; For max-retries=2, we should sleep exactly 2 times
        (is (= 2 @sleeps))
        ;; send! should have been invoked 3 times (attempts 0,1,2)
        (is (>= @calls 3))))))

