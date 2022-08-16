(ns snakesladders.board.snakesladders.board
  (:require
   [clojure.tools.logging :as log]))

(def ^:const COLORS [:red :green :yellow :blue])

(defn mk-bead
  [color id]
  {:pre [(>= 3 id 0)]}
  {:type :bead
   :color color
   :id id})

(defn mk-cell
  [id]
  {:type :cell
   :id id})

(def cn-path
  (range 101))

(def ^:const PATHS
  (map #(mk-cell %) cn-path))

(def SNAKES
  [{:from {:type :cell :id 99} :to {:type :cell :id 4}}
   {:from {:type :cell :id 30} :to {:type :cell :id 11}}
   {:from {:type :cell :id 46} :to {:type :cell :id 25}}
   {:from {:type :cell :id 52} :to {:type :cell :id 29}}
   {:from {:type :cell :id 70} :to {:type :cell :id 51}}
   {:from {:type :cell :id 94} :to {:type :cell :id 75}}])

(def LADDERS
  [{:from {:type :cell :id 3} :to {:type :cell :id 84}}
   {:from {:type :cell :id 7} :to {:type :cell :id 53}}
   {:from {:type :cell :id 15} :to {:type :cell :id 96}}
   {:from {:type :cell :id 21} :to {:type :cell :id 98}}
   {:from {:type :cell :id 54} :to {:type :cell :id 93}}])

(defn- mk-beads [colors n]
  (into {}
        (for [c colors, i (range 0 n)]
          [(mk-bead c i) (mk-cell 0)])))

(defn mk-board
  [colors n]
  {:beads (mk-beads (map #(keyword %) colors) n)
   :current-color (first colors)
   :turns 1})