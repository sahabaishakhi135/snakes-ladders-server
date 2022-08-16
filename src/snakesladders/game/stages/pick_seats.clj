(ns snakesladders.game.stages.pick-seats
  (:require
   [clojure.tools.logging :as log]
   [engine.util :refer [wae->]]
   [snakesladders.game.core :as core]
   [snakesladders.stages :refer [start-stage]]
   [snakesladders.game.cmds.cmd :refer [handle-timeout game-timeout?]]))

(defmethod game-timeout? :pick-seats [_ _] true)

(defmethod handle-timeout :pick-seats
  [game _stage]
  ;(let [res (wae-> game
  ;                 (core/pick-seats)
  ;                 (start-stage :starting-round))]
  ;  (log/info res)
  ;  res)
  (wae-> game
         (core/pick-seats)
         (start-stage :starting-round)))