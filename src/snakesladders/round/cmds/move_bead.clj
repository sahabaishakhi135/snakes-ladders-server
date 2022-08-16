(ns snakesladders.round.cmds.move-bead
  (:require
    [clojure.tools.logging :as log]
    [better-cond.core :as better]
    [com.pekaplay.events :refer [notify2]]

    [engine.util :refer [wae-> +nil]]
    [com.pekaplay.timer :as timer]
    [engine.timeouts :as t]
    [engine.manager :as mgr]

    [snakesladders.round.events :as events :refer [error]]

    [snakesladders.board.snakesladders.events :as lble]
    [snakesladders.board.snakesladders.board :as lblb :refer [PATHS COLORS SNAKES LADDERS]]
    [snakesladders.board.snakesladders.cmds :as lblc]
    [snakesladders.board.snakesladders.util :refer [->bead ->beadId]]

    [snakesladders.round.cmds.cmd :refer [change-turn pre-roll-dice finish-round change-status handle-cmd end-turn out-of-turn? oot-error cmd-error game-type-dispatch TURN-TIMER]]))

(defmulti move-bead   (fn [round _player-id _bead] (game-type-dispatch round)))
(defmulti move-bead-and-if-winner (fn [round _player-id _bead] (game-type-dispatch round)))
(defmulti round-ended?   (fn [board color] (game-type-dispatch board)))
(defmulti calc-winner (fn [board player-id color] (game-type-dispatch board)))

(defn check-reroll*
  [board player-id color die-roll]
  (if-not (= (:state board) :finished)
    (if (> (:turns board) 0)
        (let [timeout-dur (+ (TURN-TIMER) 2)

              turn-uuid (get-in board [:timers (str ":turn-" (:current-player board))])

              f #(mgr/dispatch {:task :timeout
                                :cmd {:type (:status board)
                                      :data {:turn-count (:turn-count board)
                                             :player-id (:current-player board)
                                             :timeout timeout-dur}}}
                               (:game-uuid board))
              board1 (-> board
                         (t/cancel-timeout! player-id :move-bead)
                         (t/cancel-timeout! player-id :roll-die))
              ok (timer/extend-timer! turn-uuid f timeout-dur)]
          (log/info "check-reroll*" (count (:timers ok)))
          (wae-> board1
                 (pre-roll-dice)
                 (change-status :roll-die {})))
        (wae-> board
               (change-turn)))
    [board nil]))

(defn kill-bead-event* [[bead cell]]
  [(lble/kill-bead bead)
   (lble/move-bead bead cell (lblc/start+1-cell))])

(defn kill-bead
  "Kill the `bead` at `cell` and move the `bead` to cell-18 (start-cell)
   And deduct the points from that `bead` color"
  [board bead cell]
  (let [dst-cell (lblc/start+1-cell)
        bead-path PATHS
        points (dec (.indexOf (into [] bead-path) cell))
        player-id (.indexOf COLORS (:color bead))
        score (get-in board [:players player-id :score])]
    [(-> board
         (assoc-in [:beads bead] dst-cell)
         (update-in [:players player-id :score] - points))
     [(lble/kill-bead bead)
      (lble/update-score player-id (nth lblb/COLORS player-id) (- points) (- score points) 0)
      (lble/move-bead bead cell dst-cell)]]))

(defn kill-bead-reducer
  "Kill the `bead` at `cell` and move the `bead` to cell-18 (start-cell)
   And deduct the points from that `bead` color. Reset the distance moved
   by the killed `bead`"
  [[board evs] [bead cell]]
  (let [[board' evs'] (kill-bead board bead cell)]
    [board' (into [] (concat evs evs'))]))

