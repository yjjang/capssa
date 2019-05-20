(ns viz-stra.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub ::name
                  (fn [db _]
                    (:name db)))

(re-frame/reg-sub ::description
                  (fn [db _]
                    (:description db)))

(re-frame/reg-sub ::active-panel
                  (fn [db _]
                    (:active-panel db)))

(re-frame/reg-sub ::alert-list
                  (fn [db _]
                    (:alert-list db)))

(re-frame/reg-sub ::data-loading?
                  (fn [db _]
                    (:data-loading? db)))

(re-frame/reg-sub ::geneset-list
                  (fn [db _] (vals (:genesets db))))

(re-frame/reg-sub ::default-genesets
                  :<- [::geneset-list]
                  (fn [gs-list _]
                    (filter #(not (:user? %)) gs-list)))

(re-frame/reg-sub ::user-genesets
                  :<- [::geneset-list]
                  (fn [gs-list _]
                    (filter :user? gs-list)))

(re-frame/reg-sub ::geneset-of
                  (fn [db [_ id]] ((:genesets db) id)))

(re-frame/reg-sub ::cohort-ids
                  (fn [db _] (keys (:cohorts db))))

(re-frame/reg-sub ::cohort-list
                  (fn [db _] (vals (:cohorts db))))

(re-frame/reg-sub ::default-cohorts
                  :<- [::cohort-list]
                  (fn [co-list _]
                    (filter #(not (:user? %)) co-list)))

(re-frame/reg-sub ::user-cohorts
                  :<- [::cohort-list]
                  (fn [co-list _]
                    (filter :user? co-list)))

(re-frame/reg-sub ::cohort-of
                  (fn [db [_ id]] ((:cohorts db) id)))

(re-frame/reg-sub ::selected-geneset
                  (fn [db _] (get-in db [:genesets (:selected-geneset-id db)])))

(re-frame/reg-sub ::selected-geneset-id
                  (fn [db _] (:selected-geneset-id db)))

(re-frame/reg-sub ::selected-cohort
                  (fn [db _] (get-in db [:cohorts (:selected-cohort-id db)])))

(re-frame/reg-sub ::selected-cohort-id
                  (fn [db _] (:selected-cohort-id db)))

(re-frame/reg-sub ::clinical-data
                  (fn [db [_ cohort]]
                    (get-in db [:clinical-data (:id cohort)])))

(re-frame/reg-sub ::survival-data
                  (fn [[_ cohort] _]
                    (re-frame/subscribe [::clinical-data cohort]))
                  (fn [clinic-dat _]
                    (when clinic-dat
                      (map #(select-keys % ["participant_id"
                                            "os_status"
                                            "os_days"
                                            "dfs_status"
                                            "dfs_days"]) clinic-dat))))

