(ns viz-stra.events
  (:require [cljs.reader]
            [clojure.string :as string]
            [clojure.walk :refer [stringify-keys]]
            [re-frame.core :as re-frame]
            [day8.re-frame.http-fx]
            [re-com.util :refer [insert-nth remove-id-item]]
            [ajax.core :as ajax]
            [viz-stra.db :as db]))

(def gss-storage-key "local-genesets")
(def cos-storage-key "local-cohorts")
(def user-cohorts-group "User Supplied Cohorts")

(re-frame/reg-event-fx
  ::initialize-db
  [(re-frame/inject-cofx :local-stored-genesets)
   (re-frame/inject-cofx :local-stored-cohorts)]
  (fn  [{:keys [local-stored-genesets local-stored-cohorts]} _]
    {:db (-> db/default-db
             (update :genesets merge local-stored-genesets)
             (update :cohorts merge local-stored-cohorts))}))

(re-frame/reg-cofx
  :local-stored-genesets
  (fn [cofx _]
    (assoc cofx :local-stored-genesets
           (into (sorted-map)
                 (some->> (.getItem js/localStorage gss-storage-key)
                          (cljs.reader/read-string))))))

(re-frame/reg-cofx
  :local-stored-cohorts
  (fn [cofx _]
    (assoc cofx :local-stored-cohorts
           (into (sorted-map)
                 (some->> (.getItem js/localStorage cos-storage-key)
                          (cljs.reader/read-string))))))

(def genesets->local-store
  (re-frame/after
    #(let [gss (into (sorted-map)
                     (filter (fn [[_ gene]] (:user? gene)) %))]
       (.setItem js/localStorage gss-storage-key (str gss)))))

(def cohorts->local-store
  (re-frame/after
    #(let [cohorts (into (sorted-map) (filter (fn [[_ cohort]] (:user? cohort)) (:cohorts %)))]
       (.setItem js/localStorage cos-storage-key (str cohorts)))))

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

(re-frame/reg-event-fx
  ::add-a-cohort
  [cohorts->local-store]
  (fn [{:keys [db]} [_ cohort]]
    (let []
      {:db (assoc-in db [:cohorts (:id cohort)]
                     (-> cohort
                         (assoc :user? true)
                         (assoc :group user-cohorts-group)
                         (assoc :alters false)
                         (assoc :exp false)
                         (assoc :clinical nil)))
       :dispatch [::add-alert {:alert-type :info
                               :heading "Cohort data added."
                               :body (:name cohort)}]})))

