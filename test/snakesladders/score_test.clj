(ns snakesladders.score-test
  (:require
    [clojure.test :refer :all]
    [snakesladders.round.score :as s]
    [com.pekaplay.card.card :as c]))

(defn ->card [s] (c/parse s))

(defn ->cards [s] (map c/parse (clojure.string/split s #" ")))

(deftest suits-extraction
  (is (= [:spades :hearts :diamonds :clubs]
         (s/suits (->cards "as 2h 3d 4c")))))

(deftest ranks-extraction
  (is (= [:ace, :jack, :king, :queen]
         (s/ranks (->cards "ac jh kd qs")))))

(deftest not-same-suits
  (is (s/same-suit? []))
  (is (s/same-suit? (->cards "ac jc 3c tc 6c"))))

(deftest not-same-suits
  (is (not (s/same-suit? (->cards "ac jc 3d tc 6c"))))
  (is (not (s/same-suit? (->cards "ac jr 3d 5c tc")))))

(deftest same-ranks
  (is (s/same-rank? (->cards "ac ad as ah")))
  (is (s/same-rank? (->cards "ac ad ac ad")))
  (is (s/same-rank? (->cards "4c 4d 4s 4d")))
  (is (s/same-rank? (->cards "qc qd qs qd")))
  (is (s/same-rank? (->cards "tc td ts td")))
  (is (s/same-rank? (->cards "tc tc tc tc")))
  (is (s/same-rank? (->cards "jr jr jr jr"))))

(deftest not-same-ranks
  (is (not (s/same-rank? (->cards "tc qd ts td"))))
  (is (not (s/same-rank? (->cards "ac jr as ad")))))

(deftest min-3-needed
  (is (not (s/pure? (->cards "4s 3s"))))
  (is (not (s/impure? (->cards "4s 3s"))))
  (is (not (s/group? (->cards "4s 3s")))))

(deftest real-sequence
  (is (s/pure? (->cards "4s 3s 2s as")))
  (is (s/pure? (->cards "4s 6s 7s 5s")))
  (is (s/pure? (->cards "ks ts qs js")))
  (is (s/pure? (->cards "ks as qs js")))
  (is (s/pure? (->cards "ks qs js")))
  (is (s/pure? (map #(c/mk-card % :diamonds) c/RANKS))))

(deftest not-pure
  (is (not (s/pure? (->cards "2s 3s 4s 6s"))))
  (is (not (s/pure? (->cards "2s 3s 4s 2s")))))

(deftest ace-low-will-not-affect-pure-sequences-without-ace
  (is (s/pure? (->cards "4s 6s 7s 5s") false))
  (is (s/pure? (->cards "ks ts qs js") false))
  (is (s/pure? (->cards "ks qs js") true))

  (is (not (s/pure? (->cards "2s 3s 4s 6s") false)))
  (is (not (s/pure? (->cards "2s 3s 4s 2s") true))))

(deftest ace-low-is-always-counted-irrespective-of-ace-high?-flag
  (is (s/pure? (map #(c/mk-card % :clubs) c/RANKS) true))
  (is (s/pure? (map #(c/mk-card % :spades) c/RANKS) false))

  (is (s/pure? (->cards "4s 3s 2s as") false))
  (is (s/pure? (->cards "4s 3s 2s as") true)))

(deftest j-q-k-a-sequence-fails-with-ace-low
  (is (not (s/pure? (->cards "ks as qs js") false))))

(deftest group-are-cards-with-same-rank-w-all-different-suits
  (is (s/group? (->cards "as ac ad ah")))
  (is (not (s/group? (->cards "as ac ad ac")))))

(deftest groups-without-enough-cards-can-be-filled-by-jokers
  (is (s/group? (->cards "as 2d ah") (->card "ac"))))

(deftest all-pure-sequences-are-sequences-too
  (is (s/impure? (->cards "4s 3s 2s as")))
  (is (s/impure? (->cards "4s 6s 7s 5s")))
  (is (s/impure? (->cards "ks ts qs js")))
  (is (s/impure? (->cards "ks as qs js")))
  (is (s/impure? (->cards "ks qs js")))
  (is (s/impure? (map #(c/mk-card % :diamonds) c/RANKS))))

(deftest non-sequence
  (is (not (s/impure? (->cards "2s 3s 4s 6s"))))
  (is (not (s/impure? (->cards "2s 3s 4s 2s")))))

(deftest sequences-with-gaps-are-filled-by-jokers
  (is (s/impure? (->cards "4s 3s as") (->card "ah")))
  (is (not (s/impure? (->cards "4s 3s as") (->card "6s"))))

  (testing "With face jokers"
    (is (s/impure? (->cards "as jr 3s jr 4s")))
    (is (s/impure? (->cards "as 2s jr 5s 4s"))))

  (is (s/impure? (->cards "ts qs ks as") (->card "ad")))
  (is (s/impure? (->cards "qs ks as") (->card "jc")))

  (is (not (s/impure? (->cards "as 2s jr 5s")))))

(deftest sequences-with-jokers-with-no-ace-high-cannot-use-ace-even-with-jokers
  (is (not (s/impure? (->cards "qs ks as") (->card "qd") false)))
  (is (not (s/impure? (->cards "2s ks as") (->card "qc") false))))

(deftest all-jokers-is-still-a-sequence
  (is (s/impure? (->cards "2s 2d 2c") (->card "2h"))))

(deftest single-cards-with-rest-jokers-are-both-sequence-and-group
  (is (s/impure? (->cards "4s 4s 3c 4d") (->card "4d")))
  (is (s/impure? (->cards "4s 4s 3c 4d") (->card "4d") false))
  (is (s/group? (->cards "4s 4s 3c 4d") (->card "4d"))))

(deftest show-1-pure-1-seq-2-groups-is-a-valid-show
  (let [card-groups [(->cards "4s 3s 2s")
                     (->cards "ad qd jr jd")
                     (->cards "5s 5d 5c")
                     (->cards "9s 9c 9h")]
        joker (->card "9d")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0))))

(deftest show-1-pure-no-seq-2-groups-gets-scores-for-all-except-pure-seq
  (is (= (s/score [(->cards "4s 3s 2s")
                   (->cards "ad qd 8d jd")
                   (->cards "5s 5d 5c")
                   (->cards "9s 9c 9h")] (->card "7d")) (+ 38 15 27))))

(deftest all-pure-sequences-is-a-valid-show-w-score-0
  (let [card-groups [(->cards "4s 3s 2s")
                     (->cards "ad qd kd jd")
                     (->cards "5c 7c 6c")
                     (->cards "9h 7h 8h")]
        joker (->card "jc")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0)))

  (let [card-groups [(->cards "4d 3d 2d 6d 5d")
                     (->cards "ad qd td kd jd")
                     (->cards "5c 7c 6c")]
        joker (->card "8c")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0)))

  (let [card-groups [(->cards "4s 3s 2s 6s 5s")
                     (->cards "ad qd kd jd td 7d 9d 8d")]
        joker (->card "jc")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0)))

  (let [card-groups [(->cards "4d 3d 2d 6d 5d ad qd kd jd td 7d 9d 8d")]
        joker (->card "jc")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0))))

(deftest show-1-pure-all-jokers-2-triplets-is-a-valid-show-w-score-0
  (let [card-groups [(->cards "4s 3s 2s as")
                     (->cards "jr jd jc")
                     (->cards "5s 5d 5c")
                     (->cards "9h 9c 9d")]
        joker (->card "jc")]
    (is (s/valid-show? card-groups joker))
    (is (= (s/score card-groups joker) 0))))

(deftest scoring-with-no-pure-only-seq-gives-full-count
  (is (= (s/score [(->cards "4s 3s 2s 6s")
                   (->cards "jr kd jc")
                   (->cards "5s 6d 7c")
                   (->cards "9h 9c 9d")] (->card "jc"))
         (reduce + [4 3 2 6 10 5 6 7 9 9 9]))))

(deftest scoring-with-pure-&-trips-gives-all-trips-none-as-count
  (is (= (s/score [(->cards "4s 3s 2s as")
                   (->cards "9c 8d jc")
                   (->cards "5s 5d 5c")
                   (->cards "9h 9c 9d")] (->card "jc"))
         (reduce + [9 8 (* 5 3) (* 9 3)]))))

(deftest live-bug
  (let [card-groups [(->cards "3s 3d 6d")
                     (->cards "7s 6d 8s")
                     (->cards "2s 6c 4s")
                     (->cards "js qs ks as")]
        joker (->card "6c")]
    (is (= (s/score card-groups joker) 0))))

(deftest live-bug2
  (let [card-groups [(->cards "4s 5s")
                     (->cards "6s 5s 4s 3s 2s as")
                     (->cards "6d 5d 4d 3d 2d")]
        joker (->card "ah")]
    (is (> (s/score card-groups joker) 0))))
