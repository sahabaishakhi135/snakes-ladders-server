(ns snakesladders.board.snakesladders.util
  (:require
    [clojure.tools.logging :as log]
    [clojure.string :as str]
    [com.pekaplay.math :refer [->int]]
    [snakesladders.board.snakesladders.board :refer [mk-cell mk-bead]]))

(defn parseId
  "Returns the color and integer id as a vector.
  beadId e.g Red3
  cellId e.g y18"
  [s]
  (let [[[_ c id]] (re-seq #"(\D+)(\d+)" s)]
    [c id]))

(defn ->bead
  "BeadId are of the form <Color><Id> e.g Blue2"
  [beadId]
  (let [[Color id] (parseId beadId)]
    (mk-bead (keyword (str/lower-case Color)) (->int id))))

(defn ->cell
  "CellId are of the form <c><id> e.g y18"
  [cellId]
  (let [[c id] (parseId cellId)]
    (mk-cell
      (get {"r" :red
            "b" :blue
            "y" :yellow
            "g" :green}
            c)
      (->int id))))

(defn ->beadId [{:keys [color id]}]
  (str (str/capitalize (name color)) id))

(defn ->cellId [{:keys [color id]}]
  (str (first (name color)) id))