(defn check-kill*
  [board player-id bead cell]
  (if-let [cellmates (seq (lblc/other-cellmates board bead cell))]
    (let [[board1 evs] (reduce kill-bead-reducer [board []] cellmates)
            extra-roll (inc (rand-int 6))
            board2 (-> board1
                       (update :turns inc)
                       (update-in [:players player-id :rolls] conj extra-roll))
            score  (get-in board2 [:players player-id :score])
            evs' (conj evs (lble/update-score player-id (nth lblb/COLORS player-id) 0 score extra-roll))]
        [board2 (map #(notify2 % :all) evs')])
    [board nil]))

(defn check-snake-landing
  [board player-id bead cell]
  (if-let [dst-cell (first (map #(get % :to) (filter #(= (:from %) cell) SNAKES)))]
    (let [points (- (:id cell) (:id dst-cell))
          score (get-in board [:players player-id :score])]
      [(-> board
           (assoc-in [:beads bead] dst-cell)
           (update-in [:players player-id :score] - points))
       [(lble/snake bead cell dst-cell)
        (lble/update-score player-id (nth lblb/COLORS player-id) (- points) (- score points) 0)
        (lble/move-bead bead cell dst-cell)]])
    [board nil]))

(defn check-ladder-climbing
  [board player-id bead cell]
  (if-let [dst-cell (first (map #(get % :to) (filter #(= (:from %) cell) LADDERS)))]
    (let [points (- (:id dst-cell) (:id cell))
          _ (log/info "DST CELL" dst-cell)
          score (get-in board [:players player-id :score])
          board1 (-> board
                     (assoc-in [:beads bead] dst-cell)
                     (update-in [:players player-id :score] + points))]
      [board1
       [(lble/ladder bead cell dst-cell)
        (lble/update-score player-id (nth lblb/COLORS player-id) (+ points) (+ score points) 0)
        (lble/move-bead bead cell dst-cell)]])
    [board nil]))

(defmethod calc-winner :snakesladders
  [board player-id color]
  (let [sorted-players (into {} (sort-by (comp - :score val) (:players board)))
        winner-color (nth COLORS (:seat (first (vals sorted-players))))
        winner (ffirst sorted-players)]
    [(assoc board :winner winner :status :finished) [(notify2 (lble/winner winner winner-color) :all)]]))

(defmethod round-ended? :snakesladders
  [board player-id]
  (let [ended? (every? zero? (map (fn [p] (-> p :rolls count)) (vals (:players board))))
        max-turn-count (:max-turn-count board)]
    (if (or ended?
            (and (some? max-turn-count) (>= (:turn-count board) max-turn-count)))
      (let [sorted-players (sort-by (comp :score val) (:players board))
            winner (ffirst sorted-players)]
        [true winner])
      [false nil])))

(defn- do-move-bead
  [{:keys [current-player beads die-roll turns] :as board} player-id bead]
  (let [cell (get beads bead)
        current-color (nth lblb/COLORS current-player)]
    (if (lblc/move-possible? current-color cell die-roll)
      (let [cell' (lblc/nth-cell cell die-roll)
            points (if (lblc/home-cell? cell') (+ die-roll 56) die-roll)
            board' (-> board
                       (assoc :die-roll nil)
                       (update :turns dec)
                       (update-in [:players current-player :score] + points)
                       (assoc-in [:beads bead] cell')
                       (end-turn player-id :move-bead))
            score (get-in board' [:players current-player :score])
            evs (notify2 [(lble/move-bead bead cell cell')
                          (lble/update-score player-id current-color points score)] :all)]
        [board' evs])
      [nil (notify2 (error :move-not-possible current-player) current-player)])))

(defmethod move-bead :snakesladders
  [{:keys [current-player beads die-roll turns] :as board} player-id bead]
  (let [current-color (nth lblb/COLORS current-player)
        [board' evs] (do-move-bead board player-id bead)
        cell' (get-in board' [:beads bead])]
    (wae-> [board' evs]
           (check-snake-landing player-id bead cell')
           (check-ladder-climbing player-id bead cell')
           (check-kill* player-id bead cell')
           (check-reroll* player-id current-color die-roll))))

(defmethod move-bead-and-if-winner :snakesladders
  [{:keys [current-player die-roll] :as board} player-id bead]
  (let [current-color (nth lblb/COLORS current-player)
        [board' evs] (move-bead board player-id bead)]
    (if (some? board')
      (let [[ended? winner] (round-ended? board' player-id)]
        (if ended?
          (wae-> [board' evs]
                 (calc-winner player-id bead)
                 (finish-round))
          [board' evs]))
      [board' evs])))

(defmethod handle-cmd :move-bead
  [{:keys [status die-roll] :as round} player-id {{beadId :beadId} :data :as cmd}]
  (better/cond
   (out-of-turn? round player-id)
   (oot-error round player-id)

   (not= :move-bead (:status round))
   (cmd-error :state-cmd-mismatch player-id)

   (not= (get-in round [:players player-id :status]) :running)
   [nil (error :unauthorized-move player-id)]

   :let [bead (->bead beadId)]

   (not= (nth lblb/COLORS player-id) (:color bead))
   [nil (error :unauthorized-move player-id)]

   (nil? die-roll)
   [nil (error :invalid-die-roll player-id)]

   :else
   (move-bead-and-if-winner round player-id bead)))
