(ns snakesladders.round.cmds
  (:require
   [clojure.tools.logging :as log]
   [com.pekaplay.events :refer [notify2]]
   [com.pekaplay.timer :as timer]
   [engine.util :refer [wae->]]
  ;;  [engine.player :refer [debit credit get-balance]]
   [snakesladders.round.core :as core]
   [snakesladders.board.snakesladders.events :as lble]
   [snakesladders.round.events :as events :refer [error]]

   ; Handle cmds
   [snakesladders.round.cmds.cmd :as cmd-impl :refer [change-turn]]

   ; round cmd impls
   [snakesladders.round.cmds.move-bead]
   [snakesladders.round.cmds.roll-die]
   [snakesladders.round.cmds.im-back]
   [snakesladders.round.cmds.sync]
   [snakesladders.round.cmds.chat]

   ; round stage impls
   [snakesladders.round.stages.turn]
   [snakesladders.round.stages.move-bead]
   [snakesladders.round.stages.roll-die]
   [snakesladders.round.stages.end-round]))

(defn start
  [round cards players turn-order]
  (let [round' (core/init round cards players turn-order)
        players' (vals (:players round'))]
    (doseq [[s plyr] (:players round')]
      (log/info "Seat:" s "Player:" (get-in plyr [:player :alias])))
    [(assoc round' :status :dealing) [(notify2 (events/ready players' turn-order (:game-time (:config round))) :all)]]))

(defn start-turns
  [round]
  (wae-> round
         (change-turn)))

(defn distribute-rolls
  [round]
  (let [r (core/distribute-rolls* round)
        {:keys [players turn-order config player-id current-player]} r
        num-rolls (count (get-in round [:players player-id :rolls]))
        remaining-turn (timer/remaining (get-in round [:timers (str ":turn-" current-player)]))
        _ (log/info "BEADS" (:beads r))
        evs (for [s turn-order]
              (notify2
               (lble/sync-board (:beads r)
                                (get-in players [s :used])
                                (get-in players [s :rolls])
                                (:num-rolls config)
                                (->> players
                                     (core/scoreboard)
                                     (map (fn [[k v]] [k (:score v)]))
                                     (into {}))
                                turn-order
                                remaining-turn)
               s))]
    [r evs]))

(defn distribute-cards
  [round]
  (let [r (core/distribute-cards* round)]
    [r nil]))

(defn handle-timeout
  [round player-id timeout-cmd]
  (try
    (if-let [res (cmd-impl/handle-timeout round player-id timeout-cmd)]
      res
      [nil []])
    (catch Exception e
      (.printStackTrace e)
      [nil (error (or (:error (ex-data e)) :move-error) player-id)])))

(defn handle-cmd
  [round player-id cmd]
  (try
    (cmd-impl/handle-cmd round player-id cmd)
    (catch Exception e
      (.printStackTrace e)
      [nil (error (or (:error (ex-data e)) :move-error) player-id)])))

(comment
  (require '[com.pekaplay.uuid :refer [uuid-str]]
           '[ludo.round :refer [mk-round]]
           '[ludo.round.cmds.cmd :as cmd])

  (def round {:current-player 0
              :turn-count 0
              :players {0 {:seat 0, :status :running}
                        1 {:seat 1, :status :running}
                        2 {:seat 2, :status :running}
                        3 {:seat 3, :status :running}}
              :status :ready
              :turn-order [0 1 2 3]})

  (let [[r evs] (change-turn round)]
    (change-status r :move-bead {}))

  (roll-die round :yellow))
