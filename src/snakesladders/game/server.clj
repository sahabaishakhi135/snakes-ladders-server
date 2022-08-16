(ns snakesladders.game.server
  (:require
    [clojure.tools.logging :as log]
    [clojure.core.async :as a]
    [better-cond.core :as better]
    [com.pekaplay.timer :as timer]
    [engine.util :refer [gcmd]]
    [snakesladders.helpers.director :refer [stop-game abort-game]]
    [snakesladders.round.events :as events :refer [error]]
    [snakesladders.games :as games]
    [engine.push :refer [push]]
    [snakesladders.game.handlers :as gh]))

(def games (atom {}))

(defn get-game [uuid]
  (get @games uuid))

(defn delete-game! [uuid]
  (swap! games dissoc uuid))

(defn mk-game! [game-type table-type]
  (games/mk-game! games game-type table-type))

(defn stop-game! [uuid]
  (let [game      (get-game uuid)
        ping-task (:pinger @game)]
    (log/info "Reporting stop-game to director" uuid)
    (stop-game uuid)

    ;; (log/info "Canceling ping task" ping-task)
    ;; (timer/cancel ping-task)

    (log/info "Deleting game" uuid)
    (delete-game! uuid)))

(defn abort-game! [uuid]
  (let [game      (get-game uuid)
        ping-task (:pinger @game)]
    (log/info "Reporting abort-game to director" uuid)
    (abort-game uuid)

    ;; (log/info "Canceling ping task" ping-task)
    ;; (timer/cancel ping-task)

    (log/info "Deleting game" uuid)
    (delete-game! uuid)))

(defn abort-games []
  (doseq [uuid (keys @games)]
    (abort-game! uuid)))

; Handling joins as a queued command system
(def join-queue (a/chan))

(defn spawn-commander
  ([]
   (spawn-commander join-queue))
  ([cmd-chan]
   (a/go-loop [ev (a/<!! cmd-chan)]
              (let [[cmd ch-id] ev]
                (if (= (:type cmd) :join)
                  (let [res (games/handle-join-cmd games ch-id (:data cmd))]
                    (if (not= (type res) clojure.lang.Agent)
                      (push res ch-id)))
                  (log/error "Received invalid cmd" cmd "in cmd-chan")))
              (recur (a/<!! cmd-chan)))))

(defn handle-join
  [cmd ch-id]
  (a/>!! join-queue [cmd ch-id]))

; All game only commands are handled by the game which is an agent
(defn handle-cmd
  [cmd ch-id game-uuid]
  (log/info "Processing cmd" cmd game-uuid)
  (better/cond
    :let [game (get @games game-uuid)]

    (nil? game)
    (do
      (log/error "game not found" game-uuid)
      (push (error :game-not-found nil) ch-id))

    (= :finished (:status game))
    (do
      (log/error "game not found" game-uuid)
      (push (error :game-finished nil) ch-id))

    :else
    (send-off game gcmd gh/handle-cmd ch-id cmd)))

(defn handle-stage-timeout
  [stage game-uuid]
  (log/info "Processing stage timeout" stage game-uuid)
  (better/cond
    :let [game (get @games game-uuid)]

    (nil? game)
    (log/error "game not found" game-uuid)

    (= :finished (:status game))
    (log/error "game not found" game-uuid)

    (= stage :stopping-game)
    (stop-game! game-uuid)

    :else
    (send-off game gcmd gh/handle-stage-timeout stage)))

(defn handle-timeout
  [cmd game-uuid]
  (log/info "Processing cmd timeout" cmd game-uuid)
  (better/cond
    :let [game (get @games game-uuid)]

    (nil? game)
    (log/error "game not found" game-uuid)

    (= :finished (:status game))
    (log/error "game not found" game-uuid)

    :else
    (send-off game gcmd gh/handle-timeout cmd)))

(comment

  (def game-uuid "3c5f79f0-f75e-4e8e-b4f6-7bbc5e22a39f")
  (def ga (get @games game-uuid))
  (-> ga deref :rounds last clojure.pprint/pprint)
  (-> ga deref (dissoc :rounds) clojure.pprint/pprint)
  (map :rolls (-> ga deref :rounds last :players vals))
  (-> ga deref :turn-order)
  (-> ga deref :rounds last :turn-count)
  (handle-timeout {:type :roll-die :data {:player-id 0 :turn-count 96}} "3c5f79f0-f75e-4e8e-b4f6-7bbc5e22a39f")
  )