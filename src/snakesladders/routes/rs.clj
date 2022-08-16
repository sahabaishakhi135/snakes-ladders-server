(ns snakesladders.routes.rs
  (:require
    [compojure.core :refer [defroutes context GET POST]]
    [com.pekaplay.regex :refer [uuid]]
    [engine.comms :as comms]
    [snakesladders.handlers.rs :as rs]))

(defroutes urls
  (context (format "/rooms/:game-uuid{%s}" uuid) []
    (context "/chsk" []
      (GET  "/" [] comms/ring-ajax-get-or-ws)
      (POST "/" [] comms/ring-ajax-post))
    (GET  "/" [] rs/handle-cmd)
    (POST "/" [] rs/handle-cmd)))