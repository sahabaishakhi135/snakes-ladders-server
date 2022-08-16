(ns snakesladders.round
  (:require
    [com.pekaplay.uuid :refer [uuid-str]]
    [snakesladders.board.snakesladders.board :refer [mk-board]]))

(defrecord TurnBasedRound
  [uuid players turn-order turn-count current-player status game-uuid config])

(defrecord SnakesladdersRound
  [board winner points])

(defn mk-round [game-uuid config]
  (merge (TurnBasedRound. (uuid-str) {} nil -1 nil :ready game-uuid config)
         (mk-board (:colors config) (:num-beads config))
         (map->SnakesladdersRound {:players (zipmap (:colors config) (repeat nil))})))