(re-frame/reg-event-fx
  ::store-cohort-data
  [cohorts->local-store]
  (fn [{:keys [db]} [_ {:keys [id name uuid]} type data]]
    (case type
      :clinical
      (let [clinical-store (str id type)]
        {:pouchdb-store {:clinical-store clinical-store
                         :docs data
                         :on-success [:pouchdb-clinical-store-success id name clinical-store]
                         :on-failure #(js/console.log (.-message %))}})
      {:http-xhrio {:method :post
                    :uri "/upload"
                    :timeout 30000
                    :format (ajax/json-request-format)
                    :response-format (ajax/json-response-format)
                    :params {:uuid uuid :type type :data data}
                    :on-success [:http-cohort-store-success id name type]
                    :on-failure [::http-load-failed false]}})))

(re-frame/reg-event-fx
  :pouchdb-clinical-store-success
  [cohorts->local-store]
  (fn [{:keys [db]} [_ id name clinical-store res]]
    {:db (assoc-in db [:cohorts id :clinical] clinical-store)
     :dispatch [::add-alert {:alert-type :info
                             :heading name
                             :body "Clinical information has been stored."}]}))

(re-frame/reg-event-fx
  :http-cohort-store-success
  [cohorts->local-store]
  (fn [{:keys [db]} [_ id name type res]]
    (let [rows (get res "rows")]
      {:db (assoc-in db [:cohorts id type] true)
       :dispatch [::add-alert {:alert-type :info
                               :heading (case type
                                          :alters "Alteration"
                                          :exp "Expression"
                                          "None?")
                               :body (str rows " rows have been stored.")}]})))

(re-frame/reg-fx
  :pouchdb-store
  (fn [{:keys [clinical-store docs on-success on-failure]}]
    (let [pouchdb (js/PouchDB. clinical-store)]
      (.. pouchdb
          (bulkDocs docs)
          (then #(do (re-frame/dispatch (conj on-success %)) (.close pouchdb)))
          (catch #(do (on-failure %) (.close pouchdb)))))))

(re-frame/reg-event-fx
  ::remove-a-cohort
  [cohorts->local-store]
  (fn [{:keys [db]} [_ cohort]]
    {:http-xhrio {:method :post
                  :uri "/unload"
                  :timeout 30000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:uuid (:uuid cohort)}
                  :on-success [::add-alert {:alert-type :info
                                            :heading "Cohort data has been removed."
                                            :body (:name cohort)}]
                  :on-failure [::http-load-failed false]}
     :pouchdb-destroy {:clinical-store (:clinical cohort)}
     :db (-> db
             (update :clinical-data dissoc (:id cohort))
             (update :cohorts dissoc (:id cohort)))}))

(re-frame/reg-fx
  :pouchdb-destroy
  (fn [{:keys [clinical-store]}]
    (let [pouchdb (js/PouchDB. clinical-store)]
      (.. pouchdb
          (destroy) (then #(print clinical-store "deleted.")) (catch #(js/console.log %))))))

(re-frame/reg-event-db
  ::select-a-cohort
  (fn [db [_ cohort-id]]
    (assoc db :selected-cohort-id cohort-id)))

#_(re-frame/dispatch [::select-a-geneset 2])
#_(re-frame/dispatch [::select-a-cohort 3])

(re-frame/reg-event-fx
  ::http-load-failed
  (fn [{:keys [db]} [_ retry? details]]
    {:db (assoc db :data-loading? false)
     :http-error (assoc details :retry? retry?)}))

(re-frame/reg-fx
  :http-error
  (fn [details]
    (re-frame/dispatch
      [::add-alert
       {:alert-type :danger
        :heading (str "HTTP request failed." (if (:retry? details) " Retrying ..." ""))
        :body [:div
               [:p (:uri details)]
               [:p (:debug-message details)]]}])))

(re-frame/reg-event-fx
  ::http-load-clinical-data
  (fn [{:keys [db]} [_ cohort]]
    (println "(http) Requesting clinical data for" (:name cohort) "...")
    {:db (assoc db :data-loading? true)
     :http-xhrio {:method :post
                  :uri "/clinical"
                  :timeout 30000
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format)
                  :params {:institute (:institute cohort) :cancer-type (:code cohort)}
                  :on-success [::set-clinical-data (:id cohort)]
                  :on-failure [::http-load-failed true]}}))

(re-frame/reg-event-fx
  ::local-load-clinical-data
  (fn [{:keys [db]} [_ cohort]]
    (println "(local) Requesting local clinical data for" (:name cohort) "...")
    (if-let [clinical-store (:clinical cohort)]
      {:db (assoc db :data-loading? true)
       :pouchdb-load {:clinical-store clinical-store
                      :on-success [::set-clinical-data (:id cohort)]
                      :on-failure #(js/console.log (.-message %))}}
      {:db (assoc db :data-loading? true)
       :dispatch [::set-clinical-data (:id cohort) []]})))

(re-frame/reg-fx
  :pouchdb-load
  (fn [{:keys [clinical-store on-success on-failure]}]
    (let [pouchdb (js/PouchDB. clinical-store)]
      (.. pouchdb
          (allDocs #js {:include_docs true})
          (then (fn [res]
                  (let [NaN->nil #(if (number? %) % nil)
                        docs (->> (js->clj res :keywordize-keys true)
                                  :rows
                                  (map :doc)
                                  (map #(-> (dissoc % :_id :_rev)
                                            (update :os_days NaN->nil)
                                            (update :os_status NaN->nil)
                                            (update :dfs_days NaN->nil)
                                            (update :dfs_status NaN->nil)))
                                  stringify-keys
                                  vec)]
                    (re-frame/dispatch (conj on-success docs))
                    (.close pouchdb))))
          (catch #(do (on-failure %) (.close pouchdb)))))))

#_(re-frame/dispatch [::local-load-clinical-data {:clinical "101:clinical" :id 101}])
#_(re-frame/dispatch [::local-load-clinical-data {:clinical "102:clinical" :id 102}])

(re-frame/reg-event-db
  ::add-clinical-data
  (fn [db [_ cohort json]]
    (let [clinical-dat (get-in db [:clinical-data (:id cohort)])
          cmap (reduce #(assoc %1 (get %2 "participant_id") (dissoc %2 "participant_id"))
                       {} (js->clj json))
          cnames (-> cmap first second keys)
          data (->> clinical-dat
                    (map #(merge % (if-let [valm (get cmap (get % "participant_id"))]
                                     valm
                                     (reduce (fn [m c] (assoc m c "NA")) {} cnames))))
                    (map #(->> (dissoc % "participant_id") keys sort
                               (cons "participant_id")
                               (select-keys %)))
                    (vec))]
      (println "Clinical data added:" cnames)
      (cond-> db
        (not (:user? cohort)) (assoc-in [:cohorts (:id cohort) :clinical] "ADDED")
        true (assoc-in [:clinical-data (:id cohort)] data)))))

(re-frame/reg-event-db
  ::set-clinical-data
  (fn [db [_ cohort-id json]]
    (println "Clinical data loaded" (if (empty? json) "(empty)" ""))
    (let [cnames (-> json first (dissoc "participant_id") keys sort (#(cons "participant_id" %)))
          json (->> json
                    (map #(apply array-map (reduce (fn [v c] (concat v [c (get % c)])) [] cnames)))
                    (sort-by #(% "participant_id")))]
      ;(js/console.log json)
      (-> db
          (assoc-in [:clinical-data cohort-id] json)
          (assoc :data-loading? false)))))

(re-frame/reg-event-db
  ::set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
  ::add-alert
  (fn [db [_ alert]]
    (let [{:keys [alert-type heading body auto-close?] :or {alert-type :info auto-close? true}} alert
          id (gensym)]
      (when auto-close?
        (js/setTimeout #(re-frame/dispatch [::remove-alert id]) 10000))
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

