(ns viz-stra.db
  (:require [hikari-cp.core :as hikari]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [config.core :refer [env]]))

(def cgis-config {:jdbc-url (let [host (or (env :db-host) "localhost")
                                  port (or (env :db-port) "3306")]
                              (str "jdbc:mysql://" host ":" port "/CGIS?rewriteBatchedStatements=true"))
                  :username (env :db-user)
                  :password (env :db-pass)})

(def CGIS {:datasource (hikari/make-datasource cgis-config)})

#_(jdbc/with-db-connection [conn CGIS]
    (let [rows (jdbc/query conn "SELECT 0")]
      rows))


(hugsql/def-db-fns "sql/clinicals.sql")
#_(hugsql/def-sqlvec-fns "sql/clinicals.sql")

(def subtype-specs
  (reduce (fn [specs r]
            (update-in specs
                       [(keyword (:institute r)) (keyword (:cancer_type r))]
                       #(conj (vec %) [(:id r) (:name r)])))
          {} (get-all-subtype-specs CGIS)))
#_(let [specs subtype-specs] specs)

(comment
  "Test queries for clinical data"

  (count (get-patient-list CGIS {:institute "tcga" :cancer-type "thym"}))
  (count (get-patient-list CGIS {:institute "smc" :cancer-type "luad"}))

  (get-patient-list-sqlvec {:institute "tcga" :cancer-type "luad"})
  (get-patient-list CGIS {:institute "tcga" :cancer-type "luad"}))

(comment
  "Subtype table(VS_COHORT_SUBTYPE_TB) creation, write data and tests"

  (do (drop-cohort-subtype-table! CGIS)
      (create-cohort-subtype-table! CGIS)
      (let [mk-tuples (fn [institute cancer-type subtype]
                        (let [[stype sname] subtype
                              rs (get-subtype-value-summary
                                   CGIS {:institute institute
                                         :cancer-type cancer-type
                                         :subtype stype
                                         :subtype-name sname})]
                          (->> (map vals rs)
                               (filter second)
                               (map #(concat [institute cancer-type "value"] %)))))
            tuples (->> subtype-specs
                        (map (fn [[i specs]]
                               (reduce (fn [res [k v]]
                                         (reduce into res (map #(mk-tuples (name i) (name k)  %) v)))
                                       [] specs)))
                        (apply concat))]
        (insert-subtype-values! CGIS {:subtype-vals tuples})))

  (get-subtype-values CGIS {:institute "tcga" :cancer-type "thym"})
  (get-subtype-values CGIS {:institute "smc" :cancer-type "luad"})
  (get-subtype-values CGIS {:institute "tcga" :cancer-type "luad"})
  (get-subtype-values CGIS {:institute "tcga" :cancer-type "pancan"}))


(hugsql/def-db-fns "sql/expressions.sql")
#_(hugsql/def-sqlvec-fns "sql/expressions.sql")

(comment
  "Test queries for expression signature"

  (count (get-cohort-exp-values
           CGIS {:institute "tcga" :cancer-type "skcm" :genes ["TP53", "RB1"]}))

  (get-cohort-exp-values-user CGIS {:uuid "52b63e20-fed0-4990-9129-23c3fa0049ba" :genes ["KRAS", "RB1"]})

  (do (assert (= 222 (count (get-cohort-exp-values
                              CGIS {:institute "smc" :cancer-type "luad" :genes ["TP53", "RB1"]}))))
      (assert (= 1030 (count (get-cohort-exp-values
                               CGIS {:institute "tcga" :cancer-type "luad" :genes ["TP53", "RB1"]}))))
      (assert (= 1012 (count (get-cohort-prognostic-values
                               CGIS {:institute "tcga" :cancer-type "luad" :genes ["TP53", "RB1"]}))))))


(hugsql/def-db-fns "sql/mutations.sql")
#_(hugsql/def-sqlvec-fns "sql/mutations.sql")

(comment
  "Test quries for mutational patterns"

  (count (get-mutation-list
           CGIS {:institute "tcga" :cancer-type "skcm" :genes ["TP53" "RB1"]}))

  (get-mutation-list-user CGIS {:uuid "52b63e20-fed0-4990-9129-23c3fa0049ba" :genes ["KRAS" "RB1"]})

  (do (assert (= 43 (count (get-mutation-list
                             CGIS {:institute "smc" :cancer-type "luad" :genes ["TP53" "RB1"]}))))
      (assert (= 361 (count (get-mutation-list
                              CGIS {:institute "tcga" :cancer-type "luad" :genes ["TP53" "RB1"]}))))
      (assert (= 254 (count (get-mutation-list
                              CGIS {:institute "tcga" :cancer-type "stad" :genes ["TP53" "RB1"]})))))

  (get-subtype-list-sqlvec {:institute "tcga"
                            :cancer-type "luad"
                            :subtypes (get-in subtype-specs [:tcga :luad])})

  (let [rs (get-subtype-list CGIS {:institute "tcga"
                                   :cancer-type "luad"
                                   :subtypes (get-in subtype-specs [:tcga :luad])}
                             {} {:keywordize? false :identifiers identity})]
    (assert (= 490 (count rs))) rs))


(hugsql/def-db-fns "sql/main.sql")
#_(hugsql/def-sqlvec-fns "sql/main.sql")

(comment
  "Test main queries"

  (get-valid-gene-symbols CGIS {:genes ["ABC" "BRCA1" "BRCA2"]})
  (get-valid-gene-symbols CGIS {:genes #{"ABC" "BRCA1" "CDE"}})
  
  (assert (= 102 (count (get-msigdb-suggestions CGIS {:like "%EGFR%" :limit 50}))))
  (assert (= 33 (count (get-msigdb-suggestions CGIS {:like "%EGFR%" :limit 20})))))

