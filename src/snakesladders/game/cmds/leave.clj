(ns snakesladders.game.cmds.leave
  (:require
   [clojure.tools.logging :as log]
   [better-cond.core :as better]
   [com.pekaplay.events :refer [notify2]]

   [engine.util :refer [wae->]]

   [snakesladders.round.events :refer [error]]
   [snakesladders.game.core :as core]
   [snakesladders.game :refer [dealloc-seat update-pot change-status-maybe]]
   [snakesladders.game.cmds.cmd :refer [handle-cmd game-cmd?]]))

(defn leave-table
  [game plyr-id]
  (let [buyin (get-in game [:config :bet-value])]
    (wae-> game
           (dealloc-seat plyr-id)
           (update-pot (- buyin) :all)
           (change-status-maybe))))

(defn- leave-table-allowed?
  [game plyr-id]
  (let [round            (core/current-round game)
        game-status      (:status game)
        game-plyr-status (get-in game [:players plyr-id :status])
        plyr-status      (get-in round [:players plyr-id :status])]
    (cond
      (contains? #{:left} game-plyr-status)
      false

      (contains? #{:ready :waiting} game-status)
      true

      :else
      (log/error "Leave Table" plyr-id (get-in game [:config :game-type]) game-plyr-status plyr-status))))

(defn handle-leave-table
  [game plyr-id]
  (if (leave-table-allowed? game plyr-id)
    (wae-> game
           (leave-table plyr-id))
    [nil (error :leave-table-disallowed plyr-id)]))

(defmethod handle-cmd :leave
  [game plyr-id _cmd]
  (log/info "Handle leave cmd" plyr-id _cmd)
  (handle-leave-table game plyr-id))

(defmethod game-cmd? :leave [_ _] true)
