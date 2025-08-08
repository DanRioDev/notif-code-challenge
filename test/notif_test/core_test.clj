(ns notif-test.core-test
  (:require [clojure.test :refer [deftest is testing]]
            ;; Ensure web tests namespace is loaded so lein discovers it reliably
            [notif-test.web-test]
            ;; Ensure repo tests namespace is loaded
            [notif-test.repository.postgres-test]))

(deftest sanity-test
  (testing "Sanity"
    (is true)))
