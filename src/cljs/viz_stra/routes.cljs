(ns viz-stra.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History)
  (:require [secretary.core :as secretary]
            [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [re-frame.core :as re-frame]
            [viz-stra.events :as events]))

(defn hook-browser-navigation! []
  (doto (History.)
    (gevents/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  ;; --------------------
  ;; define routes here
  (defroute "/" []
    (re-frame/dispatch [::events/set-active-panel :home-panel]))
  (defroute "/upload" []
    (re-frame/dispatch [::events/set-active-panel :upload-panel]))
  (defroute "/mutation" []
    (re-frame/dispatch [::events/set-active-panel :mutation-panel]))
  (defroute "/expression" []
    (re-frame/dispatch [::events/set-active-panel :expression-panel]))
  (defroute "/help" []
    (re-frame/dispatch [::events/set-active-panel :help-panel]))
  ;; --------------------
  (hook-browser-navigation!))
