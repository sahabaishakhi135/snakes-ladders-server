(ns snakesladders.round.handlers
  (:require
   [clojure.tools.logging :as log]
   [snakesladders.round.events :as events :refer [error]]
   [snakesladders.round.cmds :as cmds]
   [snakesladders.round.core :as core]))

(defn scoreboard [round] (core/scoreboard (:players round)))

(defn oot-error
  ([current-player player-id]
   (oot-error current-player player-id player-id nil))
  ([current-player player-id recipient]
   (oot-error current-player player-id recipient nil))
  ([current-player player-id recipient cmd]
   [nil (error :out-of-turn
               {:expected {:player current-player}
                :got {:player player-id}
                :cmd cmd}
               recipient)]))

(defn handle-cmd
  [round player-id cmd]
  (cmds/handle-cmd round player-id cmd))

(defn handle-timeout
  [round player-id {cmd-data :data :as cmd}]
  (let [{:keys [current-player status]} round]
    (cond

      (= :end-round (:type cmd))
      (cmds/handle-timeout round player-id cmd)

      (contains? #{:roll-die :move-bead} status)
      (if (and (= (:turn-count round) (:turn-count cmd-data)) (= player-id current-player))
        (cmds/handle-timeout round player-id cmd)
        (oot-error current-player player-id nil))

      :else
      (do
        (log/error "cmd timeout" status cmd)
        [nil nil]))))
