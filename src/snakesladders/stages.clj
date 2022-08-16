(ns snakesladders.stages
  (:require
    [clojure.tools.logging :as log]
    [engine.timeouts :as t]
    [com.pekaplay.events :refer [notify2]]
    [snakesladders.game.events :as gevents]))

(def stage-timeouts {:pick-seats     2    ; core game
                     :starting-round 5   ; core game
                     :starting-turns 3    ; core game
                     :waiting        15   ; core game
                     :rejoins        10   ; pool game type
                     :splits         10   ; pool game type
                     :settle-round   1    ; points game type
                     :stopping-game  5
                     :end-round      60}) ; special stage timeout

(defn start-stage
  "Start stage with timeout"
  [game stage & {:keys [internal?] :or {internal? false}}]
  (if-let [period (get stage-timeouts stage)]
    (if internal?
      [(assoc game :status stage) (t/timeout stage period)]
      [(assoc game :status stage)
       [(t/timeout stage period)
        (notify2 (gevents/next-stage stage period) :all)]])
    [(assoc game :status stage)
     (notify2 (gevents/next-stage stage (:game-time (:config game)) true) :all)]))
