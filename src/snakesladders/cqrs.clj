(ns snakesladders.cqrs
  (:require
   [clojure.tools.logging :as log]
   [environ.core :refer [env]]
   [jackdaw.admin :as ja]
   [jackdaw.client :as jc]
   [jackdaw.serdes.edn :as jse]))

(def producer-config
  {;"application.id" "snakesladdersgs"
   "bootstrap.servers" (:kafka-url env)
   ;"cache.max.bytes.buffering" "0"
   })

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
           :replication-factor 2
           :topic-config {}}
          serdes)))

(defn room-topic [uuid]
  ;(topic-config (str "rooms-" uuid))
  (topic-config "snakesladders-rooms-events"))

(def lobby-topic
  (topic-config "rooms"))

(def LOBBY-EVENTS #{:room-created
                    :stop-game
                    :join-room-result
                    :leave-game-result
                    :settle-game})

(defn lobby-event?
  "Is this a room level event?"
  [event]
  (some? (LOBBY-EVENTS event)))

(defn- -publish-event!
  "Every event is written to 2 topics - the specific room and to the
  rooms global topic. Ideally, we want the events written to a single
  topic and have KStreams branch it out to other branches"
  [event-type room-uuid data to]
  (let [event {:game-type :snakesladders
               :uuid room-uuid
               :to to
               :event event-type
               :data data}]
    (with-open [producer (jc/producer producer-config serdes)]
      (let [fut (jc/produce! producer (room-topic room-uuid) event)]
        (log/debug "-publish-event!1" @fut))
      (when (lobby-event? event-type)
        (let [fut (jc/produce! producer lobby-topic event)]
          (log/debug "-publish-event!2" @fut))))))

; https://danlebrero.com/2017/06/21/atoms-delays-and-side-effects-resource-managent-in-clojure/
(defn create-new-producer []
  (jc/producer producer-config serdes))

(def global-producer (atom (delay (create-new-producer))))

(defn refresh-producer!
  [broken-producer]
  (if (compare-and-set! global-producer
                        broken-producer
                        (delay (create-new-producer)))
    (log/info "Closing connection" @broken-producer)))

(defn run-with-producer [f]
  (let [producer @global-producer]
    (try
      (f @producer)
      (catch Exception _
        (refresh-producer! producer)
        (log/info "Retrying")
        (run-with-producer f)))))

(defn- -publish-cmd!
  "Every event is written to 2 topics - the specific room and to the
  rooms global topic. Ideally, we want the events written to a single
  topic and have KStreams branch it out to other branches"
  [event-type room-uuid data from]
  (let [event {:game-type :snakesladders
               :uuid room-uuid
               :event event-type
               :from  from
               :data data}]
    (when (lobby-event? event-type)
      (run-with-producer #(jc/produce! % lobby-topic event))
      (if (= event-type :join-room-result)
        (ja/create-topics! producer-config
                           [{:topic-name (room-topic room-uuid)
                             :partition-count 15
                             :replication-factor 2
                             :topic-config {"cleanup.policy" "compact"}}])))
    (run-with-producer #(jc/produce! % (room-topic room-uuid) event))))

(defn publish-event!
  [event room-uuid data to]
  (log/debug "Publishing event" room-uuid event data to (:kafka-url env))
  (if (and (some? (:kafka-url env)) (some? room-uuid))
    (-publish-event! event room-uuid data to)))

(defn publish-cmd!
  [event room-uuid data from]
  (log/debug "Publishing cmd" room-uuid event data from (:kafka-url env))
  (if (some? (:kafka-url env))
    (-publish-cmd! event room-uuid data from)))
