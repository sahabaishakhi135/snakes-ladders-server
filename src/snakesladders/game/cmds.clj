(ns snakesladders.game.cmds
  (:require
   [clojure.tools.logging :as log]
   [snakesladders.game.cmds.cmd :as cmd-impl]
   [snakesladders.game-types :as gt]

   ; game cmd impls
   [snakesladders.game.cmds.history]
   [snakesladders.game.cmds.sync]
   [snakesladders.game.cmds.leave]

   ; game timeout (stage) impls
   [snakesladders.game.stages.waiting]
   [snakesladders.game.stages.pick-seats]
   [snakesladders.game.stages.starting-round]
   [snakesladders.game.stages.starting-turns]))

(defn game-cmd?
  [game cmd]
  (or (cmd-impl/game-cmd? game cmd) (gt/game-cmd? game cmd)))

(defn handle-cmd
  [game player-id cmd]
  (log/info "Handling cmd" player-id cmd)
  (condp = true
   (cmd-impl/game-cmd? game cmd)
   (cmd-impl/handle-cmd game player-id cmd)

   (gt/game-cmd? game cmd)
   (gt/handle-cmd game player-id cmd)))

(defn game-timeout?
  [game stage]
  (or (cmd-impl/game-timeout? game stage) (gt/game-stage? game stage)))

(defn handle-timeout
  [game stage]
  (condp = true
   (cmd-impl/game-timeout? game stage)
   (cmd-impl/handle-timeout game stage)

   (gt/game-stage? game stage)
   (let [handler (gt/stage-handler game stage)]
     (handler game stage))))
