(ns snakesladders.game-types.points-test
  (:require
    [clojure.test :refer :all]
    [clojure.tools.logging :as log]
    [engine.util :refer [wae->]]
    [snakesladders.test.config :refer [wrap-setup]]
    [snakesladders.game :as g]
    [snakesladders.game.core :as gc]
    [snakesladders.game-types :as gt]
    [snakesladders.round.cmds :as rcmd]
    [snakesladders.game.cmds :as gcmd]))

(use-fixtures :once wrap-setup)

(def ^:const TABLE-ID  "57")  ; pts-2p-0.1
(def ^:const TABLE-ID2  "69") ; pts-6p-0.5

(defn seat-from-player-id [game id]
  (ffirst (filter (fn [[s p]] (= id (:id p))) (:players game))))

(defn- mk-profile
  [id buyin]
  {:id         (str "id" id)
   :alias      (str "alias" id)
   :user-token (str "token" id)
   :buyin       buyin})

(deftest points-snakesladders-round-end
  (let [game (g/mk-game :snakesladders TABLE-ID)
        [game1 _] (wae-> game
                         (g/alloc-seat-in-game (mk-profile 1 20.0) "ch1")
                         (g/alloc-seat-in-game (mk-profile 2 16.0) "ch2")
                         (g/start-pick-seats)
                         (gc/pick-seats)
                         (gc/start-round))
        p0     (get-in game1 [:players 0])
        p1     (get-in game1 [:players 1])
        [round [ev]] (-> (get-in game1 [:rounds 0])
                         (update-in [:players 0] merge {:points 0, :status :show})
                         (update-in [:players 1] merge {:points 80, :status :meld})
                         (assoc :winner 0)
                         (rcmd/finish-round))
        [game2 _] (gt/reconcile-score game1 round)
        scoreboard (gt/enrich-round-scoreboard game2 :finished (get-in ev [:data :scoreboard]))]

    (is (= :points
           (get-in game2 [:config :game-type])
           (get-in game2 [:rounds 0 :config :game-type])))

    (testing "game player's buyin has changed"
      (is (= (+ (:buyin p0) 8.0) (get-in game2 [:players 0 :buyin])))
      (is (= (- (:buyin p1) 8.0) (get-in game2 [:players 1 :buyin]))))

    (testing "game scoreboard has score in amount"
      (is (= 8.0 (get-in scoreboard [0 :score])))
      (is (= -8.0 (get-in scoreboard [1 :score]))))))

(deftest points-snakesladders-show-scoreboard
  (let [game (g/mk-game :snakesladders TABLE-ID2)
        [game1 _] (wae-> game
                         (g/alloc-seat-in-game (mk-profile 1 80.0) "ch1")
                         (g/alloc-seat-in-game (mk-profile 2 60.0) "ch2")
                         (g/alloc-seat-in-game (mk-profile 3 46.0) "ch3")
                         (g/start-pick-seats)
                         (gc/pick-seats)
                         (gc/start-round))
        [s0 s1 s2] (map #(seat-from-player-id game1 %) ["id1" "id2" "id3"])
        [p0 p1 p2] (map #(get-in game1 [:players %]) [s0 s1 s2])
        [round [ev]] (-> (get-in game1 [:rounds 0])
                         (update-in [:players s0] merge {:points 0, :status :show})
                         (assoc :winner s0)
                         (rcmd/show-scoreboard))
        scoreboard (gt/enrich-round-scoreboard game1 :running (get-in ev [:data :scoreboard]))]

    (testing "game scoreboard has score in amount"
      (is (= 0.0 (get-in scoreboard [s0 :score])))
      (is (nil? (get-in scoreboard [s1 :score])))
      (is (nil? (get-in scoreboard [s2 :score]))))))

(deftest points-snakesladders-round-end-leave
  (let [game (g/mk-game :snakesladders TABLE-ID2)
        [game1 _] (wae-> game
                         (g/alloc-seat-in-game (mk-profile 1 80.0) "ch1")
                         (g/alloc-seat-in-game (mk-profile 2 60.0) "ch2")
                         (g/alloc-seat-in-game (mk-profile 3 46.0) "ch3")
                         (g/start-pick-seats)
                         (gc/pick-seats)
                         (gc/start-round))
        [s0 s1 s2] (map #(seat-from-player-id game1 %) ["id1" "id2" "id3"])
        [p0 p1 p2] (map #(get-in game1 [:players %]) [s0 s1 s2])
        [round [ev]] (-> (get-in game1 [:rounds 0])
                         (update-in [:players s0] merge {:points 0, :status :show})
                         (update-in [:players s1] merge {:points 80, :status :meld})
                         (update-in [:players s2] merge {:points 20, :status :drop})
                         (assoc :winner s0)
                         (rcmd/finish-round))
        [game2 evs] (wae-> game1
                           (gcmd/leave-table s2)
                           (gt/reconcile-score round))
        scoreboard (gt/enrich-round-scoreboard game2 :finished (get-in ev [:data :scoreboard]))]

    (is (= :points
           (get-in game2 [:config :game-type])
           (get-in game2 [:rounds 0 :config :game-type])))

    (testing "busted because of leave table"
      (is (.contains evs {:type :leave-table-result, :data {:player-id s2}, :to :all}))
      (is (.contains evs {:type :busted, :data {:player s2}, :to :all})))

    (testing "left table"
      (is (.contains evs {:type :busted, :data {:player s1}, :to :all})))

    (testing "game player's buyin has changed"
      (is (= (+ (:buyin p0) (* 80 0.5) (* 20 0.5)) (get-in game2 [:players s0 :buyin])))
      (is (nil? (get-in game2 [:players s1])))  ; busted
      (is (nil? (get-in game2 [:players s2])))) ; left table

    (testing "game scoreboard has score in amount"
      (is (= (* (+ 80 20) 0.5) (get-in scoreboard [s0 :score])))
      (is (= (* -1 80 0.5) (get-in scoreboard [s1 :score])))
      (is (= (* -1 20 0.5) (get-in scoreboard [s2 :score]))))))
