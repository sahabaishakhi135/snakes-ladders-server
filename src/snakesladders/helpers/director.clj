(ns snakesladders.helpers.director
  (:require
    [clojure.tools.logging :as log]
    [medley.core :refer [filter-vals]]
    [environ.core :refer [env]]
    [cheshire.core :as json]
    [clj-http.client :as http]
    [snakesladders.event-sourcing :as es]
    [snakesladders.event-sourcing.core :refer [->Event ->Cmd]]))

(defn postback!
  [ev uuid data]
  (future
    (let [res (http/post (str (:director-svc env) "/api/postback")
               {:headers {"Content-Type" "application/json"}
                :body (json/encode {:event ev
                                    :gameType "snakesladdders"
                                    :uuid  uuid
                                    :data  data})
                :throw-exceptions false})]
      (log/info res))))

(defn publish
  "Register the new session / game with the director"
  [game]
  (let [{:keys [uuid players config]} game
        {:keys [id num-seats]}        config
        data {:session-id uuid
              :host-id    (:host-id env)
              :state      (:status game)
              :num-seats  num-seats
              :avl-seats  (count (filter-vals nil? players))
              :table-id   id}]
    (when (some? (:director-svc env))
      (postback! :room-created uuid data))

    (if (some? (:kafka-url env))
      (es/publish-event! (->Event :room-created data uuid nil nil))
      (log/warn "⚠️  STANDALONE server. Ignoring new room pub"))))

(defn- ->full-result
  [seat result player]
  (log/info "->full-result" seat result player)
  (when-let [win (or (:win result) (:win-amount player))]
    (let [plyr-adp   (:player-adapter player)

          {:keys [api-key auth-token site-id]} (:profile-adapter plyr-adp)

          wlt-adp    (when-let [wlt-adp-ref (:wallet-adapter-ref plyr-adp)]
                       (deref wlt-adp-ref))
          {:keys [wager-id ticket-id]} wlt-adp]
      {:seat-id      seat
       :ticket-id    ticket-id
       :wager-id     wager-id
       :api-key      api-key
       :access-token auth-token
       :amount       win
       :site-id      site-id})))

(defn settle-game
  [game result]
  (if (some? (:kafka-url env))
    (let [game-uuid   (:uuid game)
          game-result (->> result
                           (map
                            (fn [[s d]]
                              (->full-result s d (get-in game [:players s]))))
                           (filter some?))]
      (es/publish-event! (->Event :settle-game game-result game-uuid nil nil)))
    (log/warn "❌ STANDALONE server. Settle Win not possible")))

(defn credit-win
  "Credit win for the given game, player and amount"
  [game-uuid seat-id access-token ticket-id amount]
  (if (some? (:kafka-url env))
    (let [data {:sessionId   game-uuid
                :seatId      seat-id
                :ticketId    ticket-id
                :accessToken access-token
                :amount      amount
                :hostId      (:host-id env)}]
      (es/publish-event! (->Event :settle-win data game-uuid nil nil)))
    (log/warn "❌ STANDALONE server. Credit Win not possible")))

(defn stop-game
  [uuid]
  (log/info "Stopping game" uuid)
  (when-not (some? (:kafka-url env))
    (es/publish-event! (->Event :stop-game nil uuid nil nil))))

(defn abort-game
  [uuid]
  (log/info "Aborting game" uuid)
  (when-not (some? (:kafka-url env))
    (es/publish-event! (->Event :abort-game nil uuid nil nil))))

(defn bot-player?
  [p]
  (= :jwt ((comp :wallet-adapter-type :player-adapter) p)))

(defn prune-bot-only-games
  [games-atom]
  (doseq [[guuid ga] (deref games-atom)]
    (let [g (deref ga)
          game-status (:status g)
          humans (->> (:players g)
                      (vals)
                      (remove #(nil? (:alias %)))
                      (remove bot-player?))]
      (if-not (empty? humans)
        (println (:status g) guuid "has humans" (map :alias humans))
        (println (:status g) guuid "bots only")))))
