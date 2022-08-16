(ns snakesladders.spec
  (:require [clojure.spec.alpha :as s]))

(def uuids? (s/and string? #(re-matches #"[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}" %)))

(s/def ::type #{"snakesladders"})
(s/def ::alias string?)
(s/def ::token string?)
(s/def ::tableType string?)
(s/def ::skinId (s/or :dummy zero? :skin pos-int?))
(s/def ::wagerId uuids?)
(s/def ::roomId uuids?)
(s/def ::ticketId uuids?)

(s/def ::join-cmd (s/keys :req-un [::type
                                   ::tableType
                                   ::token
                                   ::roomId
                                   ::skinId]
                          :opt-un [::alias ::wagerId ::ticketId]))