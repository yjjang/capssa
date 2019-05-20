(ns viz-stra.server
  (:require [viz-stra.handler :refer [handler]]
            [viz-stra.logger :refer [wrap-with-logger]]
            [config.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "8080"))]
    (println "Starting CaPSSA server on port" port "...")
    (run-jetty
      (wrap-with-logger handler)
      {:port port :join? false :request-header-size 256000000})))
