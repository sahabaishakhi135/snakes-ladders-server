; best of 3 / 5 rounds aka deals snakesladders
(ns snakesladders.game-types.bestof
  (:require
   [clojure.tools.logging :as log]
   [engine.player.protocols :refer [debit]]
   [snakesladders.game.core :as game-core]
   [snakesladders.game-types.core :as  gt]))

(defmethod gt/reconcile-score :default
  [game round]
  (let [game' (game-core/+current-round game round)]
    (reduce
     (fn [[game evs] [seat-id player]]
       (if (and (some? (:score player)) (some? (get-in game [:players seat-id :score])))
         [(update-in game [:players seat-id :score] + (:score player))
          evs]
         (do
           (log/error "Falling back to setting the score. Since not able to reconcile score" player (get-in game [:players seat-id]))
           [(assoc-in game [:players seat-id :score] (or (:score player) 0)) evs])))
     [game' []]
     (:players round))))

(defn player-game-score
  [player]
  (or (:score player) 0))

(defmethod gt/game-ended? :snakesladders
  [game]
  (let [ended? (= (-> game :config :num-rounds) (count (:rounds game)))]
    (if ended?
      (if (some? (:prizes-amount (:config game)))
        (let [scores (map player-game-score (vals (:players game)))

              sorted-scores (sort > scores)

              best-scores (take 3 sorted-scores)

              winners-list (->> (map (fn [x] (filter #(= (:score (val %)) x) (:players game))) best-scores)
                                flatten
                                (apply hash-map)
                                (map first)
                                (into #{}))]
          (log/info "gt/game-ended?" winners-list)
          [true winners-list])
        (let [best-score (apply max (map player-game-score (vals (:players game))))
              winners (->> (:players game)
                           (filter #(= (:score (val %)) best-score))
                           (map first)
                           (into #{}))]
          (log/info "gt/game-ended?" winners)
          [true winners]))
      [false nil])))

(defmethod gt/busted? :default
  [_game _seat-id _score]
  false)

(defmethod gt/take-seat :default
  [game profile]
  (let [buyin-amt (get-in game [:config :bet-value])
        [ok? bal] (debit profile buyin-amt (:uuid game) nil)]
    (log/debug :bestof profile buyin-amt ok? bal)
    [ok? buyin-amt bal]))