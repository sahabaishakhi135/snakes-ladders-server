(ns snakesladders.handlers.rs
  (:require
    [com.pekaplay.uuid :refer [uuid-str]]
    [org.httpkit.server :refer [with-channel on-close on-receive]]
    [engine.push :refer [register! unregister! push]]
    [clojure.tools.logging :as log]
    [com.pekaplay.util :refer [msg->cmd]]
    [engine.comms :as comms]
    [snakesladders.game.server :as srv]
    [snakesladders.round.events :as re :refer [error]]))

(defmulti process-cmd
  (fn [cmd _ch-id]
    (:type cmd)))

(defmethod process-cmd :echo
  [cmd ch-id]
  (push cmd ch-id))

(defmethod process-cmd :join
  [cmd ch-id]
  (srv/handle-join cmd ch-id))

(defmethod process-cmd :default
  [cmd ch-id]
  (if-let [game-id (or (:roomId cmd) (-> cmd :data :roomId))]
    (srv/handle-cmd cmd ch-id game-id)
    (do
      (log/error "missing-room-id in cmd" cmd)
      (push (error :missing-room-id ch-id) ch-id))))

(defmethod comms/-event-msg-handler :peka/play
  [ev-msg]
  (let [{:keys [event uid id ?data ring-req ?reply-fn send-fn]} ev-msg]
    (log/debug "comms/-event-msg-handler" ?data uid)
    (process-cmd ?data uid)))

; web socket handler
(defn handle-cmd
  [req]
  (let [ch-id (uuid-str)]
    (with-channel req channel
      (register! ch-id channel req)

      (on-receive channel
                  (fn [msg]
                    (log/debug "recvd" ch-id msg)
                    (case msg
                      "pong"
                      (log/debug "ping ack from" ch-id)
                      "ping"
                      (push "pong" ch-id)
                      (process-cmd (msg->cmd msg) ch-id))))

      (on-close channel
                (fn [status]
                  (unregister! ch-id))))))