(ns viz-stra.mut.events
  (:require [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax :refer [POST]]
            [viz-stra.events :as e]
            [viz-stra.mut.db :as mut-db]))

(re-frame/reg-event-db
  ::on-geneset-deleted
  (fn [db [_ geneset-id]]
    (update-in db [:mutation :landscape-data] dissoc geneset-id)))

(re-frame/reg-event-db
  ::on-cohort-deleted
  (fn [db [_ cohort-id]]
    (reduce #(update-in %1 [:mutation :landscape-data %2] dissoc cohort-id)
            db (keys (get-in db [:mutation :landscape-data])))))

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
    {:db (assoc db :data-loading? true)
     :http-xhrio {:method :post
                  :uri "/mutland"
                  :timeout 40000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:genes (:genes geneset)
                           :institute (:institute cohort)
                           :cancer-type (:code cohort)
                           :uuid (:uuid cohort)
                           :no-group-list (not (nil? (:clinical cohort)))}
                  :on-success [::set-landscape-data geneset cohort]
                  :on-failure [::e/http-load-failed true]}}))

#_(POST "/mutland"
        {:format :json
         :response-format :json
         :params {:genes ["CDKN2A" "CDKN2B" "CDK4" "RB1"]
                  :uuid "52b63e20-fed0-4990-9129-23c3fa0049ba"}
         :handler #(js/console.log %)})

(defn- fill-clinical-inf [db cohort json]
  (let [clinical-dat (get-in db [:clinical-data (:id cohort)])
        cnames (-> clinical-dat first keys set
                   (disj "participant_id" "os_days" "os_status" "dfs_days" "dfs_status")
                   sort)
        fill-data #(let [group-list
                         (vec (for [n cnames]
                                {"name" n
                                 "data" (mapv (fn [m] {"participant_id" (get m "participant_id")
                                                       "value" (if-let [v (get m n)] v "NA")})
                                              clinical-dat)}))]
                     (assoc-in json ["data" "group_list"] group-list))]
    (if (:user? cohort)
      (let [mpids (->> (get-in json ["data" "mutation_list"])
                       (map #(get % "participant_id"))
                       (into #{}))
            cpids (map #(get % "participant_id") clinical-dat)
            fill-na #(let [data (mapv (fn [p] {"participant_id" p "value" "NA"}) %)]
                       (assoc-in json ["data" "group_list"] [{"name" "No clinical data" "data" data}]))]
        (if (empty? clinical-dat)
          (fill-na mpids)
          (if (empty? cnames)
            (fill-na cpids)
            (fill-data))))
      (if (:clinical cohort) ;; Not a user cohort but with added clinical information
        (fill-data)
        json))))


(re-frame/reg-event-db
  ::set-landscape-data
  (fn [db [_ geneset cohort json]]
    (let [json (fill-clinical-inf db cohort json)]
      ;(js/console.log json)
      (println (str (if (:user? cohort) "User l" "L") "andscape data loaded."))
      (println (get json "message"))
      (-> db
          (assoc-in [:mutation :landscape-data (:id geneset) (:id cohort)] json)
          (assoc :data-loading? false)))))

