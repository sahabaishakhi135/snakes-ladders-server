(ns snakesladders.repl
  (:require
   [engine.nrepl :as nrepl]))

(defonce repl-server (atom nil))

(defn start-repl [port]
  (when (nil? @repl-server)
    (reset! repl-server (nrepl/start {:bind "0.0.0.0" :port port}))))

(defn stop-repl []
  (when @repl-server
    (nrepl/stop @repl-server)))