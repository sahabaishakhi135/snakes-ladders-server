(ns snakesladders.round.stages.move-bead
  (:require
   [clojure.tools.logging :as log]
   [snakesladders.board.snakesladders.board :as lblb]
   [snakesladders.board.snakesladders.util :refer [->beadId]]
   [snakesladders.round.cmds.cmd :refer [handle-cmd handle-timeout]]))

(defmethod handle-timeout :move-bead
  [round player-id cmd]
  (when (= (:status round) :move-bead)
   (loop [ids (range 4)]
     (if-let [id (first ids)]
       (let [bead-id (->beadId {:color (nth lblb/COLORS player-id) :id id})
             [round' _evs :as res] (handle-cmd round player-id {:type :move-bead
                                                                :data {:beadId bead-id}})]
         (if (some? round')
           res
           (do (log/info "handle-timeout :move-bead" res bead-id (:status round'))
               (recur (next ids)))))
       (log/error "Not able to find a valid move-bead move for any bead" player-id cmd)))))
