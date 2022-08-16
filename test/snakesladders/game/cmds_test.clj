(ns snakesladders.game.cmds-test
  (:require
    [clojure.test :refer :all]
    [clojure.tools.logging :as log]
    [com.pekaplay.card.card :as c]
    [engine.util :refer [wae->]]
    [snakesladders.test.config :refer [wrap-setup]]
    [snakesladders.game :as r]
    [snakesladders.game.core :as core]
    [snakesladders.game.cmds :as cmds]))

(use-fixtures :once wrap-setup)

(deftest turn-order-dealer
  (with-redefs [cmds/start-splits-maybe (fn [_game] nil)]
   (let [game {:players {0 {:status :ready}
                         1 {:status :running}
                         2 {:status :running}
                         3 {:status :running}
                         4 {:status :ready}
                         5 {:status :running}}
               :config {:points {:drop 20, :middle-drop 40, :full-count 80}}
              :dealer-index 2
              :turn-order [2 4 5 3 0 1]
              :rejoins {:accepts [0 4]}
              :rounds [{:turn-order [2 4 5 3 0 1]}
                       {:turn-order [4 5 3 0 1 2]}
                       {:turn-order [5 3 1 2]}]}
        [game' _evs] (cmds/close-rejoins game)
        game2        (core/next-dealer game')
        {:keys [turn-order dealer-index]} game2]
    (is (= turn-order [2 5 0 4 3 1]))
    (is (= dealer-index 3)))))

(defn get-event
  [ev-type evs]
  (let [ev (filter (fn [ev] (= ev-type (:type ev))) evs)]
    (if (= 1 (count ev))
      (first ev)
      ev)))

(deftest split-prizes-maybe
  (let [game {:players {0 {:status :running :score 100}
                        1 {:status :bust    :score 103}
                        2 {:status :running :score 60}
                        3 {:status :bust    :score 127}
                        4 {:status :running :score 63}
                        5 {:status :bust    :score 100}}
              :config {:game-type :pool
                       :bet-value 20
                       :points {:drop 20, :middle-drop 40, :full-count 80}
                       :num-rounds 101}
              :pot (* 6 20)}
        [game' evs] (cmds/start-splits-maybe game)
        split-prize-ev (get-event :split-prize evs)]
    (is (some? split-prize-ev))
    (is (= [0 2 4] (-> split-prize-ev :data :prize-split (keys) (sort))))
    (doseq [[s info] (-> split-prize-ev :data :prize-split)]
      (is (contains? info :dropsLeft))
      (is (contains? info :score))
      (is (contains? info :amount)))
    (is (some? (get-event :start-stage-timeout evs)))
    (is (some? (get-event :next-stage evs)))))

(deftest no-split-prizes
  (let [game {:players {0 {:status :bust    :score 236}
                        1 {:status :running :score 68}
                        2 {:status :running :score 182}
                        3 {:status :running :score 81}
                        4 {:status :bust    :score 208}
                        5 {:status :bust    :score 233}}
              :config {:game-type :pool
                       :bet-value 20
                       :points {:drop 20, :middle-drop 40, :full-count 80}
                       :num-rounds 201}
              :pot (* 6 20)}
        res (cmds/start-splits-maybe game)]
    (is (nil? res))))
