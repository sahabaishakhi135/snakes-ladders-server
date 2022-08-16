(ns snakesladders.game.core
  (:require
    [clojure.tools.logging :as log]
    [clojure.set :refer [rename-keys]]
    [com.pekaplay.card.card :refer [pip-value]]
    [com.pekaplay.card.deck :refer [mk-deck]]
    [com.pekaplay.events :refer [notify2]]
    [engine.util :refer [wae->]]
    [engine.player.protocols :refer [profile]]

    [engine.timeouts :as t]
    [snakesladders.game.events :as ge]
    [snakesladders.helpers.director :as dir]
    [snakesladders.stages :refer [start-stage]]

    [snakesladders.board.snakesladders.cmds :as lblc]
    [snakesladders.round.events :as events]
    [snakesladders.round :as round]
    [snakesladders.round.cmds :as rcmd]))

(defn current-round [game]
  (let [current-round (:current-round game)]
    (get-in game [:rounds current-round])))

(defn +current-round [game round]
  (let [current-round (:current-round game)]
    (assoc-in game [:rounds current-round] round)))

(defn game-deck
  [n]
  (cond
    (= n 2) (mk-deck 1 1)
    (<= 3 n 6) (mk-deck 2 2)
    (<= 7 n 10) (mk-deck 3 3)))

