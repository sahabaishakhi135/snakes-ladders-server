(ns snakesladders.game-test
  (:require
    [clojure.test :refer :all]
    [snakesladders.test.config :refer [wrap-setup]]
    [snakesladders.game :as g]))

(use-fixtures :once wrap-setup)

(def ^:const TABLE-ID "3")

(defn- mk-profile [id]
  {:id         (str "id" id)
   :alias      (str "alias" id)
   :user-token (str "token" id)})

(deftest empty-game
  (let [game (g/mk-game :snakesladders TABLE-ID)]
    (is (some? (-> game :config :points)))
    (is (= :ready (:status game)))))

(deftest waiting-status-on-min-join
  (let [game (g/mk-game :snakesladders TABLE-ID)
        [game1 _] (g/alloc-seat-in-game game  (mk-profile 1) "ch1")
        [game2 _] (g/alloc-seat-in-game game1 (mk-profile 2) "ch2")
        [game3 _] (g/alloc-seat-in-game game2 (mk-profile 3) "ch3")
        [game4 _] (g/alloc-seat-in-game game3 (mk-profile 4) "ch4")
        [game5 _] (g/alloc-seat-in-game game4 (mk-profile 5) "ch5")
        [game6 _] (g/alloc-seat-in-game game5 (mk-profile 6) "ch6")]
    (println (map :status [game game1 game2 game3 game4 game5 game6]))
    (testing "game is idle till min players join"
      (is (apply = :ready (map :status [game game1 game2]))))

    (testing "game is in waiting status till it gets ready"
      (is (apply = :waiting (map :status [game3 game4 game5]))))

    (testing "players status is not ready till game is not ready"
      (doseq [r [game1 game2 game3 game4 game5]]
        (is (apply not= :ready (map :status (vals (:players r)))))))

    (testing "game & players are ready when all players join"
      (is (apply = :ready (map :status (vals (:players game6)))))
      ; waiting -> :ready -> :running
      (is (= :pick-seats (:status game6))))))
