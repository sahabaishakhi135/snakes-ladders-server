(ns snakesladders.test.config
  (:require
    [snakesladders.game.config :as cfg]))

(defn- setup-test
  []
  (cfg/fetch-snakesladders-tables))

(defn- teardown-test
  []
  (reset! cfg/configs nil))

(defn wrap-setup
  [f]
  (setup-test)
  (f)
  (teardown-test))

