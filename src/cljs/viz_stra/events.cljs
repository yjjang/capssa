(ns viz-stra.events
  (:require [cljs.reader]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [re-com.util :refer [insert-nth remove-id-item]]
            [ajax.core :as ajax]
            [viz-stra.db :as db]))

(re-frame/reg-event-fx
  ::initialize-db
  [(re-frame/inject-cofx :local-stored-genesets)]
  (fn  [{:keys [local-stored-gss]} _]
    {:db (update db/default-db :genesets merge local-stored-gss)}))

(def gss-storage-key "local-genesets")

(re-frame/reg-cofx
  :local-stored-genesets
  (fn [cofx _]
    (assoc cofx :local-stored-gss
           (into (sorted-map)
                 (some->> (.getItem js/localStorage gss-storage-key)
                          (cljs.reader/read-string))))))

(def genesets->local-store
  (re-frame/after
    #(let [gss (into (sorted-map)
                     (filter (fn [[_ gene]] (:user? gene)) %))]
       (.setItem js/localStorage gss-storage-key (str gss)))))

(re-frame/reg-event-db
  ::add-a-geneset
  [(re-frame/path :genesets)
   genesets->local-store]
  (fn [genesets [_ gs]]
    (let [id (-> genesets keys last (#(inc (if (> % 100) % 100))))
          geneset (-> gs (assoc :id id) (assoc :user? true))]
      (re-frame/dispatch
        [::add-alert {:alert-type (if (empty? (:unknowns gs)) :info :warning)
                      :heading "Gene set added."
                      :body [:div
                             [:p [:strong (:name gs)] " (" (count (:genes gs)) " genes)"]
                             (when-not (empty? (:unknowns gs))
                               [:div
                                [:p "Invalid gene symbols were ignored:"]
                                [:ul (for [u (:unknowns gs)] ^{:key u} [:li u])]])]}])
      (assoc genesets id geneset))))

(re-frame/reg-event-db
  ::modify-a-geneset
  [(re-frame/path :genesets)
   genesets->local-store]
  (fn [genesets [_ gs]]
    (re-frame/dispatch
      [::add-alert {:alert-type (if (empty? (:unknowns gs)) :info :warning)
                    :heading "Gene set modified."
                    :body [:div
                           [:p [:strong (:name gs)] " (" (count (:genes gs)) " genes)"]
                           (when-not (empty? (:unknowns gs))
                             [:div
                              [:p "Invalid gene symbols were ignored:"]
                              [:ul (for [u (:unknowns gs)] ^{:key u} [:li u])]])]}])
    (assoc genesets (:id gs) gs)))

(re-frame/reg-event-db
  ::remove-a-geneset
  [(re-frame/path :genesets)
   genesets->local-store]
  (fn [genesets [_ gs]]
    (re-frame/dispatch
      [::add-alert {:alert-type :warning
                    :heading "Gene set removed."
                    :body (:name gs)}])
    (dissoc genesets (:id gs))))

#_(re-frame/dispatch
    [::add-a-geneset {:name "Test Genes"
                      :genes ["BRCA1" "BRCA2"]
                      :desc "No description"}])
#_(re-frame/dispatch [::remove-a-geneset 5])

(re-frame/reg-event-db
  ::select-a-geneset
  (fn [db [_ geneset-id]]
    (assoc db :selected-geneset-id geneset-id)))

(re-frame/reg-event-db
  ::add-a-cohort
  (fn [db [_ cohort-info]]
    (let [id (-> (:cohorts db) keys last ((fnil inc 0)))
          cohort (assoc cohort-info :id id)]
      (assoc-in db [:cohorts id] cohort))))

(re-frame/reg-event-db
  ::select-a-cohort
  (fn [db [_ cohort-id]]
    (assoc db :selected-cohort-id cohort-id)))

#_(re-frame/dispatch [::select-a-geneset 2])
#_(re-frame/dispatch [::select-a-cohort 3])

(re-frame/reg-event-fx
  ::http-load-failed
  (fn [{:keys [db]} [_ details]]
    {:db (assoc db :http-loading? false)
     :http-error details}))

(re-frame/reg-fx
  :http-error
  (fn [details]
    (re-frame/dispatch
      [::add-alert
       {:alert-type :danger
        :heading "HTTP request failed. Retrying..."
        :body [:div
               [:p (:uri details)]
               [:p (:debug-message details)]]}])))

(re-frame/reg-event-fx
  ::http-load-clinical-data
  (fn [{:keys [db]} [_ cohort]]
    (println "Requesting clinical data for" (:name cohort) "...")
    {:db (assoc db :http-loading? true)
     :http-xhrio {:method :post
                  :uri "/clinical"
                  :timeout 30000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:institute (:institute cohort) :cancer-type (:code cohort)}
                  :on-success [::set-clinical-data (:id cohort)]
                  :on-failure [::http-load-failed]}}))

(re-frame/reg-event-db
  ::set-clinical-data
  (fn [db [_ cohort-id json]]
    (println "Clinical data loaded")
    (-> db
        (assoc-in [:clinical-data cohort-id] json)
        (assoc :http-loading? false))))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
  ::add-alert
  (fn [db [_ alert]]
    (let [{:keys [alert-type heading body] :or {alert-type :info}} alert
          id (gensym)]
      (js/setTimeout #(re-frame/dispatch [::remove-alert id]) 5000)
      (update db :alert-list
              insert-nth 0 {:id id
                            :alert-type alert-type
                            :heading heading
                            :body body
                            :padding "4px"
                            :closeable? true}))))

(re-frame/reg-event-db
  ::remove-alert
  (fn [db [_ alert-id]]
    (update db :alert-list #(remove-id-item alert-id %))))

#_(re-frame/dispatch
    [::add-alert {:heading "Gene set added."
                  :body "BRCA1/2 Genes"}])

