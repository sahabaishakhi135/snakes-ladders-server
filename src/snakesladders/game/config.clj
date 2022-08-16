(ns snakesladders.game.config
  (:require
   [clojure.tools.logging :as log]
   [clojure.set :refer [rename-keys]]
   [cheshire.core :as json]
   [environ.core :refer [env]]
   [clj-http.client :as http]
   ;[org.httpkit.client :as http]
   [com.pekaplay.util :refer [->resource]]))

(def configs (atom nil))

(defn camel->kebab
  "Simple translation from camelCase to kebab-case"
  [tbl-json]
  (let [{:strs [tableId tableType tableName tableLimit numPlayers
                betValue minBuyin maxBuyin tableCode rakePct gameTime numRolls prizesAmount colors numBeads]} tbl-json]
    {:table-id    tableId
     :table-code  tableCode
     :table-type  (keyword tableType)
     :table-name  tableName
     :table-limit tableLimit
     :num-players numPlayers
     :bet-value   betValue
     :max-buyin   maxBuyin
     :min-buyin   minBuyin
     :rake-pct    rakePct
     :game-time (or gameTime 600)
     :num-rolls (or numRolls 24)
     :prizes-amount prizesAmount
     :colors (or colors [:red :green :yellow :blue])
     :num-beads (or numBeads 3)}))

(defn fetch-tables-from-url
  [url]
  (loop []
    (let [res (http/request {:method :get
                             :url    url
                             :as     :json-string-keys
                             :throw-exceptions false})]
      (if-let [data (:body res)]
        data
        (do (log/warn "Retrying to load from" url)
            (Thread/sleep 2000)
            (recur))))))

(defn fetch-snakesladders-tables
  []
  (when-let [json-src (:tables-json env)]
    (let [json-str (if (clojure.string/starts-with? json-src "http://")
                     (do
                       (log/info "Fetching table details from director")
                       (fetch-tables-from-url json-src))
                     (do
                       (log/info "Parsing cached table details json")
                       (-> json-src (->resource) (slurp) (json/parse-string))))]
      (if-not (nil? json-str)
        (do
          (log/info "OK")
          (reset! configs (->> json-str
                               (map camel->kebab)
                               (map (fn [t] [(:table-id t) t]))
                               (into {}))))
        (log/info "Failed!")))))

(defn- add-drop-points
  [table-config]
  (let [{:keys [game-type num-rounds]} table-config]
    (if (and (= :pool game-type) (= 201 num-rounds))
      (assoc table-config :points {:drop 25, :middle-drop 50, :full-count 80})
      (assoc table-config :points {:drop 20, :middle-drop 40, :full-count 80}))))

(defn ->tbg-config
  [cfg]
  (-> cfg
      (rename-keys {:table-id    :id
                    :table-code  :code
                    :table-type  :game-type
                    :table-name  :name
                    :table-limit :num-rounds
                    :num-players :num-seats})
      (add-drop-points)))

(defn- -get-config
  [table-id]
  (if-let [url (:table-json env)]
    (let [res (http/request {:method :get
                             :url (format url table-id)
                             :as :json-string-keys
                             :throw-exceptions false})]
      (log/info "-get-config" res)
      (camel->kebab (:body res)))
    (get @configs table-id)))

(defn get-config
  [table-type]
  (if-let [cfg (or (-get-config table-type)
                   (-get-config (str table-type)))]
    (->tbg-config cfg)
    (log/error "Config for table type" table-type (type table-type) "not found")))

;; (defn num-seats
;;   [table-type]
;;   (let [config (get @configs table-type)]
;;     (:num-players config)))

(comment
  (get @configs "25")
  (get-config "25"))