(ns snakesladders.round.stages.turn
  (:require
   [snakesladders.round.cmds.cmd :refer [handle-timeout]]))

(defmethod handle-timeout :turn
  [round player-id cmd]
  [round nil])