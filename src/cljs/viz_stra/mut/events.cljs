(ns viz-stra.mut.events
  (:require [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [viz-stra.events :as e]
            [viz-stra.mut.db :as mut-db]))

(re-frame/reg-event-db
  ::on-geneset-deleted
  (fn [db [_ geneset-id]]
    (update-in db [:mutation :landscape-data] dissoc geneset-id)))

(re-frame/reg-event-db
  ::initialize-db
  (fn [db _]
    (merge db mut-db/default-db)))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ panel]]
    (assoc-in db [:mutation :active-panel] panel)))

(re-frame/reg-event-db
  ::set-landscape-sub-plot
  (fn [db [_ plot]]
    (assoc-in db [:mutation :landscape-sub-plot] plot)))

(re-frame/reg-event-db ::set-exclusivity-data
                       (fn [db [_ data]]
                         (assoc-in db [:mutation :exclusivity-data] data)))

(re-frame/reg-event-fx
  ::http-load-landscape-data
  (fn [{:keys [db]} [_ geneset cohort]]
    (println "Requesting landscape data for " (:name geneset) "@" (:name cohort) "...")
    {:db (assoc db :http-loading? true)
     :http-xhrio {:method :post
                  :uri "/mutland"
                  :timeout 40000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)}
                  :on-success [::set-landscape-data (:id geneset) (:id cohort)]
                  :on-failure [::e/http-load-failed]}}))

(re-frame/reg-event-db
  ::set-landscape-data
  (fn [db [_ geneset-id cohort-id json]]
    (println "Landscape data loaded")
    (println (get json "message"))
    (-> db
        (assoc-in [:mutation :landscape-data geneset-id cohort-id] json)
        (assoc :http-loading? false))))

