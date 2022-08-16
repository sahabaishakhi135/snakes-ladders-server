(ns snakesladders.board.snakesladders.handlers
  (:require
   ;[com.pekaplay.shared.rooms :as rooms]
   [snakesladders.board.snakesladders.cmds :as cmds]
   [snakesladders.board.snakesladders.util :refer [->bead ->cell ->beadId ->cellId]]
   [snakesladders.board.snakesladders.events :as events]))

(comment

(defn on-error [{:keys [current-color] :as board} move]
  (events/next-turn current-color move))

(defn beads-cells [board]
  (map
   (fn [[bead cell]] (str (->beadId bead) "->" (->cellId cell)))
   (:beads board)))

(defn handle-move-bead
  [{:keys [n beadId cellId roomId] :as msg}]
  {:pre [(rooms/valid? roomId)]}
  (let [room (rooms/get-room roomId)
        bead (->bead beadId)
        res (cmds/move-bead (:board room) bead n)]
    (if (map? res)
      [nil [res (on-error (:board room) :move-bead)]]
      (let [[board events] res
            room' (assoc room :board board)]
        (rooms/save-room! room')
        [room' events]))))

(defn handle-roll-die
  [{:keys [color roomId] :as msg}]
  {:pre [(rooms/valid? roomId)]}
  (let [room (rooms/get-room roomId)
        color (keyword color)
        res (cmds/roll-die (:board room) color)]
    (if (map? res)
      [nil [res (on-error (:board room) :roll-die)]]
      (let [[board events] res
            room' (assoc room :board board)]
        (rooms/save-room! room')
        [room' events]))))

(defn handle-join [channel {user :alias}]
  (let [[room evs] (cmds/join-room user channel)]
    [room evs]))

(defn ^:export handle-cmd
  [channel {:keys [type msg] :as cmd}]
  (case type
    "join" (handle-join channel msg)
    "roll-die" (handle-roll-die msg)
    "move-bead" (handle-move-bead msg)))

  )