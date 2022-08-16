(ns snakesladders.game.handlers
  (:require
   [clojure.tools.logging :as log]
   [medley.core :refer [dissoc-in]]
   [com.pekaplay.util :refer [error?]]

   [snakesladders.event-sourcing :as es]
   [snakesladders.event-sourcing.core :refer [->Event ->Cmd]]

   [snakesladders.game :as game]
   [snakesladders.game.cmds :as cmds]
   [snakesladders.game.processor :refer [process-events process-round-result]]
   [snakesladders.game.core :as core]
   [snakesladders.round.events :as re :refer [error]]
   [snakesladders.round.handlers :as rh]))

(def ^:const GAME-TYPE :snakesladders)

(defn- same-channel?
  [ch-id [_k v]]
  (= ch-id (:channel v)))

(defn- player-from-channel
  [game ch-id]
  (log/debug (:players game) ch-id)
  (->> (:players game)
       (filter (partial same-channel? ch-id))
       ffirst))

(defn publish-event!
  "Publish the list of events `evs` using the event sourcing system"
  [{:keys [uuid] :as game} evs]
  (let [round-uuid (-> game (core/current-round) :uuid)]
    (if (map? evs)
      (let [{event :type, data :data, to :to} evs]
        (es/publish-event! (->Event event data uuid round-uuid to)))
      (doseq [ev evs]
        (let [{event :type, data :data, to :to} ev]
          (es/publish-event! (->Event event data uuid round-uuid to)))))))

;; Round handlers for cmd and timeouts
;;
;; The resultant evs must be processed by the game
(defn handle-round-cmd
  [game round player-id cmd]
  (if (some? round)
    (let [[round' evs :as res] (rh/handle-cmd round player-id cmd)]
      (if (error? res)
        [nil evs]
        (process-round-result game round' evs)))

    [game nil]))

(defn handle-round-timeout
  [game round player-id cmd]
  (if (some? round)
    (let [[round' evs :as res] (rh/handle-timeout round player-id cmd)]
      (if (error? res)
        [nil evs]
        (process-round-result game round' evs)))

    [game nil]))

(defn alloc-seat-in-game
  [game profile ch-id]
  (log/debug "alloc-seat-in-game" profile)
  (let [[seat [game1 evs :as res]] (game/alloc-seat game profile ch-id)]
    (if (and (some? seat) (some? game1))
      (let [[game2 evs1] (process-events game1 evs)
            [_ evs2]     (cmds/handle-cmd game2 seat {:type :sync})]
        (publish-event! game1 evs1)
        [game2 (concat evs1 evs2)])

      ; If the command is invalid (returns nil game), just publish events
      ; Just don't process them
      (do
        (publish-event! game evs)
        res))))

(defn handle-cmd
  [game ch-id {typ :type, data :data :as cmd}]
  (if-let [player-id (player-from-channel game ch-id)]
    (let [round (core/current-round game)
          [game' evs :as res] (if (cmds/game-cmd? game cmd)
                               (cmds/handle-cmd game player-id cmd)
                               (handle-round-cmd game round player-id cmd))]
      (es/publish-cmd! (->Cmd typ data (:uuid game) (:uuid round) player-id))
      (publish-event! game' evs)
      res)

    (do
      (log/error "handle-cmd: Invalid player" ch-id game cmd)
      [nil (error :invalid-player [:chan ch-id])])))

(defn handle-stage-timeout
  [game stage]
  (if (cmds/game-timeout? game stage)
    (let [[game' evs] (cmds/handle-timeout game stage)]
      (publish-event! game' evs)
      (process-events game' evs))

    (log/error "Invalid stage timeout" stage (:uuid game))))

(defn handle-timeout
  "Other game related cmds follow the handle-stage-timeout code path. Player's autoplay
  cmds are handled here"
  [game cmd]
  (let [round     (core/current-round game)
        player-id (get-in cmd [:data :player-id])
        cmd'      (dissoc-in cmd [:data :player-id])
        [game' evs :as res] (handle-round-timeout game round player-id cmd)]
    (es/publish-cmd! (->Cmd (:type cmd') (:data cmd') (:uuid game) (:uuid round) player-id))
    (publish-event! game' evs)
    res))
