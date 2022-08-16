(ns snakesladders.event-sourcing.utils
  (:require [jackdaw.serdes.edn :as jse]))

(def serdes
  {:key-serde (jse/serde)
   :value-serde (jse/serde)})

(defn topic-config
  "Takes a topic name and (optionally) a key and value serde and
  returns a topic configuration map, which may be used to create a
  topic or produce/consume records."
  ([topic-name]
   (topic-config topic-name serdes))
  ([topic-name serdes]
   (merge {:topic-name topic-name
           :partition-count 1
           :replication-factor 1}
          serdes)))