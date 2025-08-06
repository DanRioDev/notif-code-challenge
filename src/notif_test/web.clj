(ns notif-test.web
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [clojure.string :as str]
   [reitit.ring :as ring]
   [hiccup.page :as page]
   [notif-test.domain.models :as m]
   [notif-test.repository.memory :as mem]
   [notif-test.repository.protocols :as p]
   [notif-test.service.notification-service :as svc]
   [notif-test.notification.channels :as ch]
   [ring.util.response :as resp]))

;; Inversion of dependencies: wire repositories here
(defonce users (mem/users-repo))
(defonce messages (mem/messages-repo))
(defonce logs (mem/logs-repo))
(defonce notifiers {:sms (ch/->SMSNotifier)
                    :email (ch/->EmailNotifier)
                    :push (ch/->PushNotifier)})
(defonce deps {:users users :messages messages :logs logs :notifiers notifiers})

(defn layout [title & body]
  (page/html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title title]
    (page/include-css "https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css")]
   [:body
    [:section.section
     [:div.container
      body]]
    (page/include-js "https://cdn.jsdelivr.net/npm/axios@1.6.7/dist/axios.min.js")
    [:script {:type "text/javascript"}
     "async function submitForm(e){e.preventDefault(); const cat = document.getElementById('category').value; const msg=document.getElementById('message').value; if(!msg.trim()){alert('Message cannot be empty'); return;} const res=await fetch('/api/messages',{method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({category: cat, messageBody: msg})}); if(res.ok){document.getElementById('message').value=''; await loadLogs();} else {const t=await res.text(); alert('Error: '+t);} } async function loadLogs(){ const res=await fetch('/api/logs'); const data=await res.json(); const container=document.getElementById('logs'); container.innerHTML=''; for(const row of data){ const li=document.createElement('li'); li.textContent = `[${row.timestamp}] msg#${row.message_id} ${row.category} -> user#${row.user.id} via ${row.channel} => ${row.notification_status}`; container.appendChild(li);} } window.addEventListener('load', loadLogs);"]]))

(defn home-page []
  (layout "Notification Test"
          [:h1.title "Notification Test"]
          [:div.box
           [:form {:onSubmit "submitForm(event)"}
            [:div.field
             [:label.label "Category"]
             [:div.control
              [:div.select
               [:select {:id "category"}
                (for [c (sort m/categories)]
                  [:option {:value (name c)} (str/capitalize (name c))])]]]]
            [:div.field
             [:label.label "Message"]
             [:div.control
              [:textarea.textarea {:id "message" :placeholder "Type your message"}]]]
            [:div.field
             [:div.control
              [:button.button.is-primary {:type "submit"} "Send"]]]]]
          [:h2.subtitle "Log history"]
          [:ul#logs]))

(defn json-response [data]
  (-> (resp/response (json/encode data))
      (resp/content-type "application/json")))

(def app
  (let [router
        (ring/router
         [["/" {:get (fn [_] (resp/response (home-page)))}]
          ["/api/logs" {:get (fn [_]
                               (json-response (map (fn [l]
                                                     (-> l
                                                         (update :timestamp str)
                                                         (set/rename-keys {:message-id :message_id
                                                                           :notification-status :notification_status})))
                                                   (p/all-logs logs))))}]
          ["/api/messages" {:post (fn [{:keys [body]}]
                                    (try
                                      (let [data (json/decode (slurp body) keyword)
                                            {:keys [category messageBody]} data
                                            category-kw (keyword (str/lower-case (name category)))
                                            result (svc/submit-message! deps {:category category-kw :message-body messageBody})]
                                        (json-response result))
                                      (catch Exception e
                                        (-> (resp/response (str (.getMessage e)))
                                            (resp/status 400)))))}]])]
    (ring/ring-handler router
                       (constantly (-> (resp/response "Not found") (resp/status 404))))))

