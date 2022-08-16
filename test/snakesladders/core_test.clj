(ns snakesladders.core-test
    (:require
      [cheshire.core :as json]
      [clojure.test :refer :all]
      [com.pekaplay.util :refer [same?]]
      [com.pekaplay.card.deck :as d]
      [snakesladders.round.core :as core]))

(defn mk-player [id seat]
    {:seat seat
     :id id
     :alias (name id)
     :type :test})

(defn mk-players [ids]
    (into {}
          (map-indexed (fn [i id] [i (mk-player id i)]) ids)))

(defn mk-player-ids [n]
    (map #(keyword (str "p" %)) (range 1 (inc n))))

(deftest distribute-cards
    (testing "simple deck"
      (let [deck (d/mk-deck)
            players (mk-players (mk-player-ids 2))
            [h s o j] (core/dist-cards deck players)
            cards (flatten (concat h s [o j]))]
        (is (= (count cards) 52))
        (is (same? cards deck))))

    (testing "two decks"
      (let [deck (d/mk-deck 2 2)
            players (mk-players (mk-player-ids 6))
            [hands, stock, open, joker] (core/dist-cards deck players)
            cards (flatten (concat hands stock [open joker]))]
        (is (= (count players) (count hands)))
        (doseq [h hands] (is (= 13 (count h))))
        (is (= (count (flatten hands)) (* 13 (count players))))
        (is (same? cards deck))
        (is (= (count (distinct cards)) 53)))))