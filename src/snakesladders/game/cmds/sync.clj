(ns snakesladders.game.cmds.sync
  (:require
   [com.pekaplay.events :refer [notify2]]
   [engine.timeouts :as t]

   [snakesladders.game.events :as ge]
   [snakesladders.game.core :as core]
   [snakesladders.game-types :as gt]
   [snakesladders.game.cmds.cmd :refer [handle-cmd handle-round-cmd game-cmd?]]
   [snakesladders.game.processor :refer [process-events]]

   [snakesladders.round.events :as re]))

(defmethod game-cmd? :sync [_ _] true)

(defn round-events
  [{:keys [players turn-order] :as _game}]
  (when (some? turn-order)
    (list (re/ready (vals players) turn-order)
          (re/turn-order turn-order))))

(defn sync-game*
  [{curr-stage :status :as game}]
  (let [remaining (t/eta game curr-stage)]
    [(ge/next-stage curr-stage remaining)]))

(defn sync-game
  [game]
  (let [curr-stage (:status game)
        game-evs   (sync-game* game)
        gt-evs     (when (gt/game-stage? game curr-stage) (gt/sync-stage game))]
    (concat game-evs gt-evs)))

(defmethod handle-cmd :sync
  [{stage :status :as game} plyr-id cmd]
  (let [round        (core/current-round game)
        [_round evs] (if (some? round)
                       (handle-round-cmd game round plyr-id cmd)
                       [nil (round-events game)])
        [_game evs'] (process-events game evs)
        game-evs     (sync-game game)]
    (cond
      (contains? #{:round} stage)
      [nil (notify2
            (apply list
                   ;(ge/game-config game)
                   (concat game-evs evs'))
            plyr-id)]

      (contains? #{:waiting :pick-seats :starting-round :starting-turns} stage)
      [nil (notify2
            (apply list
                   ;(ge/game-config game)
                   (concat evs' game-evs))
            plyr-id)]

      :else
      [nil (notify2
            (apply list
                   ;(ge/game-config game)
                   (concat evs' game-evs))
            plyr-id)])))