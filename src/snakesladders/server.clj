(ns snakesladders.server
  (:require
    [clojure.tools.logging :as log]
    [clojure.stacktrace :refer [print-stack-trace]]
    [environ.core :refer [env]]
    [medley.core :refer [dissoc-in]]
    [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [com.pekaplay.middleware :refer [wrap-exception wrap-allow-cors]]
    [org.httpkit.server :as httpkit]
    [snakesladders.routes :refer [all-routes]]
    [ring.logger :as logger]))

(defn mk-site-defaults []
  (if (= (:allow-cors-sessions env) "true")
    (-> site-defaults
        (dissoc-in [:session :cookie-attrs :same-site])
        (assoc-in [:security :anti-forgery] false))
    site-defaults))

(def app
  (let [site-defaults (mk-site-defaults)]
    (-> all-routes
        (wrap-defaults site-defaults)
        (wrap-keyword-params)
        (wrap-params)
        (wrap-allow-cors)
        (wrap-exception))))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 1000)
    (reset! server nil)))

(defn mk-server [port]
  (httpkit/run-server (logger/wrap-with-logger #'app) {:port port}))

(defn start-server [port]
  (println "🚀 [snakesladders-server] Starting on port" port)
  (when (nil? @server)
    (reset! server (mk-server port))))