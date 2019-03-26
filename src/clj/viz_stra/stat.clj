(ns viz-stra.stat
  (:import org.apache.commons.math3.distribution.ChiSquaredDistribution
           javastat.survival.regression.CoxRegression))

(defn chisq->p [score] ; String typed score
  (let [cumul-prob (-> (ChiSquaredDistribution. 1)
                       (.cumulativeProbability (Float/parseFloat score)))
        p-value (- 1 cumul-prob)]
    (Double/toString p-value)))

(defn- double-array->list [arr]
  (map #(aget ^doubles arr %) (range (alength arr))))

(defn cox-regression
  [times censors covariates] ; Clojure sequences
  (let [times (double-array times)
        censors (double-array censors)
        covariates (into-array (map double-array covariates))
        cox (CoxRegression. times censors covariates)]
    {:coefficients (double-array->list (.coefficients cox))
     :z-statistics (double-array->list (.testStatistic cox))
     :p-values (double-array->list (.pValue cox))}))

