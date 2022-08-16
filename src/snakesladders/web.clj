(ns snakesladders.web
  (:require
   [environ.core :refer [env]]
   [com.pekaplay.math :refer [->int]]
   [com.pekaplay.timer :as t]
   [engine.push :refer [start-pusher]]
   [engine.sites :refer [load-sites]]
   [engine.comms :as comms]
   [engine.manager :refer [worker-chan]]
   [snakesladders.worker :refer [start-worker]]
   [snakesladders.server :refer [start-server stop-server]]
   [snakesladders.repl :refer [start-repl stop-repl]]
   [snakesladders.game.config :refer [fetch-snakesladders-tables]]
   [snakesladders.game.server :refer [spawn-commander abort-games]])
  (:gen-class))

(def ^:const DEFAULT-PORT 8090)

(defn- check-env-vars []
  (when-not (some? (:kafka-url env))
    (println "⚠️  Missing KAFKA_URL EnvVar")))

(defn -main [& args]
  (if-let [port (->int (:port env))]
    (do
      (check-env-vars)
      (load-sites (:sites-edn env))
      (fetch-snakesladders-tables)
      (spawn-commander)
      (start-pusher)
      (start-worker worker-chan)
      (t/start-timer)
      (comms/start-router!)
      (start-server port)
      (start-repl (or (->int (:nrepl-port env)) 7575))

      (.addShutdownHook
        (Runtime/getRuntime)
        (Thread. (fn []
                   (do
                     (println "Shutting down...")
                     (t/stop-timer)
                     (abort-games)
                     (comms/stop-router!)
                     (stop-server)
                     (stop-repl)
                     (shutdown-agents))))))
    (println "💥 [snakesladders-server] NOPORT. Exiting...")))
