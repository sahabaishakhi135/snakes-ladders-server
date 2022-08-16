(ns snakesladders.game-types.core)

;; Dispatcher for game-type multimethods
(defn- game-type [game] (-> game :config :game-type))

;; Multi method Specs
(defmulti reconcile-score
  "Returns a [game evs] tuple"
  (fn [game _round] (game-type game)))

(defmulti game-ended?
  "Returns a [game-ended-boolean winner-seat-id] tuple"
  (fn [game] (game-type game)))

(defmulti busted?
  "Returns a boolean"
  (fn [game _seat-id _points] (game-type game)))

(defmulti enrich-round-scoreboard
  "Returns the enriched scoreboard as a map pf seat -> scoreboard record"
  (fn [game _round-status _round-scoreboard] (game-type game)))

(defmulti reconcile-round
  "Returns a [game evs] tuple"
  (fn [game _round] (game-type game)))

(defmulti handle-cmd
  "Returns a [game evs] tuple"
  (fn [game _plyr-id _cmd] (game-type game)))

(defmulti game-cmd?
  "Returns a boolean if this cmd is handled by the game-type"
  (fn [game _cmd] (game-type game)))

(defmulti game-stage?
  "Returns a boolean if this stage is specific to this game-type"
  (fn [game _stage] (game-type game)))

(defmulti stage-handler
  "Returns the stage handler which takes the args as [game stage] tuple"
  (fn [game _stage] (game-type game)))

(defmulti sync-stage
  "Returns the events that are returned in this stage"
  (fn [game] (game-type game)))

(defmulti take-seat
  "Make a buyin or something based on the game-type"
  (fn [game _plyr-adp] (game-type game)))

;; default impls for multi-methods
(defmethod reconcile-score :default [game _round] [game nil])

(defmethod game-ended? :default [_game] [false nil])

(defmethod busted? :default [_game _seat-id _points] false)

(defmethod enrich-round-scoreboard :default [_game _round-status round-scoreboard] round-scoreboard)

(defmethod reconcile-round :default [_game _round] nil)

(defmethod handle-cmd :default [_game _plyr-id _cmd] nil)

(defmethod game-cmd? :default [_game _cmd] false)

(defmethod game-stage? :default [_game _stage] false)

(defmethod stage-handler :default [_game _stage] nil)

(defmethod sync-stage :default [_game] nil)

(defmethod take-seat :default [_game _plyr-adp] [nil nil nil])