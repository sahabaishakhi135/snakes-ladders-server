(ns snakesladders.round.events
  (:require
    [clojure.tools.logging :as log]
    [snakesladders.board.snakesladders.board :refer [mk-board COLORS]]))

(def ^:const TIMEOUT 15)

(defn error
  ([err seat]
    {:type :error, :data {:error err}, :to seat})
  ([err data seat]
    {:type :error, :data (assoc data :error err), :to seat}))

; snakesladders round specific events
(defn ready
  ([players turn-order]
   {:type :start-round
   :data {:statuses (into {}
                          (map (fn [{:keys [seat status] :as _p}]
                                 [seat status])
                               players))
          :turn-order turn-order,
          :dealer (last turn-order)}})
  ([players turn-order game-time]
   {:type :start-round
   :data {:statuses (into {}
                          (map (fn [{:keys [seat status] :as _p}]
                                 [seat status])
                               players))
          :turn-order turn-order,
          :dealer (last turn-order)
          :game-time game-time}}))

(defn next-turn
  ([player move]
    (next-turn player move TIMEOUT))
  ([player move timeout]
    {:type :next-turn
     :data {:player player, :color (nth COLORS player), :move move}
     :timeout timeout}))

(defn deal
  ([hand open joker]
   {:type :deal
    :data {:hand hand :open open :joker joker}})
  ([hand open joker discards]
   {:type :deal
    :data {:hand hand :open open :joker joker :discards discards}}))

(defn turn-order [turn-order]
  {:type :turn-order
   :data {:turn-order turn-order
          :player-colors (map #(do [% (nth COLORS %)]) turn-order)}})

(defn turn-order-with-cards [ordered-seats]
  (let [order (map (comp first first) ordered-seats)
        player-cards (map (fn [[[s _p] c]] [s c]) ordered-seats)
        player-colors (map (fn [[[s _p] _c]] [s (nth COLORS s)]) ordered-seats)]
    [{:type :seat-cards
      :data {:player-cards player-cards, :player-colors player-colors}}
     (turn-order order)]))

(defn pick [from player-id card]
  {:pre [(#{:stock :discards} from)]}
  {:type :pick
   :data {:from from, :card card, :by player-id}})

(defn discard [player-id card]
  {:type :discard
   :data {:card card, :by player-id}})

(defn show-card [player-id card]
  {:type :show-card
   :data {:card card, :by player-id}})

(defn drop-player [player-id]
  {:type :drop
   :data {:player player-id}})

(defn middle-drop [player-id]
  {:type :middle-drop
   :data {:player player-id}})

(defn winner [player-id]
  {:type :winner
   :data {:player player-id}})

(defn stop [scoreboard]
  {:type :stop-round
   :data {:scoreboard scoreboard}})

(defn scoreboard [scoreboard]
  {:type :scoreboard
   :data {:scoreboard scoreboard}})

(defn do-meld [timeout]
  {:type :do-meld
   :timeout timeout})

(defn remix
  [reason]
  {:type :remix
   :data {:reason reason}})

(defn pot-update
  [pot]
  {:type :pot
   :data {:amount pot}})
