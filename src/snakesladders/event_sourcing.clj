(ns snakesladders.event-sourcing
  (:require
   [clojure.tools.logging :as log]
   [environ.core :refer [env]]
   [jackdaw.admin :as ja]
   [jackdaw.client :as jc]
   [jackdaw.serdes.edn :as je]
   [snakesladders.event-sourcing.utils :refer [topic-config]]
   [snakesladders.event-sourcing.core])
  (:import
   (snakesladders.event_sourcing.core Event Cmd)))

(def ^:const GAME-TYPE :snakesladders)

(def producer-config
  {;"application.id"    "game-server.snakesladders"
   "bootstrap.servers" (:kafka-url env)})

(def serdes
  {:key-serde (je/serde)
   :value-serde (je/serde)})

(def events-topic (topic-config "game-server.all.events")) ; game-server.snakesladders.events

(defn create-topic!
  [topic-name]
  (ja/create-topics! producer-config [(topic-config topic-name)]))

(defn publish-event!
  [^Event event]
  (log/debug "Publishing event" event (:kafka-url env))
  (when (some? (:kafka-url env))
    (let [key {:room-uuid (:game-uuid event), :game-type GAME-TYPE}
          value (dissoc event :game-uuid)]
      (with-open [producer (jc/producer producer-config serdes)]
        @(jc/produce! producer events-topic key value)))))

(defn publish-cmd!
  "Every event is written to 2 topics - the specific room and to the
  rooms global topic. Ideally, we want the events written to a single
  topic and have KStreams branch it out to other branches"
  [^Cmd cmd]
  (log/debug "Publishing cmd" cmd (:kafka-url env))
  (when (some? (:kafka-url env))
    (let [key {:room-uuid (:game-uuid cmd), :game-type GAME-TYPE}
          value (dissoc cmd :game-uuid)]
      (with-open [producer (jc/producer producer-config serdes)]
        @(jc/produce! producer events-topic key value)))))

(defn produce-one [k v]
  (with-open [producer (jc/producer producer-config serdes)]
    @(jc/produce! producer events-topic k v)))

(comment
  (create-topic! "game-server.ludo.events")

  (produce-one {:room-uuid "room1" :game-type :ludo}
               {:to nil, :event :room-created, :data {:table-id 11, :num-players 4, :buyin 5.0}})

  (produce-one {:room-uuid "room1" :game-type :ludo}
               {:from 1, :cmd :join-room, :data {:token "tok1", :wager-id "wgr3"}})

  (produce-one {:room-uuid "room1" :game-type :ludo}
               {:to [1], :event :join-room-result, :data {:seat-id 1, :alias "john", :player-id 345}})

  (produce-one {:room-uuid "room1" :game-type :ludo}
               {:to [1, 3], :event :join-room-result, :data {:seat-id 3, :alias "may", :player-id 873}}))
