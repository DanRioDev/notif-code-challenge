(ns notif-test.domain.models
  "Domain models and specs for the notification system."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

;; Enumerations
(def categories #{:sports :finance :movies})
(def channels   #{:sms :email :push})

;; Helpers
(defn non-blank-string? [s]
  (and (string? s) (not (str/blank? s))))

;; Specs
(s/def ::id pos-int?)
(s/def ::name non-blank-string?)
(s/def ::email (s/and non-blank-string? #(str/includes? % "@")))
(s/def ::phone (s/and non-blank-string? #(re-matches #"[+]?\d[\d -]{6,}" %)))
(s/def ::category categories)
(s/def ::channel channels)

(s/def ::subscribed (s/coll-of ::category :min-count 0 :kind vector? :distinct true))
(s/def ::preferred-channels (s/coll-of ::channel :min-count 1 :kind vector? :distinct true))

(s/def ::user
  (s/keys :req-un [::id ::name ::email ::phone ::subscribed ::preferred-channels]))

(s/def ::message-id pos-int?)
(s/def ::message-category ::category)
(s/def ::message-body non-blank-string?)

(s/def ::message
  (s/keys :req-un [::message-id ::message-category ::message-body]))

(s/def ::timestamp inst?)
(s/def ::notification-status #{:success :failed})
(s/def ::error (s/nilable non-blank-string?))

(s/def ::notification-log
  (s/keys :req-un [::id ::message-id ::category ::channel ::user ::timestamp ::notification-status]
          :opt-un [::error]))

;; Constructors with validation
(defn validate!
  "Validate data against a spec, throwing ex-info on failure."
  [spec data err-msg]
  (when-not (s/valid? spec data)
    (throw (ex-info (or err-msg "Spec validation failed")
                    {:ex ::spec-validation
                     :explain (s/explain-str spec data)})))
  data)

(defn ->User [m]
  (validate! ::user m "Invalid user"))

(defn ->Message [m]
  (validate! ::message m "Invalid message"))

(defn ->NotificationLog [m]
  (validate! ::notification-log m "Invalid notification log"))

