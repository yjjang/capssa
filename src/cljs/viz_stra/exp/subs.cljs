(ns viz-stra.exp.subs
  (:require [re-frame.core :as re-frame]
            [viz-stra.subs :as s]))

(re-frame/reg-sub
  ::active-panel
  (fn [db _] (get-in db [:expression :active-panel])))

(re-frame/reg-sub
  ::cluster-sub-plot
  (fn [db _] (get-in db [:expression :cluster-sub-plot])))

(re-frame/reg-sub
  ::risk-sub-plot
  (fn [db _] (get-in db [:expression :risk-sub-plot])))

(re-frame/reg-sub :cluster-db
                  (fn [db _] (get-in db [:expression :cluster-data])))

(re-frame/reg-sub ::cluster-data-by
                  :<- [:cluster-db]
                  (fn [clust-db [_ geneset cohort] _]
                    (get-in clust-db [(:id geneset) (:id cohort)])))

(re-frame/reg-sub ::cluster-data
                  :<- [:cluster-db]
                  :<- [::s/selected-geneset]
                  :<- [::s/selected-cohort]
                  (fn [[clust-db geneset cohort] _ _]
                    (get-in clust-db [(:id geneset) (:id cohort)])))

(re-frame/reg-sub :signature-db
                  (fn [db _] (get-in db [:expression :signature-data])))

(re-frame/reg-sub ::signature-data-by
                  :<- [:signature-db]
                  (fn [sig-db [_ geneset cohort] _]
                    (get-in sig-db [(:id geneset) (:id cohort)])))

(re-frame/reg-sub ::signature-data
                  :<- [:signature-db]
                  :<- [::s/selected-geneset]
                  :<- [::s/selected-cohort]
                  (fn [[sig-db geneset cohort] _ _]
                    (get-in sig-db [(:id geneset) (:id cohort)])))

