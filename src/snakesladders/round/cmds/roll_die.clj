(ns snakesladders.round.cmds.roll-die
  (:require
   [clojure.tools.logging :as log]
   [cheshire.core :as json]
   [better-cond.core :as better]
   [com.pekaplay.events :refer [notify2]]

   [engine.util :refer [wae-> +nil]]
   [snakesladders.round.events :as events :refer [error]]
   [snakesladders.board.snakesladders.events :as lble]
   [snakesladders.board.snakesladders.board :as lblb]
   [snakesladders.board.snakesladders.cmds :as lblc]
   [snakesladders.round.cmds.cmd :refer [out-of-turn? oot-error cmd-error end-turn* change-turn change-status handle-cmd game-type-dispatch]]))

(defmulti roll-die    (fn [board _player-id _data] (game-type-dispatch board)))

(defn bonus-roll-maybe?
  [board n]
  (if (= n 6)
    [(update board :turns inc) nil]
    [board nil]))

(defn vec-remove
  "remove elem in coll"
  [coll pos]
  (into (subvec coll 0 pos) (subvec coll (inc pos))))

(defn use-selected-number
  [rolls used-rolls n]
  (let [i (.indexOf (take 3 rolls) n)]
    (when (not= -1 i)
      [(vec-remove rolls i) (conj used-rolls n)])))

(defn roll-selected-die
  [{:keys [current-player] :as round} n]
  (let [{:keys [rolls used]} (get-in round [:players current-player])]
    (if-let [[rolls' used' :as res] (use-selected-number rolls used n)]
      (-> round
          (update-in [:players current-player] merge {:rolls rolls', :used used'})
          (assoc :die-roll n))
      (log/error "Error in using roll" rolls used n))))

(defn do-roll-die
  [board player-id color n]
  (let [board' (roll-selected-die board n)
        num-rolls (count (get-in board' [:players player-id :rolls]))
        evs [(notify2 (lble/die-roll color n num-rolls) :all)]]
    [n [board' evs]]))

(defmethod roll-die :snakesladders
  [board player-id {n :dieRoll :as _data}]
  (log/info "roll-die" player-id n (take 3 (get-in board [:players player-id :rolls])) (get-in board [:players player-id :rolls]))
  (better/cond
   (= -1 (.indexOf (take 3 (get-in board [:players player-id :rolls])) n))
   (do
     (log/warn "Expected:" (get-in board [:players player-id :rolls]) "Got:" n (json/encode _data))
     [nil (error :invalid-selection player-id)])

   :let [color (nth lblb/COLORS player-id)
         [n [board' evs]] (do-roll-die board player-id color n)]

   (lblc/moves-possible? board' color n)
   (wae-> [board' evs]
              (end-turn* player-id :roll-die)
              (change-status :move-bead {}))

   :else
   (wae-> [board' evs]
              (change-turn))))

(defmethod handle-cmd :roll-die
  [round player-id {data :data :as _cmd}]
  (better/cond
   (out-of-turn? round player-id)
   (oot-error round player-id)

   (not= :roll-die (:status round))
   (cmd-error :state-cmd-mismatch player-id)

   (not= (get-in round [:players player-id :status]) :running)
   [nil (error :unauthorized-move player-id)]

   :else
   (roll-die round player-id data)))