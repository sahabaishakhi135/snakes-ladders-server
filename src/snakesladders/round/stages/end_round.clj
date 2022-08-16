(ns snakesladders.round.stages.end-round
  (:require
   [com.pekaplay.events :refer [notify2]]
   [engine.util :refer [wae->]]
   [snakesladders.round.cmds.cmd :refer [handle-timeout finish-round]]
   [snakesladders.round.cmds.move-bead :refer [calc-winner
                                      move-bead-and-if-winner
                                      move-bead]]
   [snakesladders.board.snakesladders.util :refer [->bead]]
   [snakesladders.board.snakesladders.events :as lble]
   [clojure.tools.logging :as log]))

;; A lap consists of 4 turns. We always need to ensure that the game ends
;; at the end of a lap not mid lap
;;
;; If the current turn count is 30, we want the game to end at the 32nd turn
(defmethod handle-timeout :end-round
  [round _player-id _cmd]
  (let [{:keys [num-seats]} (:config round)
        num-laps (quot (:turn-count round) num-seats)]
    [(assoc round :max-turn-count (* num-seats (inc num-laps)))
     [(notify2 (lble/end-round) :all)]]))
