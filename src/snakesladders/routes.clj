(ns snakesladders.routes
  (:require
   [compojure.route :as route]
   [compojure.core :refer [defroutes routes]]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [snakesladders.routes.rs :as rs]
   [snakesladders.routes.admin :as admin]))

(defroutes urls
  (route/files "public")
  (route/not-found {:message "invalid action"}))

(def all-routes
  (routes
   rs/urls
   (-> admin/urls
       (wrap-json-body {:keywords? true})
       (wrap-json-response))
   urls))