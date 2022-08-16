(ns snakesladders.handlers.admin
  (:require
    [clojure.tools.logging :as log]
    [environ.core :refer [env]]
    [medley.core :refer [filter-vals]]
    [com.pekaplay.util :refer [json-response]]
    [snakesladders.helpers.director :as dir]
    [snakesladders.game.server :as srv]
    [snakesladders.game.config :as cfg]))

; POST /games API handler
(defn mk-game
  [req]
  (if-let [game @(srv/mk-game! :snakesladders (-> req :body :tableType))]
    (do
      (log/info "Publishing new game info" game)
      (dir/publish game)
      (json-response {:type :snakesladders, :room-uuid (:uuid game)}))
    (assoc (json-response {:error :table-type-not-found}) :status 404)))

(defn ->round-info [r]
  {:round-uuid (:uuid r)
   :turn-order (:turn-order r)
   :current-player (:current-player r)
   :players (map
              (fn [[s p]]
                [s (select-keys p [:status :points])])
              (:players r))})

(defn ->game-info [game]
  (let [r (deref game)]
    {:type (:typ r)
     :game-uuid (:uuid r)
     :players (map
                (fn [[s p]]
                  [s (select-keys p [:id :alias])])
                (:players r))
     :rounds (map ->round-info (:rounds r))
     :status (:status r)}))

; GET /games API handler
(defn get-games
  [req]
  (json-response (map ->game-info (vals @srv/games))))

; GET /games/:id API handler
(defn get-game-details
  [req]
  (let [game-id (get-in req [:params :id])
        game (get @srv/games game-id)]
    (json-response (->game-info game))))

; GET /tables API handler
(defn get-tables
  [req]
  (letfn [(category [table]
            (let [{:keys [table-limit table-type]} table]
              (if (= :pool table-type)
                (str table-limit "-pool")
                table-type)))]
    (->> @cfg/configs
      (vals)
      (map #(assoc % :category (category %)))
      (json-response))))
