(ns snakesladders.game.stages.starting-turns
  (:require
    [engine.util :refer [wae->]]

    [snakesladders.game.core :as core]
    [snakesladders.stages :refer [start-stage]]
    [snakesladders.game.cmds.cmd :refer [handle-timeout game-timeout?]]
    [snakesladders.round.cmds :as rcmd]))

(defmethod game-timeout? :starting-turns [_ _] true)

(defmethod handle-timeout :starting-turns
  [game _stage]
  (let [{:keys [current-round]} game
        round (get-in game [:rounds current-round])
        [round' evs] (rcmd/start-turns round)
        game' (core/+current-round game round')]
    (wae-> [game' evs]
           (start-stage :round))))
