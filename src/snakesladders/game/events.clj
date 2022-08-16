(ns snakesladders.game.events
  (:require
    [medley.core :refer [map-vals]]
    [engine.player.protocols :refer [wager-info]]))

(defn stop-room
  [result]
  {:type :stop-room
   :data {:result result}})

(defn game-scoreboard
  "Returns a list of points (ordered seat wise) for each round. The list of
   seats is the list of un-pruned seats in the game

  e.g:
  history: {0: [20 43 75 80 25 0]
            1: [80 80 80 45 7 0]
            2: [56 nil nil nil 0 80]}
  scores: {0: 34, 1: 45, 2: 80}"
  [game]
  (let [empty-scores (->> game
                         :players
                         (keys)
                         (map #(do [% []]))
                         (into {}))
        scores (->> game
                    :players
                    (map (fn [[s p]] [s (:score p)]))
                    (into {}))
        seatwise-points (->> (:rounds game)
                             (filter #(= :finished (:status %)))
                             (map :players)
                             (map #(map-vals :points %))
                             (apply merge-with merge empty-scores))]
    {:type :game-scoreboard
     :data {:history seatwise-points
            :scores scores}}))

(defn game-config
  [game]
  (let [cfg (:config game)
        {:keys [game-type num-rounds num-seats bet-value max-buyin min-buyin num-rolls]} cfg]
    {:type :room-config
     :data {:config {:id         (:id cfg)
                     :name       (:name cfg)
                     :gameType   game-type
                     :numRounds  num-rounds
                     :numSeats   num-seats
                     :betValue   bet-value
                     :minBuyin   min-buyin
                     :maxBuyin   max-buyin
                     :numRolls   num-rolls
                     :drop       (get-in game [:config :points :drop])
                     :middleDrop (get-in game [:config :points :middle-drop])}}}))

(defn next-stage
  ([stage]
   {:type :next-stage
    :data {:stage stage}})
  ([stage timeout]
   {:type :next-stage
    :data {:stage stage}
    :timeout timeout})
  ([stage game-time t]
   {:type :next-stage
    :data {:stage stage :gameTime game-time}}))

(defn leave-game-result
  [seat-id player num-seats avl-seats]
  (let [{:keys [api-key auth-token wager-id ticket-id]} (wager-info player)]
    {:type :leave-game-result
     :data {:seat-id      seat-id
            :ticket-id    ticket-id
            :wager-id     wager-id
            :api-key      api-key
            :access-token auth-token
            :num-seats    num-seats
            :avl-seats    avl-seats}}))

(defn leave-table-result
  [seat-id]
  {:type :leave-table-result
   :data {:by seat-id}})

(defn leave-table
  [seat-id]
  {:type :leave-table
   :data {:by seat-id}})

(defn busted
  ([seat-id]
   {:type :busted :data {:player seat-id} :to :all})
  ([seat-id score]
   {:type :busted :data {:player seat-id, :score score} :to :all}))

(defn winner
  [winner pot]
  {:type :game-winner
   :data {:player-id winner
          :pot pot}})
