(ns snakesladders.e2e
  (:require
    [clojure.test :refer :all]
    [taoensso.carmine :as rds]
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:const API-KEY  "bb4b5b1c-b922-48e9-8220-3027a2ad0b6f")
(def ^:const RDS-SRV  "redis://localhost:6379")
(def ^:const WEBD-URL "http://localhost:8091")
(def ^:const MMKR-URL "http://localhost:9090")
(def ^:const DIR-URL  "http://localhost:9091")
(def ^:const PAM-URL  "http://localhost:8093")

(defn reset-redis! []
  (rds/wcar {:pool {} :spec {:uri RDS-SRV}} (rds/flushdb)))

(defn json-post
  "Returns the body as a map with string keys"
  ([url body]
   (let [res (http/post url
                        {:body (json/encode body)
                         :content-type :json
                         :accept :json})]
     (json/decode (:body res))))
  ([url body access-token]
   (let [hdrs {"X-Application" API-KEY
               "Authorization" access-token}
         res (http/post url
                        {:body (json/encode body)
                         :headers hdrs
                         :content-type :json
                         :accept :json})]
     (json/decode (:body res)))))

(defn do-login
  "Returns access token"
  [username password]
  (let [dvc-login-url (str WEBD-URL "/app/signin")
        body {"username"     username
              "password"     password
              "install_key" "ee754ea8-fa8f-4fb1-88cb-614a142cbf26"}
        data (json-post dvc-login-url body)]
    data))

(defn do-buyin
  [table-id buyin access-token]
  (let [buyin-url (str WEBD-URL "/app/buyin")
        body {:tableId table-id
              :buyIn   buyin}
        data (json-post buyin-url body access-token)]
    (get data "ticketId")))

(defn wager-info
  [wager-id]
  (let [wager-id-url (str DIR-URL "/wagers/" wager-id)
        res (http/get wager-id-url)]
    (json/decode (:body res))))

(defn wallet-info
  [access-token]
  (let [url (str PAM-URL "/api/v1/wallet")
        hdrs {"X-Application" API-KEY
               "Authorization" access-token}
        res (http/get url {:headers hdrs})]
    (json/decode (:body res) true)))

(defn mk-ticket
  [wager-id table-id access-token]
  (let [tickets-url (str MMKR-URL "/tickets")
        body {:wagerId wager-id
              :tableId table-id}
        data (json-post tickets-url body access-token)]
    data))

(defn ensure-valid-ticket [ticket]
  (doseq [k ["ipPort" "tableId" "siteId" "sessionId" "playerId" "wagerId" "status"
             "playerIp" "ticketId"]]
    (is (contains? ticket k))))

(defn do-leave-table
  [ticket-id wager-id access-token]
  (let [url (str MMKR-URL "/tickets/" ticket-id "/leave")
        body {:wagerId wager-id}
        data (json-post url body access-token)]
    data))

(defn do-switch-table
  [ticket-id wager-id access-token]
  (let [url (str MMKR-URL "/tickets/" ticket-id "/switch")
        body {:wagerId wager-id}
        data (json-post url body access-token)]
    data))
