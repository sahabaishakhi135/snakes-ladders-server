(ns snakesladders.game.stages.waiting
  (:require
   [medley.core :refer [remove-vals map-vals]]
   [com.pekaplay.events :refer [notify2] :as events]

   [engine.util :refer [wae->]]
   [engine.timeouts :as t]

   [snakesladders.game :refer [init-game-player]]
   [snakesladders.stages :refer [start-stage]]
   [snakesladders.game.cmds.cmd :refer [handle-timeout game-timeout?]]))

(defmethod game-timeout? :waiting [_ _] true)

(defn prune-empty-seats [game]
  (let [players (remove-vals nil? (:players game))]
    (assoc game :players players)))

(defn start-pick-seats
  [game]
  [(-> game
       (prune-empty-seats)
       (t/cancel-timeout! nil :waiting)
       ;(assoc :pinger (mgr/start-task :ping (:uuid game)))
       (update-in [:players] #(map-vals init-game-player %)))
   (notify2 (events/next-stage :pick-seats) :all)])

(defmethod handle-timeout :waiting
  [game _stage]
  (if (contains? #{:ready :waiting} (:status game))
    (wae-> game
           (start-stage :pick-seats)
           (start-pick-seats))
    [game nil]))