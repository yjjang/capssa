(ns viz-stra.exp.events
  (:require [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [viz-stra.events :as e]
            [viz-stra.exp.db :as exp-db]))

(re-frame/reg-event-db
  ::on-geneset-deleted
  (fn [db [_ geneset-id]]
    (-> db
        (update-in [:expression :cluster-data] dissoc geneset-id)
        (update-in [:expression :signature-data] dissoc geneset-id))))

(re-frame/reg-event-db
  ::initialize-db
  (fn [db _]
    (merge db exp-db/default-db)))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ panel]]
    (assoc-in db [:expression :active-panel] panel)))

(re-frame/reg-event-db
  ::set-cluster-sub-plot
  (fn [db [_ plot]]
    (assoc-in db [:expression :cluster-sub-plot] plot)))

(re-frame/reg-event-db
  ::set-risk-sub-plot
  (fn [db [_ plot]]
    (assoc-in db [:expression :risk-sub-plot] plot)))

(re-frame/reg-event-fx
  ::http-load-cluster-data
  (fn [{:keys [db]} [_ geneset cohort]]
    (println "Requesting cluster data for " (:name geneset) "@" (:name cohort) "...")
    {:db (assoc db :http-loading? true)
     :http-xhrio {:method :post
                  :uri "/expclu"
                  :timeout 60000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)}
                  :on-success [::set-cluster-data (:id geneset) (:id cohort)]
                  :on-failure [::e/http-load-failed]}}))

(re-frame/reg-event-db
  ::set-cluster-data
  (fn [db [_ geneset-id cohort-id json]]
    (println "Cluster data loaded")
    (-> db
        (assoc-in [:expression :cluster-data geneset-id cohort-id] json)
        (assoc :http-loading? false))))

(re-frame/reg-event-fx
  ::http-load-signature-data
  (fn [{:keys [db]} [_ geneset cohort]]
    (println "Requesting signature data for " (:name geneset) "@" (:name cohort) "...")
    {:db (assoc db :http-loading? true)
     :http-xhrio {:method :post
                  :uri "/expsig"
                  :timeout 120000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)}
                  :on-success [::set-signature-data (:id geneset) (:id cohort)]
                  :on-failure [::e/http-load-failed]}}))

(re-frame/reg-event-db
  ::set-signature-data
  (fn [db [_ geneset-id cohort-id json]]
    (println "Signature data loaded")
    (println (get json "message"))
    (-> db
        (assoc-in [:expression :signature-data geneset-id cohort-id] json)
        (assoc :http-loading? false))))

