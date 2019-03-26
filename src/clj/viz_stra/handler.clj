(ns viz-stra.handler
  (:require [viz-stra.db :refer :all]
            [viz-stra.stat :refer [chisq->p cox-regression]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [response resource-response status]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.core.async :refer [>!! alts!! chan timeout]]
            [ajax.core :as ajax]
            [clojure.core.matrix :as m]
            [incanter.core :refer [log2]]
            [incanter.stats :refer [mean correlation]]))

(defn echo-params [req]
  (let [params (:params req)]
    params))

(defn echo-form-params [req form-id]
  (let [params (get-in req [:form-params form-id])]
    params))

(defn get-patients-data
  ([req] (get-patients-data (get-in req [:params :institute]) (get-in req [:params :cancer-type])))
  ([institute cancer-type]
   (let [subtype-spec (get-in subtype-specs (map keyword [institute cancer-type]))]
     (->> (get-patient-list CGIS
                            {:institute institute :cancer-type cancer-type}
                            {} {:keywordize? false})
          (map (fn [p] (update p "os_status" #(if (nil? %) % (if % 1 0)))))
          (map (fn [p] (update p "dfs_status" #(if (nil? %) % (if % 1 0)))))
          (map #(select-keys % (concat ["participant_id"
                                        "os_days"
                                        "os_status"
                                        "dfs_days"
                                        "dfs_status"]
                                       (map first subtype-spec))))
          (map #(set/rename-keys % (into {} subtype-spec)))))))

#_(->> (get-patients-data "tcga" "luad") count)
#_(->> (get-patients-data "tcga" "luad") (map #(get % "os_days")) (filter nil?) count)
#_(->> (get-patients-data "tcga" "stad") count)
#_(->> (get-patients-data "tcga" "stad") (filter #(or (nil? (% "os_status")) (nil? (% "os_days")))) count)
#_(->> (get-patients-data "tcga" "blca") count)

(defn- log2p [x] (cond (number? x) (log2 (inc x)) (coll? x) (log2 (map inc x))))
#_(= (log2 1001) (log2p 1000))
#_(= (log2 [1 2 3 4]) (log2p [0 1 2 3]))

#_(mean [2 3 4 5])
#_(mean [1.7465798777938295 2.609070019811353 3.323933217094561 3.7310292495218236
         2.6295384945786298 1.8677375209134457 4.634977882915063 1.9570929234205539
         2.4724847366386165 1.628913236700244 3.6947863112877397 2.918138397885022])
#_(log2 (m/emap inc (m/array [[1.7465798777938295 2.609070019811353]
                              [35.7196561828204 32.307048847895196]])))

(defn cox
  ([req] (cox (get-in req [:params :institute])
              (get-in req [:params :cancer-type])
              (get-in req [:params :genes])))
  ([institute cancer-type genes]
   (let [cohort-data (get-cohort-prognostic-values
                       CGIS {:institute institute :cancer-type cancer-type :genes genes})
         tpms (reduce (fn [r e] (merge-with concat r {(:hugo_symbol e) [(:tpm e)]}))
                      {} cohort-data)
         genes (keys tpms)
         days (->> (map :os_days cohort-data)
                   (partition (count genes))
                   (map first))
         censors (->> (map :os_status cohort-data)
                      (partition (count genes))
                      (map first)
                      (map #(if % 1 0)))
         tpms (apply dissoc tpms
                     (for [[gene exps] tpms
                           :when (< (count (filter pos? exps)) (* (count days) 0.005))]
                       gene))]
     (if-let [c (some->> tpms vals ;covariates
                         (cox-regression days censors))]
       (assoc c :genes (keys tpms))
       {:coefficients [] :z-statistics [] :p-values [] :genes []}))))

(defn cox2
  [cohort-data clinicals]
  (let [clinicals (filter #(not (nil? (% "os_days"))) clinicals)
        surv-data
        (reduce #(assoc %1 (%2 "participant_id") {:day (%2 "os_days") :censor (%2 "os_status")})
                {} clinicals)
        has-surv? (set (keys surv-data))
        cohort-data (filter #(has-surv? (:participant_id %)) cohort-data)
        patients (distinct (map :participant_id cohort-data))
        days (map #((surv-data %) :day) patients)
        censors (map #((surv-data %) :censor) patients)
        tpms (-> (reduce (fn [r e] (merge-with concat r {(:hugo_symbol e) [(:tpm e)]}))
                         {} cohort-data)
                 (#(apply dissoc %
                          (for [[gene exps] %
                                :when (< (count (filter pos? exps)) (* (count patients) 0.005))]
                            gene))))]
    (if-let [c (some->> tpms vals m/array (m/emap inc) log2 ;covariates
                        (cox-regression days censors))]
      (assoc c :genes (keys tpms))
      {:coefficients [] :z-statistics [] :p-values [] :genes []})))

#_(cox "tcga" "laml" ["NMS"])
#_(cox "tcga" "brca" ["NMS"])
#_(cox "tcga" "stad" ["NMS"])
#_(cox "tcga" "luad" ["NMS"])
#_(let [institute "tcga" cancer-type "luad" genes ["NMS"]]
    (cox2 (get-cohort-exp-values CGIS {:institute institute :cancer-type cancer-type :genes genes})
          (get-patients-data institute cancer-type)))
#_(time (cox "tcga" "luad" ["TP53" "RB1" "BRCA1" "BRCA2"]))
#_(time (let [institute "tcga" cancer-type "luad" genes ["TP53" "RB1" "BRCA1" "BRCA2"]]
          (cox2 (get-cohort-exp-values CGIS {:institute institute :cancer-type cancer-type :genes genes})
                (get-patients-data institute cancer-type))))
#_(assert (= (cox "tcga" "luad" ["TP53" "RB1" "BRCA1" "BRCA2"])
             (cox "tcga" "luad" ["TP53" "RB1" "BRCA1" "BRCA2" "NMS"])
             (let [institute "tcga" cancer-type "luad" genes ["TP53" "RB1" "BRCA1" "BRCA2"]]
               (cox2 (get-cohort-exp-values CGIS {:institute institute :cancer-type cancer-type :genes genes})
                     (get-patients-data institute cancer-type)))
             (let [institute "tcga" cancer-type "luad" genes ["TP53" "RB1" "BRCA1" "BRCA2" "NMS"]]
               (cox2 (get-cohort-exp-values CGIS {:institute institute :cancer-type cancer-type :genes genes})
                     (get-patients-data institute cancer-type)))
             {:coefficients [0.008525693538151077 0.49611193279130195 -0.19190187221055738 -0.022264129890562936]
              :z-statistics [0.0726440313006495 2.063754223170312 -1.4630180676352 -0.25933157895127695]
              :p-values [0.9420893873468974 0.039041025357615666 0.14346243515422064 0.795379416127628]
              :genes ["BRCA1" "BRCA2" "RB1" "TP53"]}))

#_(correlation [1 2 3 4] [5 6 7 8])
#_(correlation [5 6 7 8] [1 2 3 4])
#_(correlation [1 2 3 4] [8 7 6 5])

#_(Double/parseDouble (format "%.10f" -2.220446049250313E-16))
#_(Double/parseDouble (format "%.10f" 2.4724847366786165))

(defn- to-fixed
  [scale]
  (fn [n] (.doubleValue (.setScale (bigdec n) scale BigDecimal/ROUND_HALF_UP))))

#_((to-fixed 10) -2.220446049250313E-16)
#_((to-fixed 10) 2.4724847366786165)

(defn farthest-centroid
  [cohort-data clinicals]
  (let [has-data? (set (map :participant_id cohort-data))
        patients (->> clinicals
                      (filter #(has-data? (% "participant_id")))
                      (filter #(not (nil? (% "os_days"))))
                      (sort-by #(% "os_days")))
        goods (-> (count patients) (/ 100) (* 2.5) int
                  ;(#(if (< % 10) 10 %))
                  (take-last patients))
        goods? (set (map #(% "participant_id") goods))
        goods-data (filter #(goods? (% :participant_id)) cohort-data)
        good-tpms (reduce (fn [r e] (merge-with concat r {(:hugo_symbol e) [(log2p (:tpm e))]}))
                          {} goods-data)
        genes (keys good-tpms)
        good-means (map mean (vals good-tpms))
        correlations
        (reduce (fn [m [pid ds]]
                  (let [exps-by-gene (into {} (map (juxt :hugo_symbol #(log2p (:tpm %))) ds))
                        exps (map #(exps-by-gene %) genes)]
                    (assoc m pid ((to-fixed 10) (- 1 (correlation exps good-means))))))
                {} (group-by :participant_id cohort-data))]
    correlations))

#_(time (let [institute "tcga"
              cancer-type "luad"
              genes ["TP53" "RORC" "MDM2"]
              scores (farthest-centroid
                       (get-cohort-exp-values
                         CGIS {:institute institute :cancer-type cancer-type :genes genes})
                       (get-patients-data institute cancer-type))]
          (-> scores vals sort)))

(defn get-exp-signature [req]
  (let [genes (get-in req [:params :genes])
        institute (get-in req [:params :institute])
        cancer-type (get-in req [:params :cancer-type])
        subtypes (get-subtype-values CGIS {:institute institute :cancer-type cancer-type})
        patients (get-patients-data institute cancer-type)
        cohort-data (get-cohort-exp-values CGIS {:institute institute :cancer-type cancer-type :genes genes})]
    {:status 0
     :message "OK"
     :data {:subtype_list subtypes
            :patient_list patients
            :gene_list (map (fn [g] {:hugo_symbol g}) genes)
            :cohort_rna_list cohort-data
            :sample_rna_list []}
     ;:cox (cox institute cancer-type genes)
     ;; Without additional DB query
     :cox (cox2 cohort-data patients)
     :fc (when (> (count genes) 1) (farthest-centroid cohort-data patients))}))

(defn response-for-exp-clusters [req]
  (let [genes (get-in req [:params :genes])
        institute (get-in req [:params :institute])
        cancer-type (get-in req [:params :cancer-type])
        response-channel (chan)]
    (ajax/POST "http://localhost:8008"
               {:format :json
                :response-format :json
                :params {:genes genes
                         :institute institute
                         :cancer-type cancer-type}
                :handler #(>!! response-channel %)})
    (if-let [data (first (alts!! [response-channel (timeout 60000)]))]
      (response data)
      (-> (response "Request timed out. Try again, please.")
          (status 500)))))

(defn- get-group-list [institute cancer-type]
  (let [specs (get-in subtype-specs (map keyword [institute cancer-type]))
        rs (get-subtype-list CGIS {:institute institute :cancer-type cancer-type :subtypes specs}
                             {} {:keywordize? false :identifiers identity})]
    (map (fn [s] {:name s
                  :data (reduce #(conj %1 {:participant_id (get %2 "participant_id")
                                           :value (get %2 s)}) [] rs)})
         (map second specs))))

#_(get-group-list "tcga" "luad")

#_(let [institute "tcga"
        cancer-type "luad"
        genes ["TP53" "RB1" "CTSL2"]
        muts (get-mutation-list CGIS {:institute institute :cancer-type cancer-type :genes genes})
        mut-genes (set (map :gene muts))]
    (filter mut-genes genes))

(defn get-mut-landscape [req]
  (let [genes (get-in req [:params :genes])
        institute (get-in req [:params :institute])
        cancer-type (get-in req [:params :cancer-type])
        muts (get-mutation-list CGIS {:institute institute :cancer-type cancer-type :genes genes})]
    {:status 0
     :message "OK"
     :data {:name (str "Comutations in " (string/upper-case institute) " " (string/upper-case cancer-type))
            :type (string/upper-case cancer-type)
            :mutation_list muts
            :gene_list (map (fn [g] {:gene g :p 0.0 :q 0.0})
                            (filter (set (map :gene muts)) genes))
            :group_list (get-group-list institute cancer-type)
            :patient_list []}}))

(defn validate-gene-symbols [req]
  (let [genes (get-in req [:params :genes])
        valids (map :hugo_symbol (get-valid-gene-symbols CGIS {:genes genes}))
        unknowns (let [valid? (set (map string/upper-case valids))]
                   (filter #(not (valid? (string/upper-case %))) genes))]
    {:valid valids
     :unknown unknowns}))

(defn geneset-suggestions-for
  [word & {:keys [limit cols] :or {limit 20 cols #{}}}]
  (->> (get-msigdb-suggestions CGIS {:like (str "%" word "%") :limit limit})
       (filter #(or (empty? cols) (cols (:collection %))))
       (map #(update % :genes (fn [genes] (string/split genes #","))))
       (sort-by #(count (:genes %)))
       #_(take 25)))

#_(geneset-suggestions-for "p53" :cols #{"C2"})


(defroutes routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (GET "/mutex" [] (resource-response "data/luad_mutex.json" {:root "public"}))
  (POST "/echo" req (response (echo-params req)))
  (POST "/echo.f" req (response (echo-form-params req "chi_square_score")))
  (POST "/expsig" req (response (get-exp-signature req)))
  (POST "/clinical" req (response (get-patients-data req)))
  (POST "/expclu" req (response-for-exp-clusters req))
  (POST "/mutland" req (response (get-mut-landscape req)))
  (POST "/chip" req (response (chisq->p (get-in req [:form-params "chi_square_score"]))))
  (POST "/geneset" req (response (validate-gene-symbols req)))
  (POST "/cox" req (response (cox req)))
  (POST "/msig" req (response (geneset-suggestions-for (get-in req [:params :word]))))
  (resources "/"))

(def handler (-> routes
                 wrap-params
                 wrap-keyword-params
                 wrap-json-params
                 wrap-json-response))

(def dev-handler (-> #'handler wrap-reload))

