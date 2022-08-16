(ns snakesladders.round-test
  (:require
    [cheshire.core :as json]
    [clojure.tools.trace :as t]
    [clojure.core.match :refer [match]]
    [clojure.tools.logging :as log]
    [clojure.test :refer :all]
    [com.pekaplay.util :refer [same?]]
    [com.pekaplay.card.deck :as d]
    [com.pekaplay.card.card :as c]
    [engine.util :refer [wae->]]
    [snakesladders.round :as cr]
    [snakesladders.round.score :as s]
    [snakesladders.round.core :as core]
    [snakesladders.round.cmds :as cmds]))

(defn json-card [c] (json/decode (json/encode c) true))

(defn mk-round []
  (assoc (cr/mk-round) :config {:points {:drop 20, :middle-drop 40, :full-count 80}}))

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

(defn deal-2 []
  (let [round (mk-round)
        cards (d/mk-deck)
        turn-order (range 2)
        players (mk-players (mk-player-ids 2))]

    (wae-> round
           (cmds/start cards players turn-order)
           (cmds/distribute-cards)
           (cmds/start-turns))))

(defn deal-6 []
  (let [round (mk-round)
        cards (d/mk-deck 2 2)
        turn-order (range 6)
        players (mk-players (mk-player-ids 6))]
    (wae-> round
           (cmds/start cards players turn-order)
           (cmds/distribute-cards)
           (cmds/start-turns))))

(deftest empty-round-start
  (let [round (mk-round)
        cards (d/mk-deck)
        players (mk-players [:p1 :p2])
        turn-order [0 1]
        [round events] (cmds/start round cards players turn-order)]
    (is (= (-> round :players keys) [0 1]))
    (is (= (:deck round) cards))
    (doseq [k [:stockpile :discards :joker-card :open-card :current-player]]
      (is (nil? (get round k))))
    (doseq [[_ p] (:players round)]
      (is (= :ready (:status p))))))

(defn parse-deal-2-evs [evs]
  (let [{d1 :type {h1 :hand o1 :open j1 :joker} :data} (nth evs 1)
        {d2 :type {h2 :hand o2 :open j2 :joker} :data} (nth evs 2)
        {n :type, {p :player} :data, to :to} (nth evs 4)]
    (assert (= d1 d2 :deal))
    (assert (= n :next-turn))
    (assert (= to :all))
    (assert (= o1 o2))
    (assert (= j1 j2))
    [o1 j1 h1 h2 :p1]))

(defn parse-deal-6-evs [evs]
  (let [{d1 :type {h1 :hand o1 :open j1 :joker} :data} (nth evs 1)
        {d2 :type {h2 :hand o2 :open j2 :joker} :data} (nth evs 2)
        {d3 :type {h3 :hand o3 :open j3 :joker} :data} (nth evs 3)
        {d4 :type {h4 :hand o4 :open j4 :joker} :data} (nth evs 4)
        {d5 :type {h5 :hand o5 :open j5 :joker} :data} (nth evs 5)
        {d6 :type {h6 :hand o6 :open j6 :joker} :data} (nth evs 6)
        {n :type {p :player} :data, to :to} (nth evs 8)]
    (assert (= d1 d2 d3 d4 d5 d6 :deal))
    (assert (= n :next-turn))
    (assert (= to :all))
    (assert (= o1 o2 o3 o4 o5 o6))
    (assert (= j1 j2 j3 j4 j5 j6))
    (assert (= p 0))
    [o1 j1 h1 h2 h3 h4 h5 h6 :p1]))

