(ns snakesladders.ticket-test
  (:require
    [clojure.tools.logging :as log]
    [clojure.test :refer :all]
    [cheshire.core :as json]
    [snakesladders.e2e :as e2e]
    [engine.helpers.director :as dir]
    [com.pekaplay.math :refer [->float]]
    [clj-http.client :as http]))

(deftest ^:integration buy-ticket
  (let [login-data (e2e/do-login "agnes" "123456")
        table-id   "35" ; 101pool-2p-25
        buyin      50
        win        100
        access-token (get login-data "token")
        wager-id     (e2e/do-buyin table-id buyin access-token)
        wager-info1  (e2e/wager-info wager-id)
        ticket-data  (e2e/mk-ticket wager-id table-id access-token)
        wager-info2  (e2e/wager-info wager-id)
        ticket-id (get ticket-data "ticketId")
        {:strs [ticketId sessionId]} ticket-data]
    (is (some? access-token))
    (is (some? wager-id))
    (is (= (str (get login-data "userId")) (get ticket-data "playerId")))
    (is (= "50" (get wager-info1 "buyin") (get wager-info1 "amount")))
    (e2e/ensure-valid-ticket ticket-data)
    (is (= 0.0 (->float (get wager-info2 "amount"))))
    (is (= wager-id (get ticket-data "wagerId")))))

(defn fetch-ticket
  [username password]
  (let [login-data (e2e/do-login "agnes" "123456")
        table-id   "35" ; 101pool-2p-25
        buyin      50
        win        100
        access-token (get login-data "token")
        wager-id     (e2e/do-buyin table-id buyin access-token)
        wager-info1  (e2e/wager-info wager-id)
        ticket-data  (e2e/mk-ticket wager-id table-id access-token)
        wager-info2  (e2e/wager-info wager-id)
        ticket-id (get ticket-data "ticketId")
        {:strs [ticketId sessionId]} ticket-data]
    (is (some? access-token))
    (is (some? wager-id))
    (is (= (str (get login-data "userId")) (get ticket-data "playerId")))
    (is (= "50" (get wager-info1 "buyin") (get wager-info1 "amount")))
    (e2e/ensure-valid-ticket ticket-data)
    (is (= 0.0 (->float (get wager-info2 "amount"))))
    (is (= wager-id (get ticket-data "wagerId")))
    [ticket-data access-token]))

; First ticket has no sessionId assigned to it
; Next ticket has a sessionId
(deftest ^:integration buy-ticket-allocation
  (e2e/reset-redis!)
  (let [[ticket-data1 _] (fetch-ticket "agnes" "123456")
        [ticket-data2 _] (fetch-ticket "mongoo" "bongo")]
    (is (nil? (get ticket-data1 "sessionId")))
    (is (some? (get ticket-data2 "sessionId")))))

(deftest ^:integration game-win-credit
  (let [login-data (e2e/do-login "agnes" "123456")
        table-id   "35" ; 101pool-2p-25
        buyin      50
        win        100
        access-token (get login-data "token")
        wallet-info1 (e2e/wallet-info access-token)
        wager-id     (e2e/do-buyin table-id buyin access-token)
        wallet-info2 (e2e/wallet-info access-token)
        wager-info1  (e2e/wager-info wager-id)
        ticket-data  (e2e/mk-ticket wager-id table-id access-token)
        wager-info2  (e2e/wager-info wager-id)
        ticket-id (get ticket-data "ticketId")
        {:strs [ticketId sessionId]} ticket-data
        res (dir/credit-win sessionId access-token ticketId win)
        wallet-info3 (e2e/wallet-info access-token)]
    (is (some? access-token))
    (is (some? wager-id))
    (is (= (str (get login-data "userId")) (get ticket-data "playerId")))
    (is (= "50" (get wager-info1 "buyin") (get wager-info1 "amount")))
    (e2e/ensure-valid-ticket ticket-data)
    (is (= 0.0 (->float (get wager-info2 "amount"))))
    (is (= wager-id (get ticket-data "wagerId")))
    (is (= true res))
    (is (= (- (:cash wallet-info1) (:cash wallet-info2)) 50.00))
    (is (= (- (:cash wallet-info3) (:cash wallet-info1)) 50.00))
    (is (not= wallet-info1 wallet-info2))))

(deftest ^:integration switch-table
  (e2e/reset-redis!)
  (let [[ticket-data1 access-token1] (fetch-ticket "agnes" "123456")
        [ticket-data2 _] (fetch-ticket "mongoo" "bongo")
        {:strs [ticketId wagerId]} ticket-data1
        ticket-data3 (e2e/do-switch-table ticketId wagerId access-token1)]
    (e2e/ensure-valid-ticket ticket-data3)
    (is (not= ticketId (get ticket-data3 "ticketId")))
    (is (= wagerId (get ticket-data3 "wagerId")))))