(def card-rank
  (juxt #(if (= :joker (:rank %)) 15 (pip-value (:rank %) true))
        #(get {:spades 3, :hearts 2, :diamonds 1, :clubs 0, nil -1} (:suit %))))

(defn sort-by-cards
  [players cards]
  (reverse (sort-by (comp card-rank second) (map list players cards))))

(defn pick-seats
  "Returns the game and the events when seats are picked"
  [game]
  (let [players       (:players game)
        np            (count players)
        deck          (game-deck np)
        cards         (take np (shuffle (mk-deck 1 0)))
        ordered-seats (sort-by-cards players cards)
        turn-order    (map ffirst ordered-seats)
        evs           (events/turn-order-with-cards ordered-seats)]
      [(assoc game :deck deck
                   :dealer-index -1
                   :turn-order turn-order)
       (map #(notify2 % :all) evs)]))

(defn gen-deck
  [game]
  (nth (iterate shuffle (:deck game)) 8))

(defn gen-turn-order
  [game]
  (comment (let [{:keys [dealer-index turn-order]} game
                 [a b] (split-at dealer-index turn-order)]
             (remove #(= :bust (get-in game [:players % :status])) (concat b a))))
  (let [{:keys [num-seats]} (:config game)]
    (into [] (range num-seats))))

(defmulti mk-round
  (fn [game]
    (get-in game [:config :game-type])))

(defn start-beads-at-cell-1
  [round]
  (reduce
   (fn [round [bead cell]]
     (assoc-in round [:beads bead] (lblc/nth-cell cell 1)))
   round
   (:beads round)))

(defmethod mk-round :snakesladders
  [game]
  (let [{:keys [players uuid config]} game
        deck       (gen-deck game)
        turn-order (gen-turn-order game)
        round      (round/mk-round uuid config)
        round'     (start-beads-at-cell-1 round)]
    (wae-> round
           (rcmd/start deck players turn-order)
           (rcmd/distribute-rolls))))

(defn next-dealer
  [game]
  (if (contains? game :new-turn-order)
    (rename-keys game {:new-turn-order :turn-order})
    (let [{:keys [dealer-index players turn-order]} game
          np (count turn-order)
          dealer-index' (loop [i (mod (inc dealer-index) np)]
                         (if (= :bust (:status (get players (nth turn-order i))))
                           (recur (mod (inc i) np))
                           i))]
      (assoc game :dealer-index dealer-index'))))

(defn start-round
  [game]
  (let [game'  (next-dealer game)
        ev (t/timeout nil :end-round {} (:game-time (:config game)))
        [round evs] (mk-round game')
        {rounds :rounds} game'
        game' (+current-round (assoc game' :current-round (count rounds))
                              round)]
    [game' (conj evs ev)]))

(defn distribute-pot
  [game winners]
  (if (some? (:prizes-amount (:config game)))
    (let [{:keys [players pot]} game
          rake-pct (get-in game [:config :rake-pct])

        ;; In case of more than 1 winners, pot is split evenly between the winners
          prizes (:prizes-amount (:config game))
          rake' (* 0.01 rake-pct pot)
          remaining-pot (- pot rake')

          scores (map #(:score %) (vals players))
          sorted-scores (sort > scores)

          best-scores (take 3 sorted-scores)

          scores-dec-idxed (map-indexed list (distinct best-scores))

          score-map (into {} (map #(apply hash-map %) scores-dec-idxed))

          prize-div (map #(count (filter #{%} sorted-scores)) (distinct best-scores))

          pd (into {} (map #(apply hash-map %) (map-indexed list prize-div)))

          game' (cond
                  (= 1 (count pd))
                  (reduce
                   (fn [g s]
                     (if (contains? winners s)
                       (let [sc (get-in g [:players s :score])
                             prz-idx (first (keep #(when (= (val %) sc)
                                                     (key %)) score-map))
                             div (get pd prz-idx)

                             amt (/ (* 0.01 (reduce + prizes) remaining-pot) div)]
                         (assoc-in g [:players s :win-amount] amt))
                       g))
                   (assoc game :winner winners)
                   (keys players))

                  (= 2 (count pd))
                  (reduce
                   (fn [g s]
                     (if (contains? winners s)
                       (let [sc (get-in g [:players s :score])
                             prz-idx (first (keep #(when (= (val %) sc)
                                                     (key %)) score-map))
                             div (get pd prz-idx)

                             amt (if (= prz-idx 0)
                                   (/ (* 0.01 (nth prizes prz-idx) remaining-pot) div)
                                   (/ (* 0.01 (reduce + (rest prizes)) remaining-pot) div))]
                         (assoc-in g [:players s :win-amount] amt))
                       g))
                   (assoc game :winner winners)
                   (keys players))

                  :else
                  (reduce
                   (fn [g s]
                     (if (contains? winners s)
                       (let [sc (get-in g [:players s :score])
                             prz-idx (first (keep #(when (= (val %) sc)
                                                     (key %)) score-map))
                             div (get pd prz-idx)

                             amt (/ (* 0.01 (nth prizes prz-idx) remaining-pot) div)]
                         (assoc-in g [:players s :win-amount] amt))
                       g))
                   (assoc game :winner winners)
                   (keys players)))]
      [game' (map #(do {:type :contrib-rake
                        :data {:amount rake'
                               :winner %
                               :userId (-> game'
                                           (get-in [:players %])
                                           (profile)
                                           :id)}})
                  winners)])
      (let [{:keys [players pot]} game
            rake-pct (get-in game [:config :rake-pct])
            pot' (/ pot (count winners))
            rake (* 0.01 rake-pct pot)
            rake' (* 0.01 rake-pct pot')
            game' (reduce
                   (fn [g s]
                     (if (contains? winners s)
                       (let [win (- pot' rake')]
                         (assoc-in g [:players s :win-amount] win))
                       g))
                   (assoc game :winner winners)
                   (keys players))]
        [game' (map #(do {:type :contrib-rake
                        :data {:amount rake
                               :winner %
                               :userId (-> game'
                                           (get-in [:players %])
                                           (profile)
                                           :id)}})
                  winners)])))

(defn calculate-result
  [game winners]
  (->> (:players game)
       (map (fn [[s p]] [s {:result (if (contains? winners s) :winner :lost)
                            :score  (:score p)
                            :win    (or (:win-amount p) 0)
                            :seat-id s}]))
       (into {})))

(defn settle-game
  [game result]
  (dir/settle-game game result)
  (start-stage game :stopping-game))

(defn finalize-result
  [game winners]
  (let [result  (calculate-result game winners)
        scores (map #(:score %) (vals result))
        sorted-scores (sort > scores)
        scores-dec-idxed (map-indexed list (distinct sorted-scores))
        score-map (into {} (map #(apply hash-map %) scores-dec-idxed))
        result+ranks (reduce (fn [r s]
                         (let [sc (get-in r [s :score])
                                 prz-idx (inc (first (keep #(when (= (val %) sc)
                                                         (key %)) score-map)))]
                             (assoc-in r [s :rank] prz-idx)))
                       result
                       (keys result))
        game'   (assoc game :status :finished)
        ev      (notify2 (ge/stop-room result+ranks) :all)]
    (wae-> [game' [ev]]
           (settle-game result+ranks))))

(defn finish-game
  [game winners]
  (wae-> game
         (distribute-pot winners)
         (finalize-result winners)))


(defn num-drops
  [drop-value bust-score score]
  (int (/ (- bust-score score 1)
          drop-value)))

(defn has-drop-chance?
  [drop-value bust-score score]
  (> (num-drops drop-value bust-score score) 0))

(defn player-num-drops
  [game player]
  (let [{{drop-score :drop} :points, bust-score :num-rounds} (:config game)
        score      (:score player)]
    (num-drops drop-score bust-score score)))

(defn dealer-for-seat
  "Returns the seat of the dealer"
  [turn-order players seat]
  (let [np (count turn-order)]
    (loop [i (mod (dec (.indexOf turn-order seat)) np)]
      (if (= :bust (:status (get players (nth turn-order i))))
        (recur (mod (dec i) np))
        (nth turn-order i)))))

(defn calc-turn-order-info
  [turn-order players small-blind accepts]
  (let [turn-order-wo-accepts (remove (set accepts) turn-order)
        idx        (.indexOf (vec turn-order-wo-accepts) small-blind)
        [a b]      (split-at idx turn-order-wo-accepts)
        new-turn-order (concat a accepts b)
        dealer (dealer-for-seat (vec new-turn-order) players small-blind)
        dealer-index (.indexOf (vec new-turn-order) dealer)]
    [new-turn-order dealer-index]))
