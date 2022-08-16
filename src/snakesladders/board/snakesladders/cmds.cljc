(ns snakesladders.board.snakesladders.cmds
  (:require
   [clojure.tools.logging :as log]
   [snakesladders.board.snakesladders.board :refer [COLORS PATHS]]
   [snakesladders.board.snakesladders.events :as events]
  ;;  [com.pekaplay.shared.rooms :as rooms]
  ;;  [com.pekaplay.shared.room :as room]
   [engine.util :refer [wae->]]
   [com.pekaplay.events :refer [notify2]]
   [medley.core :refer [filter-keys filter-vals remove-keys]]))

(def ^:const START-CELL 0)

(def ^:const HOME-CELL  100)

(defn draw [] (inc (rand-int 6)))

(defn current-color [{:keys [current-color] :as board}]
  current-color)

(defn next-color [color]
  (let [i (.indexOf COLORS color)]
    (nth (cycle COLORS) (inc i))))

(defn error [msg]
  {:type :error, :data {:msg msg}})

(defn nth-cell [cell n]
  (let [i (.indexOf (into [] PATHS) cell)]
    (nth PATHS (+ i n))))

(defn home-cell? [cell]
  (= (:id cell) HOME-CELL))

(defn move-possible? [color cell n]
  (cond
    (nil? n)
    false

    (home-cell? cell)
    false

    :else
    (let [i (.indexOf (into [] PATHS) cell)]
      (log/info "move-possible?" color cell PATHS i n)
      (> (count PATHS) (+ i n)))))

(defn beads-by-color [beads color]
  (filter-keys #(= (:color %) color) beads))

(defn moves-possible? [{beads :beads} color n]
  (let [beads (beads-by-color beads color)]
    (some (fn [[b c]] (move-possible? color c n)) beads)))

(defn change-turn [[board evs] color]
  (let [color' (next-color color)]
    [(assoc board :current-color color', :die-roll nil, :turns 1)
     (conj evs (events/next-turn (:current-player board) color' :roll-die))]))

(defn winner? [{all-beads :beads} color]
  (let [beads (beads-by-color all-beads color)]
    (every? (fn [[_b c]] (= (c :id) HOME-CELL)) beads)))

(defn other-cellmates [{beads :beads} bead cell]
  (->> beads
       (filter-vals #(= cell %))
       (remove-keys #(= (:color bead) (:color %)))))

(defn check-winner [[board evs] color]
  (if (winner? board color)
    [(assoc board :state :finished) (conj evs (events/winner (:current-player board) color))]
    [board evs]))

(defn check-reroll [[board evs] color die-roll]
  (if-not (= (:state board) :finished)
    (if (> (:turns board) 0)
      [board (conj evs (events/next-turn (:current-player board) color :roll-die))]
      (change-turn [board evs] color))
    [board evs]))


(defn start-cell [] {:type :cell, :id START-CELL})

(defn start+1-cell [] (nth-cell (start-cell) 1))

(defn kill-bead [board [bead cell]]
  (assoc-in board [:beads bead] (start+1-cell)))

(defn kill-bead-event [evs [bead cell]]
  (-> evs
      (conj (events/kill-bead bead))
      (conj (events/move-bead bead cell (start+1-cell)))))

(defn check-kill [[board evs] bead cell]
  (if-let [cellmates (seq (other-cellmates board bead cell))]
    (let [board (update board :turns inc)
            board' (reduce kill-bead board cellmates)
            evs' (reduce kill-bead-event evs cellmates)]
        [board' evs'])
    [board evs]))

(defn move-bead
  [{:keys [current-color cells beads die-roll] :as board} bead n]
  (if-not (= current-color (:color bead))
    (error :out-of-turn)
    (let [cell (get beads bead)]
      (if (move-possible? current-color cell die-roll)
        (let [cell' (nth-cell cell die-roll)
              board' (-> board
                         (assoc :die-roll nil)
                         (update :turns dec)
                         (assoc-in [:beads bead] cell'))
              evs [(events/move-bead bead cell cell')]]
          (-> [board' evs]
              (check-winner current-color)
              (check-kill bead cell')
              (check-reroll current-color die-roll)))
        (error :move-not-possible)))))

;; (defn join-room [user-name channel]
;;   (let [[room evs] (rooms/join-room :ludo user-name channel)]
;;     (if (= (:status room) :ready)
;;       [room (-> evs
;;                 (conj (events/ready))
;;                 (conj (events/next-turn :red :roll-die)))]
;;       [room evs])))