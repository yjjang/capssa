(ns viz-stra.exp.events
  (:require [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax :refer [POST]]
            [clojure.walk :refer [keywordize-keys stringify-keys]]
            [viz-stra.events :as e]
            [viz-stra.subs :as s]
            [viz-stra.exp.db :as exp-db]))

(re-frame/reg-event-db
  ::on-geneset-deleted
  (fn [db [_ geneset-id]]
    (-> db
        (update-in [:expression :cluster-data] dissoc geneset-id)
        (update-in [:expression :signature-data] dissoc geneset-id))))

(re-frame/reg-event-db
  ::on-cohort-deleted
  (fn [db [_ cohort-id]]
    (-> db
        (#(reduce (fn [d gid] (update-in d [:expression :cluster-data gid] dissoc cohort-id))
                  % (keys (get-in % [:expression :cluster-data]))))
        (#(reduce (fn [d gid] (update-in d [:expression :signature-data gid] dissoc cohort-id))
                  % (keys (get-in % [:expression :signature-data])))))))

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
    {:db (assoc db :data-loading? true)
     :http-xhrio {:method :post
                  :uri "/expclu"
                  :timeout 60000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)
                           :uuid (:uuid cohort)
                           :clinical-data (when (or (:user? cohort) (:clinical cohort))
                                            (get-in db [:clinical-data (:id cohort)]))}
                  :on-success [::set-cluster-data (:id geneset) (:id cohort)]
                  :on-failure [::e/http-load-failed true]}}))

#_(POST "/expclu"
        {:format :json
         :response-format :json
         :params {:genes ["CDKN2A" "CDKN2B" "CDK4" "RB1"]
                  :uuid "52b63e20-fed0-4990-9129-23c3fa0049ba"
                  :clinical-data (deref (re-frame/subscribe [::s/clinical-data {:id 101}]))}
         :handler #(js/console.log %)})

(re-frame/reg-event-fx
  ::set-cluster-data
  (fn [{:keys [db]} [_ geneset-id cohort-id json]]
    (println "Cluster data loaded.")
    (let [clinical-data (get-in db [:clinical-data cohort-id])
          cohort (get-in db [:cohorts cohort-id])
          effects {:db (-> db
                           (assoc-in [:expression :cluster-data geneset-id cohort-id] json)
                           (assoc :data-loading? false))}]
      ; Initial loading for registered cohorts e.g. tcga 
      (if (and (not (:user? cohort)) (nil? clinical-data))
        (merge effects {:dispatch [::e/http-load-clinical-data cohort]}) effects))))

#_(re-frame/reg-event-db
    ::update-cluster-data
    (re-frame/path [:expression :cluster-data])
    (fn [cluster-data [_ geneset-id cohort-id cdata]]
      (let [cluster-dat (get-in cluster-data [geneset-id cohort-id])
            cmap (reduce #(assoc %1 (get %2 "participant_id") (dissoc %2 "participant_id"))
                         {} (js->clj cdata))
            cnames (-> cmap first second keys)
            pids (get-in cluster-dat ["data" "feature_names"])
            values (mapv (fn [c]
                           (mapv
                             (fn [p] (if-let [v (get (get cmap p) c)] v "NA"))
                             pids))
                         cnames)
            data (into (sorted-map)
                       (zipmap 
                         (concat (get-in cluster-dat ["column_metadata" "feature_names"]) cnames)
                         (concat (get-in cluster-dat ["column_metadata" "features"]) values)))
            json (-> cluster-dat
                     (assoc-in ["column_metadata" "feature_names"] (keys data))
                     (assoc-in ["column_metadata" "features"] (vals data)))]
        (println "Cluster data updated for added clinical information.")
        ;(js/console.log json)
        (assoc-in cluster-data [geneset-id cohort-id] json))))

(re-frame/reg-event-fx
  ::http-load-signature-data
  (fn [{:keys [db]} [_ geneset cohort]]
    (println "Requesting signature data for " (:name geneset) "@" (:name cohort) "...")
    {:db (assoc db :data-loading? true)
     :http-xhrio {:method :post
                  :uri "/expsig"
                  :timeout 120000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)
                           :uuid (:uuid cohort)
                           :surv-data (when-let [clinical-dat (get-in db [:clinical-data (:id cohort)])]
                                        (map #(select-keys % ["participant_id"
                                                              "os_status"
                                                              "os_days"
                                                              "dfs_status"
                                                              "dfs_days"]) clinical-dat))
                           :no-clinical-info (not (nil? (:clinical cohort)))}
                  :on-success [::set-signature-data (:id geneset) (:id cohort)]
                  :on-failure [::e/http-load-failed true]}}))

#_(POST "/expsig"
        {:format :json
         :response-format :json
         :params {:genes ["CDKN2A" "CDKN2B" "CDK4" "RB1"]
                  :uuid "52b63e20-fed0-4990-9129-23c3fa0049ba"
                  :clinical-data (deref (re-frame/subscribe [::s/clinical-data {:id 101}]))}
         :handler #(js/console.log %)})

(defn- makeup-subtypes [clinical-data]
  (->> (keywordize-keys clinical-data)
       (map #(dissoc % :participant_id :os_days :os_status :dfs_days :dfs_status))
       (map #(map (fn [[k v]] {:subtype k :value v :column_type "value"}) (filter second %)))
       (apply concat)
       (into #{}) vec
       (sort-by (juxt :subtype :value))
       #_(map stringify-keys)))

(re-frame/reg-event-fx
  ::set-signature-data
  (fn [{:keys [db]} [_ geneset-id cohort-id json]]
    (let [clinical-data (get-in db [:clinical-data cohort-id])
          cohort (get-in db [:cohorts cohort-id])
          effects {:db (let [subtype-list (-> (get-in json ["data" "subtype_list"])
                                              (#(if (and (empty? %) clinical-data)
                                                  (makeup-subtypes clinical-data) %)))
                             patient-list (-> (get-in json ["data" "patient_list"])
                                              (#(if (and (empty? %) clinical-data)
                                                  clinical-data %)))
                             json (-> json
                                      (assoc-in ["data" "subtype_list"] subtype-list)
                                      (assoc-in ["data" "patient_list"] patient-list))]
                         (println "Signature data loaded")
                         (println (get json "message"))
                         ;(js/console.log json)
                         (-> db
                             (assoc-in [:expression :signature-data geneset-id cohort-id] json)
                             (assoc :data-loading? false)))}]
      ; Initial loading for registered cohorts e.g. tcga 
      (if (and (not (:user? cohort)) (nil? clinical-data))
        (merge effects {:dispatch [::e/http-load-clinical-data cohort]})
        effects))))

