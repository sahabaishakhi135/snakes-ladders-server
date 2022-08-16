(ns snakesladders.round.cmds.sync
  (:require
   [clojure.tools.logging :as log]
   [com.pekaplay.events :refer [notify2]]
   [com.pekaplay.timer :as timer]
   [snakesladders.round.core :as core]
   [snakesladders.round.events :as events]
   [snakesladders.board.snakesladders.events :as lble]
   [snakesladders.board.snakesladders.board :as lblb]
   [snakesladders.round.cmds.cmd :refer [handle-cmd get-timeout-duration game-type-dispatch]]))


(defmulti sync-state (fn [round _player-id] (game-type-dispatch round)))

(defmethod sync-state :snakesladders
  [round player-id]
  (let [{:keys [status players turn-order current-player config]} round
        {:keys [used rolls]} (get players player-id)
        num-rolls (count (get-in round [:players player-id :rolls]))
        remaining-turn (timer/remaining (get-in round [:timers (str ":turn-" current-player)]))
        period (get-timeout-duration round player-id status)]
    (cond
      (and (= current-player player-id) (contains? #{:roll-die :move-bead} status))
      (notify2 [(events/ready (vals players) turn-order)
                (events/turn-order turn-order)
                (events/scoreboard (core/scoreboard players))
                (lble/pre-roll current-player
                               (nth lblb/COLORS current-player)
                               (take 3 rolls)
                               (count rolls))
                (lble/sync-board (:beads round)
                                 used
                                 rolls
                                 num-rolls
                                 (->> players
                                      (core/scoreboard)
                                      (map (fn [[k v]] [k (:score v)]))
                                      (into {}))
                                 turn-order
                                 remaining-turn)
                (events/next-turn current-player status period)]
               player-id)

      (and (not= current-player player-id) (contains? #{:roll-die :move-bead} status))
      (notify2 [(events/ready (vals players) turn-order)
                (events/turn-order turn-order)
                (events/scoreboard (core/scoreboard players))
                (lble/sync-board (:beads round)
                                 used
                                 rolls
                                 num-rolls
                                 (->> players
                                      (core/scoreboard)
                                      (map (fn [[k v]] [k (:score v)]))
                                      (into {}))
                                 turn-order
                                 remaining-turn)
                (events/next-turn current-player status period)]
               player-id)

      (contains? #{:finished} status)
      (notify2 [(events/ready (vals players) turn-order)
                (events/turn-order turn-order)
                (events/scoreboard (core/scoreboard players))]
               player-id))))

(defmethod handle-cmd :sync
  [round player-id _cmd]
  [nil (sync-state round player-id)])
