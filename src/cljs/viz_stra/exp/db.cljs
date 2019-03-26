(ns viz-stra.exp.db)

(def default-db
  {:expression {:active-panel :risk-panel
                ;; json-format data used in InCHlib
                ;; i.e. {geneset-id {cohort-id json-data}}
                :cluster-data {}
                ;; same above, but now for signature drawing
                :signature-data {}
                :cluster-sub-plot :surv-plot
                :risk-sub-plot :gene-box-plot}})

