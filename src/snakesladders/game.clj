(ns snakesladders.game
  (:require
   [clojure.tools.logging :as log]
   [medley.core :refer [remove-vals map-vals dissoc-in]]
   [com.pekaplay.uuid :refer [uuid-str]]
   [com.pekaplay.events :refer [notify2] :as events]
   [better-cond.core :as better]
   [engine.timeouts :as t]
   [engine.util :refer [wae->]]
   [engine.player :refer [mk-player update-player]]
   [engine.player.protocols :refer [profile]]

   [snakesladders.stages :refer [start-stage]]
   [snakesladders.game.events :as gevents]
   [snakesladders.game-types :as gt]
   [snakesladders.round.events :refer [error pot-update]]
   [snakesladders.game.config :as cfg]))

(defn join-room-result
  [game-id colors seat plyr-profile  num-vacant-seats]
  (-> (events/join-room-result game-id seat plyr-profile)
      (assoc-in [:data :color] (nth (map #(keyword %) colors) seat))
      (assoc-in [:data :vacant-seats] num-vacant-seats)))

(defn join-room
  [game-id colors seat plyr-profile]
  (-> (events/join-room game-id seat plyr-profile)
      (assoc-in [:data :color] (nth (map #(keyword %) colors) seat))))

; Game
(defrecord TurnBasedGame
  [uuid game-engine config status winner])

(defn mk-tbg
  [_game-type _table-type config]
  (TurnBasedGame. (uuid-str) :snakesladders config :ready nil))

(defrecord SnakesladdersGame [table-type num-seats players pot deck rounds dealer-index current-round])

(defn mk-snakesladders-game
  [_game-type table-type config]
  (log/info config)
  (let [{:keys [num-seats]} config]
    (map->SnakesladdersGame {:table-type table-type
                             :num-seats  num-seats
                             :players    (zipmap (range 0 num-seats)
                                                 (repeat nil))
                             :pot        0
                             :rounds     []})))

(defn mk-game
  [game-type table-type]
  (if-let [config (cfg/get-config table-type)]
    (merge
      (mk-tbg game-type table-type config)
      (mk-snakesladders-game game-type table-type config))
    (log/error "Config for" game-type table-type "missing")))

(defn init-game-player
  [player]
  (assoc player :score 0, :status :ready))

(defn available? [game]
  (contains? #{:ready :waiting} (:status game)))

(defn vacant? [player] (or (nil? player) (= :left (:status player))))

(defn taken-seats [game]
  (seq (remove (fn [[_k v]] (vacant? v)) (:players game))))

(defn num-vacant-seats [game]
  (count (filter (fn [[_k v]] (vacant? v)) (:players game))))

(defn same-player?
  [pa1 pa2]
  (let [p1 (profile pa1)
        p2 (profile pa2)]
    (and (= (:id p1) (:id p2))
         (= (:site-id p1) (:site-id p2)))))

(defn seated? [profile [_s p]] (if (vacant? p) false (same-player? profile p)))

(defn pick-seat
  ([game]
   (when-let [empty-seats (not-empty (filter (fn [[_s p]] (vacant? p)) (:players game)))]
     (first (rand-nth empty-seats))))
  ([game profile]
   (when-let [seat (not-empty (filter (partial seated? profile) (:players game)))]
     (ffirst seat))))

;; ; guards
;; (defn min-players-joined? [game]
;;   (let [num-joined (count (remove-vals nil? (:players game)))]
;;     (and (> num-joined 2)
;;          (= num-joined
;;             (/ (count (vals (:players game))) 2)))))

(defn all-players-joined? [game]
  (every? (complement vacant?) (vals (:players game))))

(defn prune-empty-seats [game]
  (let [players (remove-vals vacant? (:players game))]
    (assoc game :players players)))

(defn start-pick-seats
  [game]
  [(-> game
       (prune-empty-seats)
       (t/cancel-timeout! nil :waiting)
       ;(assoc :pinger (mgr/start-task :ping (:uuid game)))
       (update-in [:players] #(map-vals init-game-player %)))
   (notify2 (events/next-stage :pick-seats) :all)])

(defn start-game
  [game]
  (if (contains? #{:ready :waiting} (:status game))
    (wae-> game
         (start-stage :pick-seats)
         (start-pick-seats))
    [game nil]))

(defn change-status-maybe
  [game]
  (log/info "change-status-maybe" (:status game))
  (case (:status game)
    :ready
    (if (all-players-joined? game)
      (start-game game)
      [game nil])

    [game nil]))

(defn on-player-joined-event
  ([game-id colors seat player seated num-vacant-seats]
   (on-player-joined-event game-id colors seat player seated num-vacant-seats true))
  ([game-id colors seat player seated num-vacant-seats inform-others?]
   (if inform-others?
     (cons
       (notify2 (join-room-result game-id colors seat (profile player) num-vacant-seats) seat)
       (conj
         (map (fn [[s p]] (notify2 (join-room game-id colors s (profile p)) seat)) seated)
         (notify2 (events/join-room game-id seat (profile player)) (keys seated))))
     (cons
       (notify2 (join-room-result game-id colors seat (profile player) num-vacant-seats) seat)
       (map (fn [[s p]] (notify2 (join-room game-id colors s (profile p)) seat)) seated)))))

(defn update-pot
  [game buyin to]
  (let [game' (if (some? buyin) (update-in game [:pot] (fnil + 0) buyin) game)]
    [game' (notify2 (pot-update (:pot game')) to)]))

(defn take-seat
  [game seat seated profile ch-id]
  (let [player (mk-player ch-id profile)
        uuid   (:uuid game)
        game'  (assoc-in game [:players seat] player)
        num-vacant-seats (num-vacant-seats game')
        {:keys [colors]} (:config game)]
    [game'
     (cons (notify2 (gevents/game-config game') seat)
           (on-player-joined-event uuid colors seat player seated num-vacant-seats))]))

(defn retake-seat
  [game seat _seated profile ch-id]
  (let [seated (remove #{seat} (taken-seats game))
        uuid   (:uuid game)
        game'  (update-in game [:players seat] update-player profile ch-id)
        num-vacant-seats (num-vacant-seats game')
        player (get-in game' [:players seat])]
    [game'
     (cons
       (notify2 (gevents/game-config game') seat)
       (on-player-joined-event uuid seat player seated num-vacant-seats false))]))

(defn alloc-seat
  "Return [seat [game evs]] tuple instead of [game evs]"
  [game plyr-adp ch-id]
  (better/cond
   :let [uuid (:uuid game)]

   (nil? uuid)
   [nil [nil (error :game-unavailable [:chan ch-id])]]

   :let [seat (pick-seat game plyr-adp)]

   (some? seat)
   [seat (wae-> game
                (retake-seat seat (remove #{seat} (taken-seats game)) plyr-adp ch-id)
                (update-pot nil [:chan ch-id]))]

   :let [seat (pick-seat game)]

   (nil? seat)
   (let [num-seats (count (keys (:players game)))]
     (log/warn "Seat Unavailable. Cashing out" plyr-adp (:players game))
     [nil [nil [(error :seat-unavailable [:chan ch-id])
                (gevents/leave-game-result nil plyr-adp num-seats 0)]]])

   (not (contains? #{:ready :waiting} (:status game)))
   [nil [nil (error :game-started [:chan ch-id])]]

   :let [[ok? buyin bal] (gt/take-seat game plyr-adp)]

   (or (nil? ok?) (false? ok?))
   [nil [nil (error :buyin-failed [:chan ch-id])]]

   :else
   [seat (wae-> game
                (take-seat seat (taken-seats game) plyr-adp ch-id)
                (update-pot buyin :all)
                (change-status-maybe))]))

(defn dealloc-seat
  [game seat-id]
  (log/debug "dealloc-seat" (:players game) seat-id)
  (better/cond
   :let [uuid (:uuid game)]

   (nil? uuid)
   [nil (error :game-unavailable seat-id)]

   :let [player (get-in game [:players seat-id])]

   (nil? player)
   [nil (error :player-not-seated seat-id)]

   :let [game' (assoc-in game [:players seat-id :status] :left)
         num-seats (count (remove nil? (vals (:players game'))))
         avl-seats (num-vacant-seats game')]

   :else
   [game'
    [(notify2 (gevents/leave-table-result seat-id) [:chan (:channel player)])
     (notify2 (gevents/leave-table seat-id) [:except seat-id])
     (gevents/leave-game-result seat-id player num-seats avl-seats)]]))