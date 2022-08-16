(ns snakesladders.round.cmds.cmd
  (:require
   [clojure.tools.logging :as log]
   [environ.core :refer [env]]
   [com.pekaplay.math :refer [->int]]
   [com.pekaplay.events :refer [notify2]]
   [engine.timeouts :as t]
   [com.pekaplay.timer :as timer]
   [engine.util :refer [wae-> +nil]]
   [snakesladders.board.snakesladders.board :as lblb]
   [snakesladders.board.snakesladders.events :as lble]
   [snakesladders.round.core :as core]
   [snakesladders.round.events :as events :refer [error]]))

;; Multimethods spec
(defmulti handle-cmd
  (fn [_round _player-id cmd]
    (:type cmd)))

(defmulti handle-timeout
  (fn [_round _player-id cmd]
    (:type cmd)))

(defmethod handle-cmd :default
  [_round player-id _cmd]
  [nil (error :invalid-cmd player-id)])

(defmethod handle-timeout :default
  [_round _player-id _cmd]
  [nil  (error :invalid-stage {})])

(defn game-type-dispatch [round] (get-in round [:config :game-type]))

(defn cmd-error
  [error-code player-id]
  [nil (error error-code player-id)])

(defn oot-error
  [round plyr-id]
  (let [curr-plyr-id (:current-player round)]
    [nil (error :out-of-turn2 {:expected curr-plyr-id :got plyr-id} plyr-id)]))

(defn out-of-turn?
  [round plyr-id]
  (let [curr-plyr-id (:current-player round)]
    (not= curr-plyr-id plyr-id)))

(defn TURN-TIMER [] (or (->int (:turn-timeout env)) 15))

(defn AFK-TIMER [] 3)

(defn get-timeout-duration
  "As a FSM, we are dealing with status

  Entering a state would mean
  - assoc'ing the round with that status
  - starting a timer (with some duration)
  - broadcasting the next-turn event to all players

  So when a player's turn starts, the status is :roll-die. Once the player picks a
  die, the round is immediately moved to the :discarding status.

               +-------------------+
               ↓                   |
   turn* -> roll-die -> move-bead -+
    ↑______________________________|

  However, the amount of time left in :discarding state is the total turn timer
  minus the time used for :roll-die. This necessitates the creation of a quasi
  called :turn, which is essentially used for time-keeping.

  In fact, as per Hierarchical FSMs, :turn is a parent state of :roll-die, :discarding
  states
  "
  [round player-id status]
  (if (= :afk (get-in round [:players player-id :status]))
    (AFK-TIMER)

    (case status
      :turn
      (TURN-TIMER)

      :roll-die
      (TURN-TIMER)

      :move-bead
      (let [remaining (t/eta round player-id :turn)]
        (if (and (some? remaining) (> remaining 0)) remaining 5))

      0)))

(defn change-status
  "Transition to a new timed state. After the given duration, the status will timeout and
   will need to be handled by the system.

   If the transition is flagged to silent, the transition into this state would not
   be broadcasted to any of the listening entities e.g players"

  ([round status data]
   (change-status round status data false)) ; silent? is false by default

  ([round status data silent?]
   (if (some? data)
     (let [{:keys [current-player turn-count]} round
           data'  (assoc data :turn-count turn-count)
           period (get-timeout-duration round current-player status)
           ev (t/timeout current-player status data' (+ period 2))  ; add a buffer of 2 secs

           ev1 (notify2 (events/next-turn current-player status period) :all)]
       [(assoc round :status status) (if silent? [ev] [ev ev1])])

     [(assoc round :status status) []])))

(defmulti change-turn (fn [round] (game-type-dispatch round)))

(defn pre-roll-dice
  [{:keys [players current-player] :as round}]
  (let [rolls (get-in players [current-player :rolls])]
    [round (notify2 (lble/pre-roll current-player
                                   (nth lblb/COLORS current-player)
                                   (take 3 rolls)
                                   (count rolls))
                    current-player)]))

(defmethod change-turn :snakesladders
  [round]
  (let [round' (core/start-turn round)]
    (wae-> round'
           (change-status :turn {} true)
           (pre-roll-dice)
           (change-status :roll-die {}))))

(defn end-status
  [round player-id status]
  (t/cancel-timeout! round player-id status))

(def end-status* (comp +nil end-status))

(defn end-turn
  ([round player-id]
   (end-turn round player-id :turn))

  ([round player-id turn]
   (t/cancel-timeout! round player-id turn)))

(def end-turn* (comp +nil end-turn))

(defn cancel-all-timers!
  [{:keys [uuid] :as round}]
  (log/info "Cancelling all timers for round" uuid)
  (doseq [[k t] (:timers round)]
    (log/debug "Canceling timer" k "for round" uuid)
    (timer/cancel t))
  (dissoc round :timers))

(defn show-stop-scoreboard
  [{:keys [players] :as round}]
  [round [(notify2 (events/stop (core/scoreboard players)) :all)]])

(defn finish-round
  [round]
  (-> round
      (cancel-all-timers!)
      (assoc :status :finished)
      (show-stop-scoreboard)))
