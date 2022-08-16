(ns snakesladders.round.cmds.chat
  (:require
   [clojure.tools.logging :as log]
   [snakesladders.board.snakesladders.events :as lble]
   [snakesladders.round.cmds.cmd :refer [game-type-dispatch handle-cmd]]
   [com.pekaplay.events :refer [notify2]]
   [snakesladders.board.snakesladders.board :refer [COLORS]]))

(defmethod handle-cmd :chat
  [round player-id {{:keys [_from message] {:keys [seat-id _player]} :to} :data :as _cmd}]
  [round [(notify2 (lble/chat {:seat-id player-id
                               :color (nth COLORS player-id)}
                              {:seat-id seat-id
                               :color (nth COLORS seat-id)}
                              message)
                   :all)]])
