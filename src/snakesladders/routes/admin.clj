(ns snakesladders.routes.admin
  (:require
   [compojure.core :refer [defroutes context GET POST]]
   [snakesladders.handlers.admin :as admin]))

(defroutes urls
  (context "/admin/tables" []
    (GET "/" [] admin/get-tables))
  (context "/admin/games" []
    (GET "/" [] admin/get-games)
    (POST "/" [] admin/mk-game)
    (GET "/:id" [] admin/get-game-details)))
