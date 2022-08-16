(ns snakesladders.game-types.pool-test
  (:require
    [clojure.test :refer :all]
    [engine.util :refer [wae->]]
    [snakesladders.test.config :refer [wrap-setup]]
    [snakesladders.game :as g]
    [snakesladders.game.core :as gc]
    [snakesladders.game-types :as gt]
    [snakesladders.round.cmds :as rcmd]
    [snakesladders.round.core :as rc]))

(use-fixtures :once wrap-setup)

(def ^:const TABLE-ID  "35") ; 101pool-2p-20
(def ^:const TABLE-ID2 "25") ; 101pool-6p-20

(defn- mk-profile
  [id buyin]
  {:id         (str "id" id)
   :alias      (str "alias" id)
   :user-token (str "token" id)
   :score      0
   :buyin      buyin})

(defn seat-from-player-id [game id]
  (ffirst (filter (fn [[s p]] (= id (:id p))) (:players game))))

(deftest simple-score-n-update-2p
  (let [game (g/mk-game :ludo TABLE-ID)
        [game1 _] (wae-> game
                     (g/alloc-seat-in-game (mk-profile 1 20.0) "ch1")
                     (g/alloc-seat-in-game (mk-profile 2 16.0) "ch2")
                     (gc/pick-seats)
                     (gc/start-round))
        [round [ev]] (-> (get-in game1 [:rounds 0])
                         (update-in [:players 0] merge {:points 0, :status :show})
                         (update-in [:players 1] merge {:points 20, :status :drop})
                         (assoc :winner 0)
                         (rcmd/finish-round))
        [game2 _] (gt/reconcile-score game1 round)
        scoreboard (gt/enrich-round-scoreboard game2 :finished (get-in ev [:data :scoreboard]))]

    (is (= :pool
           (get-in game2 [:config :game-type])
           (get-in game2 [:rounds 0 :config :game-type])))

    (testing "game player's buyin has changed"
      (is (= 0 (get-in game2 [:players 0 :score])))
      (is (= 20 (get-in game2 [:players 1 :score]))))

    (testing "game scoreboard has score in amount"
      (is (= 0 (get-in scoreboard [0 :score])))
      (is (= 20 (get-in scoreboard [1 :score]))))))

(deftest score-n-update-busted-2p
  (let [game (g/mk-game :ludo TABLE-ID)
        [game1 _] (wae-> game
                         (g/alloc-seat-in-game (mk-profile 1 20.0) "ch1")
                         (g/alloc-seat-in-game (mk-profile 2 16.0) "ch2")
                         (gc/pick-seats)
                         (gc/start-round))
        game2     (-> game1
                      (assoc-in [:players 0 :score] 60)
                      (assoc-in [:players 1 :score] 85))
        [round [ev]] (-> (get-in game2 [:rounds 0])
                         (update-in [:players 0] merge {:points 0, :status :show})
                         (update-in [:players 1] merge {:points 20, :status :drop})
                         (assoc :winner 0)
                         (rcmd/finish-round))
        [game3 _] (gt/reconcile-score game2 round)
        scoreboard (gt/enrich-round-scoreboard game3 :finished (get-in ev [:data :scoreboard]))]

    (is (= :pool
           (get-in game3 [:config :game-type])
           (get-in game3 [:rounds 0 :config :game-type])))

    (testing "game player's buyin has changed"
      (is (= 60 (get-in game3 [:players 0 :score])))
      (is (= 105 (get-in game3 [:players 1 :score]))))

    (testing "game scoreboard has score in amount"
      (is (= 60 (get-in scoreboard [0 :score])))
      (is (= 105 (get-in scoreboard [1 :score])))
      (is (= :winner (get-in scoreboard [0 :result])))
      (is (= :busted (get-in scoreboard [1 :result]))))))

(deftest score-n-busted-3p
  (let [game (g/mk-game :ludo TABLE-ID2)
        [game1 _] (wae-> game
                         (g/alloc-seat-in-game (mk-profile 1 20.0) "ch1")
                         (g/alloc-seat-in-game (mk-profile 2 20.0) "ch2")
                         (g/alloc-seat-in-game (mk-profile 3 20.0) "ch3")
                         (g/start-game)
                         (gc/pick-seats)
                         (gc/start-round))
        [s0 s1 s2] (map #(seat-from-player-id game1 %) ["id1" "id2" "id3"])
        game2     (-> game1
                      (update-in [:players s0] merge {:score 60 :status :ready})
                      (update-in [:players s1] merge {:score 85 :status :ready})
                      (update-in [:players s2] merge {:score 40 :status :ready}))
        round (-> (get-in game2 [:rounds 0])
                  (update-in [:players s2] merge {:points 20, :status :drop})
                  (update-in [:players s0] merge {:points 0, :status :show})
                  (assoc :winner 0))
        round-scoreboard (rc/scoreboard (:players round))
        scoreboard (gt/enrich-round-scoreboard game2 :running round-scoreboard)
        [round1 [ev]] (-> round
                          (update-in [:players s1] merge {:points 25, :status :meld})
                          (rcmd/finish-round))
        [game3 _] (gt/reconcile-score game2 round1)
        scoreboard1 (gt/enrich-round-scoreboard game3 :finished (get-in ev [:data :scoreboard]))]

    (testing "game player's buyin has changed"
      (is (= 60  (get-in game3 [:players s0 :score])))
      (is (= 110 (get-in game3 [:players s1 :score])))
      (is (= 60  (get-in game3 [:players s2 :score]))))

    (testing "when only 2 players have melded"
      (is (= 60 (get-in scoreboard [s0 :score])))
      (is (= :winner (get-in scoreboard [s0 :result])))

      (is (= 85 (get-in scoreboard [s1 :score])))
      (is (= :melding (get-in scoreboard [s1 :result])))

      (is (= 60 (get-in scoreboard [s2 :score])))
      (is (= :drop (get-in scoreboard [s2 :result]))))

    (testing "when all players have melded"
      (is (= 60 (get-in scoreboard1 [s0 :score])))
      (is (= :winner (get-in scoreboard1 [s0 :result])))

      (is (= 110 (get-in scoreboard1 [s1 :score])))
      (is (= :busted (get-in scoreboard1 [s1 :result])))

      (is (= 60 (get-in scoreboard1 [s2 :score])))
      (is (= :drop (get-in scoreboard1 [s2 :result]))))))
