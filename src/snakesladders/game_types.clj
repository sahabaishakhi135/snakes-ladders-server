(ns snakesladders.game-types
  (:require
   [snakesladders.game-types.core :as gt]

   ; game-type impls
   [snakesladders.game-types.bestof]))

(def reconcile-score gt/reconcile-score)

(def game-ended? gt/game-ended?)

(def busted? gt/busted?)

(def enrich-round-scoreboard gt/enrich-round-scoreboard)

(def handle-cmd gt/handle-cmd)

(def game-cmd? gt/game-cmd?)

(def game-stage? gt/game-stage?)

(def stage-handler gt/stage-handler)

(def reconcile-round gt/reconcile-round)

(def sync-stage gt/sync-stage)

(def take-seat gt/take-seat)
