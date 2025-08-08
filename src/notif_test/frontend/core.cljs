(ns notif-test.frontend.core
  (:require [clojure.string :as str]))

;; Print to browser console in development
(enable-console-print!)

(defn by-id [id]
  (.getElementById js/document id))

(defn render-logs [items]
  (let [ul (by-id "logs")]
    (set! (.-innerHTML ul) "")
    (doseq [row items]
      (let [li (.createElement js/document "li")
            ts (:timestamp row)
            msg-id (:message-id row)
            cat (name (:category row))
            uid (get-in row [:user :id])
            ch (name (:channel row))
            st (name (:notification-status row))]
        (set! (.-textContent li)
              (str "[" ts "] msg#" msg-id " " cat " -> user#" uid " via " ch " => " st))
        (.appendChild ul li)))))

(defn load-logs []
  (-> (js/fetch "/api/logs")
      (.then (fn [res] (.json res)))
      (.then (fn [data]
               (let [items (js->clj data :keywordize-keys true)]
                 (render-logs items))))
      (.catch (fn [err]
                (.error js/console err)))))

(defn on-submit [e]
  (.preventDefault e)
  (let [cat (.. (by-id "category") -value)
        msg (.. (by-id "message") -value)]
    (if (str/blank? msg)
      (js/alert "Message cannot be empty")
      (-> (js/fetch "/api/messages"
                    (clj->js {:method "POST"
                              :headers {"Content-Type" "application/json"}
                              :body (.stringify js/JSON (clj->js {:category cat :messageBody msg}))}))
          (.then (fn [res]
                   (if (.-ok res)
                     (do (set! (.. (by-id "message") -value) "")
                         (load-logs)
                         nil)
                     (.text res))))
          (.then (fn [text]
                   (when text
                     (js/alert (str "Error: " text)))))
          (.catch (fn [err]
                    (.error js/console err)))))))

(defn ^:export init []
  (.addEventListener js/window "load"
                     (fn []
                       (load-logs)
                       (.addEventListener (by-id "notify-form") "submit" on-submit))))
