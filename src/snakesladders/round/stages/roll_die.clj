(ns snakesladders.round.stages.roll-die
  (:require
   [snakesladders.round.cmds.cmd :refer [game-type-dispatch handle-cmd handle-timeout]]))

(defmulti on-roll-die-timeout (fn [round player-id] (game-type-dispatch round)))

(defmethod on-roll-die-timeout :snakesladders
  [round player-id]
  (let [rolls (take 3 (get-in round [:players player-id :rolls]))]
    (when (> (count rolls) 0)
      (handle-cmd round player-id {:type :roll-die
                                   :data {:dieRoll (rand-nth rolls)}}))))

(defmethod handle-timeout :roll-die
  [round player-id _cmd]
  (when (= :roll-die (:status round))
   (on-roll-die-timeout round player-id)))