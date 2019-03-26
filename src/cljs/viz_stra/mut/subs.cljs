(ns viz-stra.mut.subs
  (:require [re-frame.core :as re-frame]
            [viz-stra.subs :as s]))

(re-frame/reg-sub ::active-panel
                  (fn [db _]
                    (get-in db [:mutation :active-panel])))

(re-frame/reg-sub ::exclusivity-data
                  (fn [db _]
                    (get-in db [:mutation :exclusivity-data])))

(re-frame/reg-sub :landscape-db
                  (fn [db _] (get-in db [:mutation :landscape-data])))

(re-frame/reg-sub ::landscape-data-by
                  :<- [:landscape-db]
                  (fn [land-db [_ geneset cohort] _]
                    (get-in land-db [(:id geneset) (:id cohort)])))

(re-frame/reg-sub ::landscape-data
                  :<- [:landscape-db]
                  :<- [::s/selected-geneset]
                  :<- [::s/selected-cohort]
                  (fn [[land-db geneset cohort] _ _]
                    (get-in land-db [(:id geneset) (:id cohort)])))

(re-frame/reg-sub
  ::landscape-sub-plot
  (fn [db _] (get-in db [:mutation :landscape-sub-plot])))

