(ns snakesladders.games
  (:require
   [clojure.tools.logging :as log]
   [medley.core :refer [filter-vals]]
   [engine.util :refer [gcmd]]
   [snakesladders.round.events :refer [error]]
   [snakesladders.game :as game]
   [snakesladders.game.handlers :as gh]
   [snakesladders.event-sourcing :as es]
   [snakesladders.event-sourcing.core :refer [->Cmd]]
   [engine.player.adapter :refer [mk-player-adapter]]))

(def ^:const GAME-TYPE :snakesladders)

(defn- game-cmd-error-handler [ag ex]
  (.printStackTrace ex)
  (log/error ag ex)
  @ag)

(defn- valid-match?
  [table-type ga]
  (let [g (deref ga)]
    (and (game/available? g)
         (= table-type (:table-type g)))))

(defn pick-game
  "Returns one of the game agents"
  [games-atom table-type]
  (when-let [games (->> @games-atom
                        (filter (fn [[id g]] (valid-match? table-type g)))
                        (not-empty))]
    (let [kv (rand-nth games)]
      (second kv))))

(defn del-game!
  [games-atom uuid]
  (swap! games-atom dissoc uuid))

(defn mk-game!
  [games-atom game-type table-type]
  (let [game       (game/mk-game game-type table-type)
        uuid       (:uuid game)
        game-agent (agent game)]
    (set-error-handler! game-agent game-cmd-error-handler)
    (swap! games-atom assoc uuid game-agent)
    game-agent))

(defn- alloc-game
  [games-atom game-type table-type]
  (if-let [game (pick-game games-atom table-type)]
    game
    (mk-game! games-atom game-type table-type)))

(defn handle-join-cmd
  "Returns a error event to be sent directly to the channel (since the channel
  is not part of the game table) or sends the message off to the game agent
  so that the resultant events from the command may be sent asynchronously to
  the player channels"
  [games-atom ch-id cmd-data]
  (let [{game-id :roomId, table-type :tableType} cmd-data]
    (es/publish-cmd! (->Cmd :join cmd-data game-id nil ch-id))
    (log/debug "handle-join-cmd" cmd-data)
    (if-let [plyr-adp (mk-player-adapter cmd-data)]
      (if (some? game-id)
        ; Try to allocate a seat in the given game
        (if-let [game (get @games-atom game-id)]
          (send-off game gcmd gh/alloc-seat-in-game plyr-adp ch-id)
          [nil (error :room-does-not-exist [:chan ch-id])])

        ; Search for an eligible game or create one. Then try to allocate a seat
        ; in the given room
        (if-let [game (alloc-game games-atom GAME-TYPE table-type)]
          (send-off game gcmd gh/alloc-seat-in-game plyr-adp ch-id)
          [nil (error :could-not-alloc-seat [:chan ch-id])]))

      ; Error in fetching profile
      (do
        (log/error "fetch-profile-error" cmd-data)
        [nil (error :fetch-profile-error [:chan ch-id])]))))
