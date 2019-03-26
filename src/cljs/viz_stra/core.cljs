(ns viz-stra.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            ;; -- Foreign libs --------------
            [cljsjs.d3]
            [cljsjs.jquery]
            [highcharts]
            [highcharts.more]
            [highcharts.modules.exporting]
            [highcharts.modules.sankey]
            [kinetic]
            [inchlib]
            [jquery.qtip]
            ;; ------------------------------
            [viz-stra.config :as config]
            [viz-stra.events :as events]
            [viz-stra.views :as views]
            [viz-stra.exp.events :as exp-events]
            [viz-stra.mut.events :as mut-events]
            [viz-stra.routes :as routes]))


(defn dev-setup []
  (if config/debug?
    (do
      (enable-console-print!)
      (println "dev mode"))
    ;ignore println statements if not debug
    (set! *print-fn* (fn [& _]))))


(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))


;; -- Initialize application ------------------------------------------

(defn ^:export init []
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::exp-events/initialize-db])
  (re-frame/dispatch-sync [::mut-events/initialize-db])
  (dev-setup)
  (mount-root))

