(ns snakesladders.worker
  "Processes all the server initiated async tasks
  Delegates to the game server to process the tasks"
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as a]
   [engine.push :refer [push-all]]
   [snakesladders.game.server :as srv]))

(defn ping
  [uuid]
  (log/info "[process-task] ping" uuid)
  (let [game (srv/get-game uuid)]
    (push-all "ping" (:players game))))

(defn process-task
  [cmd uuid]
  (log/info "Processing task")
  (case (:task cmd)
    :ping
    (ping uuid)

    :timeout
    (srv/handle-timeout (:cmd cmd) uuid)

    :stage-timeout
    (srv/handle-stage-timeout (:stage cmd) uuid)

    (log/error "Cannot process task" cmd uuid)))

(defn start-worker
  [worker-chan]
  (a/go-loop []
    (let [[game-uuid task] (a/<! worker-chan)]
      (log/info "[processing-worker-channel]" game-uuid task)
      (process-task task game-uuid))
    (recur)))
