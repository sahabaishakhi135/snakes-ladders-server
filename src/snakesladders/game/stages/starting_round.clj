(ns snakesladders.game.stages.starting-round
  (:require
   [clojure.tools.logging :as log]
   [engine.util :refer [wae->]]
   [snakesladders.game.core :as core]
   [snakesladders.stages :refer [start-stage]]
   [snakesladders.game.cmds.cmd :refer [handle-timeout game-timeout?]]))

(defmethod game-timeout? :starting-round [_ _] true)

(defmethod handle-timeout :starting-round
  [game _stage]
  ;(let [res (wae-> game
  ;                 (core/start-round)
  ;                 (start-stage :starting-turns))]
  ;  (log/info res)
  ;  res)
  (wae-> game
         (core/start-round)
         (start-stage :starting-turns)))