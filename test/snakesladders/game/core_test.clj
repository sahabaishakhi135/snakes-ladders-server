(ns snakesladders.game.core-test
  (:require
    [clojure.test :refer :all]
    [com.pekaplay.card.card :as c]
    [snakesladders.test.config :refer [wrap-setup]]
    [snakesladders.game.core :refer [sort-by-cards]]))

(use-fixtures :once wrap-setup)

(deftest turn-order-with-ace-n-joker
  (let [players [1 2 3 4 5 6]
        cards   (map c/parse ["as" "2s" "kd" "5c" "tc" "jr"])
        ordered-players (sort-by-cards players cards)]
    (is (= (map first ordered-players) [6 1 3 5 4 2]))))

(deftest turn-order-with-ace
  (let [players [1 2 3 4 5 6]
        cards   (map c/parse ["as" "2s" "kd" "5c" "tc" "3s"])
        ordered-players (sort-by-cards players cards)]
    (is (= (map first ordered-players) [1 3 5 4 6 2]))))

(deftest turn-order-with-ace
  (let [players [1 2 3 4 5 6]
        cards   (map c/parse ["as" "2s" "ac" "ad" "tc" "ah"])
        ordered-players (sort-by-cards players cards)]
    (is (= (map first ordered-players) [1 6 4 3 5 2]))))
