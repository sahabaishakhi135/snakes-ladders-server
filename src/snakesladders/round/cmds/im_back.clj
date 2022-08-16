(ns snakesladders.round.cmds.im-back
  (:require
   [com.pekaplay.events :refer [notify2]]
   [snakesladders.round.cmds.cmd :refer [handle-cmd]]))

(defn change-player-status
  [round player-id status]
  [round [{:type :set-player-status :data {:status status :player-id player-id}}
          (notify2 {:type :player-status :data {:status status}} player-id)]])

(defmethod handle-cmd :im-back
  [round player-id]
  (if (= :afk (get-in round [:players player-id :status]))
    (change-player-status round player-id :running)
    [nil nil]))