(ns snakesladders.event-sourcing.core)

(defrecord Event [event data game-uuid round-uuid to])

(defrecord Cmd [cmd data game-uuid round-uuid from])