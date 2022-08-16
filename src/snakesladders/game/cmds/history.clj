(ns snakesladders.game.cmds.history
  (:require
   [com.pekaplay.events :refer [notify2]]
   [snakesladders.game.events :as ge]
   [snakesladders.game.cmds.cmd :refer [handle-cmd game-cmd?]]))

(defmethod handle-cmd :history
  [game plyr-id _cmd]
  [nil (notify2 (ge/game-scoreboard game) plyr-id)])

(defmethod game-cmd? :history [_ _] true)