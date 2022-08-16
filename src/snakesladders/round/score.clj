(ns snakesladders.round.score
  (:require
    [com.pekaplay.card.card :refer [pip-value rank-index]]))

(def ^:const MIN-LEN 3)
(def ^:const MAX-LEN 13)

(def ^:const ACE-LOW 1)
(def ^:const ACE-HIGH 14)

(def ^:const HAND-SIZE 13)

(defn suits [cards] (map :suit cards))
(defn ranks [cards] (map :rank cards))

(defn same-suit? [cards] (or (= 0 (count cards)) (= 1 (-> cards suits set count))))
(defn same-rank? [cards] (or (= 0 (count cards)) (= 1 (-> cards ranks set count))))

(defn uniq-ranks? [cards] (= (count cards) (-> cards ranks set count)))
(defn uniq-suits? [cards] (= (count cards) (-> cards suits set count)))

(defn sort-by-ranks [cards] (sort-by rank-index cards))

(defn non-jokers [cards joker]
  (remove #((set [:joker (:rank joker)]) (:rank %)) cards))

(defn pure?
  ([cards ace-high?]
    (let [[a & rest :as cards] (sort-by-ranks cards)]
      (if (and (same-suit? cards) (uniq-ranks? cards))
        (if (and ace-high?
                 (= (:rank a) :ace)
                 (= (-> cards last :rank) :king))
          (= (- (pip-value :king) (-> rest first rank-index))
             (dec (count rest)))
          (= (- (-> cards last rank-index) (-> cards first rank-index))
             (dec (count cards)))))))
  ([cards]
    (if (>= (count cards) MIN-LEN)
      (pure? cards true))))

(defn impure?
  ([cards joker ace-high?]
    (let [[a & rest :as others] (sort-by-ranks (non-jokers cards joker))]
      (cond
        (<= (count others) 1)
          true
        (and (same-suit? others) (uniq-ranks? others))
          (if (and ace-high? (= :ace (:rank a)))
            (or
              (< (- (-> rest last rank-index) ACE-LOW) (count cards))
              (< (- ACE-HIGH (-> rest first rank-index)) (count cards)))
            (< (- (-> rest last rank-index) (rank-index a)) (count cards))))))
  ([cards joker]
   (if (>= (count cards) MIN-LEN)
      (impure? cards joker true)))
  ([cards]
    (if (>= (count cards) MIN-LEN)
      (impure? cards nil))))

(defn group?
  ([cards]
    (if (>= (count cards) MIN-LEN)
      (and (same-rank? cards) (uniq-suits? cards))))
  ([cards joker]
    (if (>= (count cards) MIN-LEN)
      (if-let [cards (seq (non-jokers cards joker))]
        (and (same-rank? cards) (uniq-suits? cards))
        true))))

(defn valid-hand? [card-groups] (= HAND-SIZE (-> card-groups flatten count)))

(defn categorizer [card-group joker acc]
  (cond
    (< (count card-group) MIN-LEN)
      (update acc :none conj card-group)
    (pure? card-group)
      (update acc :pure conj card-group)
    (impure? card-group joker)
      (update acc :impure conj card-group)
    (group? card-group joker)
      (update acc :group conj card-group)
    :else
      (update acc :none conj card-group)))

(defn categorize [card-groups joker]
  (let [acc {:pure [], :impure [], :group [], :none []}]
    (reduce #(categorizer %2 joker %1) acc card-groups)))

(defn valid-show? [card-groups joker]
  (let [{:keys [pure impure none]} (categorize card-groups joker)]
    (cond
      (= (count none) 0)
        true
      (and (>= (count pure) 1) (> (+ (count pure) (count impure)) 1))
        true)))

(defn value [card joker]
  (let [c-rank (:rank card)
        j-rank (:rank joker)]
    (cond
      (= :joker c-rank)
        0
      (= c-rank j-rank)
        0
      (.contains (range 2 11) c-rank)
        c-rank
      (.contains [:ace :jack :king :queen] c-rank)
        10
      :else
        false)))

(defn points [cards joker]
  (reduce + (map #(value % joker) cards)))

(defn score [card-groups joker]
  (let [joker' (if (= :joker (:rank joker)) {:rank :ace :suit :spades} joker)
        {:keys [pure impure group none]} (categorize card-groups joker')
        groups (cond
                  (and (>= (count pure) 1) (>= (count impure) 1))
                    none

                  (>= (count pure) 2)
                    none

                  (= (count pure) 1)
                    (concat group none)

                  (= (count pure) 0)
                    (concat impure group none))]
    (reduce + (map #(points % joker') groups))))