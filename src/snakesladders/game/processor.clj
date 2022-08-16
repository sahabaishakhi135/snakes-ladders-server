(ns snakesladders.game.processor
  (:require
   [clojure.tools.logging :as log]
   [better-cond.core :as better]
   [engine.timeouts :as t]
   [engine.manager :as mgr]
   [engine.util :refer [wae->]]

   [snakesladders.stages :refer [start-stage]]
   [snakesladders.game.core :as core]
   [snakesladders.game-types :as gt]))

(defn- finish-game-maybe
  [game round]
  (better/cond
   (not= :finished (:status round))
   nil

   :let [uuid                 (:uuid game)
         [game1 evs]          (gt/reconcile-score game round)
         [game-ended? winners] (gt/game-ended? game1)]

   game-ended?
   (do
     (log/info "game ended!!!" uuid)
     (wae-> [game1 evs]
            (core/finish-game winners)))

   :let [[game2 evs1 :as res] (gt/reconcile-round game1 round)]

   (some? res)
   [game2 (concat evs evs1)]

   :else
   (wae-> [game1 evs]
          (start-stage :starting-round))))


(defmulti process-event (fn [[_game _evs] ev] (:type ev)))

(defmethod process-event :start-task
  [[game evs] ev]
  (mgr/start-stage (-> ev :data :task) (:uuid game) 0)
  [game evs])

(defmethod process-event :start-stage-timeout
  [[game evs] ev]
  (let [game' (t/mk-stage-timeout! game (:data ev))]
    [game' evs]))

;; (defmethod process-event :set-player-status
;;   [[game evs] ev]
;;  (let [{:keys [status player-id]} (:data ev)
;;        game' (assoc-in game [:players player-id :status] status)]
;;    [game' evs]))

(defmethod process-event :scoreboard
  [[game evs] ev]
  (let [scoreboard' (gt/enrich-round-scoreboard game :running (get-in ev [:data :scoreboard]))]
    [game (conj evs (assoc-in ev [:data :scoreboard] scoreboard'))]))

(defmethod process-event :stop-round
  [[game evs] ev]
  (let [scoreboard' (gt/enrich-round-scoreboard game :finished (get-in ev [:data :scoreboard]))]
    [game (conj evs (assoc-in ev [:data :scoreboard] scoreboard'))]))

(defmethod process-event :start-timeout
  [[game evs] ev]
  (let [round (core/current-round game)
        round' (t/mk-timeout! round (:data ev))]
    [(core/+current-round game round') evs]))

; for non special events, no processing needed
(defmethod process-event :default
  [[game evs] ev]
  [game (conj evs ev)])


(defn process-events
  "Returns the [game events] tuple"
  [game evs]
  (reduce process-event [game []] evs))

(defn process-round-result
  [game round evs]
  (if-let [[game' evs'] (finish-game-maybe game round)]
    (do
      (log/debug "process-round-result" (concat evs evs'))
      (process-events game' (concat evs evs')))
    (do
      (log/debug "process-round-result2" evs)
      (process-events (core/+current-round game round) evs))))