(defn pick-n-discard [round player-id card]
  (let [card' (json-card card)
        [round' _] (cmds/move round player-id {:type :pick
                                               :data {:from :discards :card card'}})]
    (cmds/move round' player-id {:type :discard :data {:card card'}})))

(defn picked-card [evs]
  (let [f (fn [e] (and (= (:type e) :pick) (not (nil? (:card (:data e))))))
        ps (filter f evs)]
    (-> ps first :data :card)))

; deal cards
(deftest deal-allows-first-player-to-pick-from-discard
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o' (json-card o)
        [round' evs'] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})]
    (is (.contains evs' {:type :pick :data {:from :discards :by 0 :card o} :to :all}))
    (is (= 14 (count (get-in round' [:players 0 :hand]))))
    (is (= 0 (count (:discards round'))))))

(deftest deal-allows-first-player-to-pick-from-stockpile
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round' evs'] (cmds/move round 0 {:type :pick :data {:from :stock}})
        top-card (-> round :stockpile first)]
    (is (.contains evs' {:type :pick :data {:from :stock :by 0 :card top-card} :to 0}))
    (is (.contains evs' {:type :pick :data {:from :stock :by 0 :card nil} :to [:except 0]}))
    (is (= 14 (count (get-in round' [:players 0 :hand]))))
    (is (= 1 (count (:discards round'))))))

(deftest deal-gives-players-cards-and-assigns-current-player
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)]
    (is (.contains evs {:to :all :type :next-turn :data {:player 0} :timeout 15000}))
    (is (= (count h1) (count h2) 13))
    (is (not (same? h1 h2)))
    (is (= curr-player :p1))))

(deftest deal-does-not-allow-wrong-card-to-be-pick-from-open-pile
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round' evs'] (cmds/move round 0 {:type :pick :data {:from :discards :card nil}})]
    (is (nil? round'))
    (is (= (dissoc evs' :data) {:type :error, :to 0}))
    (is (= (get-in evs' [:data :error]) :incorrect-card))
    (is (= 13 (count (get-in round [:players 0 :hand]))))
    (is (= 1 (count (:discards round))))))

(deftest deal-does-not-allow-out-of-turn-play-from-discards
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o (json-card o)
        [round' evs'] (cmds/move round 1 {:type :pick :data {:from :discards :card o}})]
    (is (nil? round'))
    (is (= (dissoc evs' :data) {:type :error :to 1}))
    (is (= (get-in evs' [:data :error]) :out-of-turn))
    (is (= 13 (count (get-in round [:players 0 :hand]))))
    (is (= 1 (count (:discards round))))))

; pick and discard functionality
(deftest deal-does-not-allow-out-of-turn-play-from-stock
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round' evs'] (cmds/move round 1 {:type :pick :data {:from :stock}})]
    (is (nil? round'))
    (is (= evs' {:type :error, :data {:expected 0, :got 1, :error :out-of-turn} :to 1}))
    (is (= 13 (count (get-in round [:players 0 :hand]))))
    (is (= 1 (count (:discards round))))))

(deftest multiple-picks-from-discards-not-possible
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o (json-card o)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :discards :card o}})
        [round2 evs2] (cmds/move round1 0 {:type :pick :data {:from :discards :card o}})]
    (is (nil? round2))
    (is (= evs2 {:type :error, :data {:error :invalid-hand-size-for-pick} :to 0}))
    (is (= 14 (count (get-in round1 [:players 0 :hand]))))
    (is (= 0 (count (:discards round1))))))

(deftest multiple-picks-from-stock-not-possible
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :stock}})
        [round2 evs2] (cmds/move round1 0 {:type :pick :data {:from :stock}})]
    (is (nil? round2))
    (is (= evs2 {:type :error, :data {:error :invalid-hand-size-for-pick} :to 0}))
    (is (= 14 (count (get-in round1 [:players 0 :hand]))))
    (is (= 1 (count (:discards round1))))))

; discard functionality
(deftest discards-are-not-allowed-without-going-for-pick-first
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o' (json-card o)
        [round' evs'] (cmds/move round 0 {:type :discard :data {:card o'}})]
    (is (nil? round'))
    (is (= evs' {:type :error, :data {:error :invalid-hand-size-for-discard} :to 0}))))

(deftest picking-from-discards-and-discarding-keeps-hand-size-at-13-and-changes-turn
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o' (json-card o)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})
        [round2 evs2] (cmds/move round1 0 {:type :discard :data {:card o'}})]
    (is (.contains evs2 {:type :discard :data {:by 0 :card o} :to :all}))
    (is (.contains evs2 {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))
    (is (= 13 (count (get-in round2 [:players 0 :hand]))))
    (is (= 1 (count (:discards round2))))))

(deftest picking-from-stock-and-discarding-keeps-hand-size-at-13-and-changes-turn
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :stock}})
        c (picked-card evs1)
        c' (json-card c)
        [round2 evs2] (cmds/move round1 0 {:type :discard :data {:card c'}})]
    (is (.contains evs2 {:type :discard :data {:by 0 :card c} :to :all}))
    (is (.contains evs2 {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))
    (is (= 13 (count (get-in round2 [:players 0 :hand]))))
    (is (= 2 (count (:discards round2))))))

(deftest cannot-discard-a-not-existing-card
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :stock}})
        c (picked-card evs1)
        o' (json-card o)
        [round2 evs2] (cmds/move round1 0 {:type :discard :data {:card o'}})]
    (is (= (.contains evs2 {:type :error, :data {:error :card-not-found}, :to 0})))
    (is (nil? round2))
    (is (= 14 (count (get-in round1 [:players 0 :hand]))))
    (is (= 1 (count (:discards round1))))))

(deftest discarding-makes-the-switch-to-next-player-continuously
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o' (json-card o)
        [round1 evs1] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})
        [round2 evs2] (cmds/move round1 0 {:type :discard :data {:card o'}})
        [round3 evs3] (cmds/move round2 1 {:type :pick :data {:from :discards :card o'}})
        [round4 evs4] (cmds/move round3 1 {:type :discard :data {:card o'}})
        [round5 evs5] (cmds/move round4 0 {:type :pick :data {:from :discards :card o'}})
        [round6 evs6] (cmds/move round5 0 {:type :discard :data {:card o'}})
        [round7 evs7] (cmds/move round6 1 {:type :pick :data {:from :discards :card o'}})
        [round8 evs8] (cmds/move round7 1 {:type :discard :data {:card o'}})]
    (is (.contains evs8 {:type :discard :data {:by 1 :card o} :to :all}))
    (is (.contains evs8 {:type :next-turn, :data {:player 0}, :timeout 15000, :to :all}))
    (is (= 13 (count (get-in round2 [:players 0 :hand]))))
    (is (= 13 (count (get-in round2 [:players 1 :hand]))))
    (is (= 1 (count (:discards round2))))))

; drop functionality
(deftest drop-move-by-player-notifies-of-drop-and-next-turn-to-al
  (let [pids (mk-player-ids 6)
        [round evs] (deal-6)
        [round' evs'] (cmds/move round 0 {:type :drop})]
    (is (.contains evs' {:type :drop, :data {:player 0}, :to :all}))
    (is (.contains evs' {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))))

(deftest drop-in-the-2nd-full-turn-is-a-middle-dro
  (let [[round evs] (deal-6)
        [open-card & rest] (parse-deal-6-evs evs)
        [round1 _] (pick-n-discard round 0 open-card)
        [round2 _] (pick-n-discard round1 1 open-card)
        [round3 _] (pick-n-discard round2 2 open-card)
        [round4 _] (pick-n-discard round3 3 open-card)
        [round5 _] (pick-n-discard round4 4 open-card)
        [round6 _] (pick-n-discard round5 5 open-card)
        [round7 evs'] (cmds/move round6 0 {:type :drop})]
    (is (.contains evs' {:type :middle-drop :data {:player 0} :to :all}))
    (is (.contains evs' {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))))

(deftest drop-in-the-2nd-full-turn-is-a-middle-dro
  (let [[round evs] (deal-6)
        [open-card & rest] (parse-deal-6-evs evs)
        [round1 _] (cmds/move round 0 {:type :drop})
        [round2 _] (cmds/move round1 1 {:type :drop})
        [round3 _] (cmds/move round2 2 {:type :drop})
        [round4 _] (pick-n-discard round3 3 open-card)
        [round5 _] (pick-n-discard round4 4 open-card)
        [round6 evs'] (pick-n-discard round5 5 open-card)]
    (is (.contains evs' {:type :next-turn, :data {:player 3}, :timeout 15000, :to :all}))))

(deftest in-a-2-table-first-player-drop-makes-other-the-winner
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        [round' evs'] (cmds/move round 0 {:type :drop})
        scoreboard {0 {:status :drop :points 20}
                    1 {:status :winner :points 0}}]
    (is (.contains evs' {:type :drop, :data {:player 0}, :to :all}))
    (is (.contains evs' {:type :winner :data {:player 1}, :to :all}))
    (is (.contains evs' {:type :stop :data {:scoreboard scoreboard}, :to :all}))))

(deftest in-a-6-table-first-5-players-drop-makes-other-the-winner
  (let [[round evs] (deal-6)
        [o j h1 h2 h3 h4 h5 h6 curr-player] (parse-deal-6-evs evs)
        [round1 _] (cmds/move round 0 {:type :drop})
        [round2 _] (cmds/move round1 1 {:type :drop})
        [round3 _] (cmds/move round2 2 {:type :drop})
        [round4 _] (cmds/move round3 3 {:type :drop})
        [round5 evs'] (cmds/move round4 4 {:type :drop})
        scoreboard {0 {:status :drop :points 20}
                    1 {:status :drop :points 20}
                    2 {:status :drop :points 20}
                    3 {:status :drop :points 20}
                    4 {:status :drop :points 20}
                    5 {:status :winner :points 0}}]
    (is (.contains evs' {:type :drop, :data {:player 4}, :to :all}))
    (is (.contains evs' {:type :winner :data {:player 5}, :to :all}))
    (is (.contains evs' {:type :stop :data {:scoreboard scoreboard}, :to :all}))))

(deftest wrong-show-with-2-players-end-the-round
  (let [[round evs] (deal-2)
        [o j h1 h2 curr-player] (parse-deal-2-evs evs)
        o' (json-card o)
        [round' _] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})
        [_ evs'] (cmds/move round' 0 {:type :show
                                      :data {:cardGroups [(map json-card h1)] :discard o'}})
        scoreboard {0 {:card-groups [h1] :status :wrong-show :points 80}
                    1 {:status :winner :points 0}}]
    (is (.contains evs' {:type :error :data {:error :wrong-show :by 0}, :to :all}))
    (is (.contains evs' {:type :discard, :data {:card o, :by 0}, :to :all}))
    (is (.contains evs' {:type :winner :data {:player 1}, :to :all}))
    (is (.contains evs' {:type :stop :data {:scoreboard scoreboard}, :to :all}))))

(deftest wrong-show-changes-turn-to-the-next-playe
  (let [[round evs] (deal-6)
        [o j h1 h2 h3 h4 h5 h6 curr-player] (parse-deal-6-evs evs)
        o' (json-card o)
        [round' _] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})
        [_ evs'] (cmds/move round' 0 {:type :show
                                      :data {:cardGroups [(map json-card h1)] :discard o'}})]
    (is (.contains evs' {:type :error :data {:error :wrong-show :by 0} , :to :all}))
    (is (.contains evs' {:type :discard, :data {:card o, :by 0}, :to :all}))
    (is (.contains evs' {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))))

(deftest wrong-show-avoids-player-in-next-player-turn
  (let [[round evs] (deal-6)
        [o j h1 h2 h3 h4 h5 h6 curr-player] (parse-deal-6-evs evs)
        o' (json-card o)
        [round1 _] (cmds/move round 0 {:type :pick :data {:from :discards :card o'}})
        [round2 _] (cmds/move round1 0 {:type :show :data {:cardGroups [(map json-card h1)] :discard o'}})
        [round3 evs] (pick-n-discard round2 1 o)
        [round4 _] (pick-n-discard round3 2 o)
        [round5 _] (pick-n-discard round4 3 o)
        [round6 _] (pick-n-discard round5 4 o)
        [_ evs'] (pick-n-discard round6 5 o)]
    (is (.contains evs' {:type :next-turn, :data {:player 1}, :timeout 15000, :to :all}))))

