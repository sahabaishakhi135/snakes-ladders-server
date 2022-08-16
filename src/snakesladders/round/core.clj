(ns snakesladders.round.core
  (:require
   [environ.core :refer [env]]
   [com.pekaplay.math :refer [->int]]
   [clojure.tools.logging :as log]
   [medley.core :refer [remove-vals]]
  ;;  [com.pekaplay.card.hand :as h]
   [snakesladders.board.snakesladders.board :as lblb]
   [snakesladders.round.score :as score]))

(defrecord RoundPlayer [status hand points seat player])

(defn mk-round-player [player seat]
  (RoundPlayer. (or (:status player) :ready) nil nil seat player))

(defn init
  "Returns the `round` map with players and deck initialized"
  [round cards players turn-order]
  (log/debug players turn-order)
  (assoc round
        ;;  :deck cards
         :players (into {}
                        (map (fn [[seat p]]
                               [seat (mk-round-player p seat)])
                             players))
         :turn-order turn-order
         :status :ready))

;; (defn- cut-deck [deck]
;;   (let [n (rand-int (count deck))
;;         [d1 d2] (split-at n deck)]
;;     (concat d2 d1)))

;; (defn dist-cards
;;   "Returns a tuple of hands, stockpile, open card and the joker card.

;;   Shuffles the deck (shuffle, cut into two, place the bottom on top) and
;;   distributes 13 cards among each of the players, picks the open and the
;;   joker cards and creates the final stockpile of cards.

;;   # Steps

;;   1. Cut the card deck into two and move the top pile under the bottom pile
;;       and start dealing hands

;;   2. One hand is created per player respectively from the shuffled cards
;;       list. For a group of say 6 players, the 1st, 7th, 13th, 20th, ... are
;;       given to 1st player, the 2nd, 8th, 14th, ... are given to 2nd player
;;       and so on

;;   3. Pick an open card and make the remaining cards the stockpile

;;   4. Finally pick the joker card from the remaining stockpile"
;;   [deck turn-order]
;;   (let [np (count turn-order)
;;         deck' (cut-deck deck)
;;         [hands-cards [open & cards]] (split-at (* score/HAND-SIZE np) deck')
;;         hands (map #(take-nth np (drop % hands-cards)) (range np))
;;         [joker stockpile] (h/pick-one cards)]
;;     [hands stockpile open joker]))

(def ^:const NUM-FULL-ROLLS 4)

(defn shuffled-die-rolls
  [n]
  (shuffle (apply concat (repeat n (range 1 7)))))

(defn distribute-rolls*
  [{:keys [turn-order] :as round}]
  (-> (reduce
       (fn [rnd [seat rolls]]
         (update-in rnd [:players seat] merge {:status :running, :rolls rolls, :used [], :score 0}))
       round
       (zipmap turn-order (repeatedly #(shuffled-die-rolls (or (->int (:num-full-rolls env)) NUM-FULL-ROLLS)))))

      (assoc :status :running)))

(defn distribute-cards*
  [{:keys [turn-order] :as round}]
  (-> (reduce
       (fn [rnd [seat _rolls]]
         (update-in rnd [:players seat] merge {:status :running}))
       round
       (zipmap turn-order (repeat nil)))
      (assoc :status :running)
      (dissoc :deck)))

;; (defn distribute-cards
;;   [{:keys [deck players turn-order] :as round}]
;;   (let [[hands stockpile open joker] (dist-cards deck turn-order)]
;;     (-> (reduce
;;          (fn [rnd [seat]]
;;            (update-in rnd [:players seat] merge {:status :running}))
;;          round
;;          (zipmap turn-order hands))
;;         (assoc :stockpile stockpile
;;                :discards (list [open nil])
;;                :open-card open
;;                :joker-card joker
;;                :status :running)
;;         (dissoc :deck))))

(defn first-turn?
  "Find what player's turn is this"
  [{:keys [turn-order turn-count]}]
  (= 0 (quot turn-count (count turn-order))))

(defn- turn-count-player
  [turn-count {:keys [turn-order players]}]
  (let [i (mod turn-count (count turn-order))
        turn (nth turn-order i)]
    (get players turn)))

(defn player-id
  [player]
  (if (map? player) (:seat player) player))

(defn- current-player
  [{:keys [turn-count] :as round}]
  (turn-count-player turn-count round))

(defn current-player-id
  [round]
  (player-id (current-player round)))

(defn change-turn
  [{:keys [turn-count] :as round}]
  (let [turn-count' (inc turn-count)
        player (turn-count-player turn-count' round)]
    (assoc round :turn-count turn-count'
           :current-player (player-id player))))

(defn in-play? [player]
  (contains? #{:running :afk} (:status player)))

(defn dropped? [player]
  (contains? #{:drop :middle-drop} (:status player)))

(defn skip-invalid-turns
  [round]
  (let [player (current-player round)]
    (if-not (in-play? player)
      (-> round (change-turn) (skip-invalid-turns))
      round)))

(defn scoreboard [players]
  (->> players
       (remove-vals #(= :bust (:status %)))
       (map (fn [[id p]]
              [id (select-keys p [:status :score])]))
       (into {})))

;; (defn last-man-standing-id?
;;   [{:keys [players]}]
;;   (let [active (filter (comp in-play? second) players)]
;;     (if (= (count active) 1)
;;       (ffirst active))))

;; (defn melded?
;;   [p]
;;   (contains? #{:show :wrong-show :meld :drop :middle-drop :bust} (:status p)))

(defn reset-turn
  [{:keys [current-player] :as round}]
  (let [color (nth lblb/COLORS current-player)]
    (assoc round :current-color color, :die-roll nil, :turns 1)))

(defn start-turn
  [round]
  (-> round
      (change-turn)
      (skip-invalid-turns)
      (reset-turn)))

(comment
  (let [rolls (shuffled-die-rolls 1)
        used-rolls []

        [r1 u1] (use-selected-number rolls used-rolls 1)
        [r2 u2] (use-selected-number r1 u1 2)
        [r3 u3] (use-selected-number r2 u2 3)
        [r4 u4] (use-selected-number r3 u3 4)
        [r5 u5] (use-selected-number r4 u4 5)
        [r6 u6] (use-selected-number r5 u5 6)]
    (prn r1 u1)
    (prn r2 u2)
    (prn r3 u3)
    (prn r4 u4)
    (prn r5 u5)
    (prn r6 u6)))
