(ns snakesladders.game.cmds.cmd
  (:require
   [com.pekaplay.util :refer [error?]]
   [snakesladders.game.processor :refer [process-round-result]]
   [snakesladders.round.handlers :as rh]))

;; Game Commands
(defmulti game-cmd? (fn [_game cmd] (:type cmd)))
(defmethod game-cmd? :default [_ _] false)

(defmulti handle-cmd (fn [_game _plyr-id cmd] (:type cmd)))

;; Game Timeouts
(defmulti game-timeout? (fn [_game stage] stage))
(defmethod game-timeout? :default [_ _] false)

(defmulti handle-timeout (fn [_game stage] stage))

(defn handle-round-cmd
  [game round player-id cmd]
  (if (some? round)
    (let [[round' evs :as res] (rh/handle-cmd round player-id cmd)]
      (if (error? res)
        [nil evs]
        (process-round-result game round' evs)))
    [nil nil]))