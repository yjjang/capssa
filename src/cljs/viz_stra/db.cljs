(ns viz-stra.db
  (:require [re-frame.core :as re-frame]))

(def default-db
  {:name "CaPSSA"
   :description
   "Visual evaluation of cancer biomarker genes for patient stratification and survival analysis using mutation and expression data"
   :active-panel :home-panel
   :selected-geneset-id 1
   :genesets (into (sorted-map)
                   {1 {:id 1
                       :name "RB Pathway"
                       :genes ["CDKN2A" "CDKN2B" "CDK4" "RB1"]
                       :desc ""
                       :user? false}
                    2 {:id 2
                       :name "PI(3)K signaling"
                       :genes ["PIK3CA" "PIK3R1" "PTEN" "EGFR" "PDGFRA" "NF1"]
                       :desc ""
                       :user? false}
                    3 {:id 3
                       :name "OncotypeDX genes"
                       :genes ["AURKA" "BAG1" "BCL2" "BIRC5" "CCNB1" "CD68" "CTSL2" "ERBB2"
                               "ESR1" "GRB7" "GSTM1" "MKI67" "MMP11" "MYBL2" "PGR" "SCUBE2"]
                       :desc ""
                       :user? false}
                    4 {:id 4
                       :name "Boutros et al. PNAS 2009"
                       :genes ["STX1A" "HIF1A" "CCT3" "HLA-DPB1" "RNF5" "MAFK"]
                       :desc ""
                       :user? false}
                    5 {:id 5
                       :name "Chen et al. NEJM 2007"
                       :genes ["DUSP6" "MMD" "STAT1" "ERBB3" "LCK"]
                       :desc ""
                       :user? false}})
   :selected-cohort-id 13
   :cohorts (into (sorted-map)
                  {1 {:id 1
                      :code "blca"
                      :name "Bladder Urothelial Carcinoma (BLCA)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-BLCA"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   2 {:id 2
                      :code "brca"
                      :name "Breast Invasive Carcinoma (BRCA)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-BRCA"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   3 {:id 3
                      :code "cesc"
                      :name "Cervical Cancer (CESC)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-CESC"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   4 {:id 4
                      :code "coad"
                      :name "Colon Adenocarcinoma (COAD)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-COAD"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   5 {:id 5
                      :code "esca"
                      :name "Esophageal Carcinoma (ESCA)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-ESCA"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   6 {:id 6
                      :code "gbm"
                      :name "Glioblastoma Multiforme (GBM)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-GBM"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   7 {:id 7
                      :code "hnsc"
                      :name "Head and Neck Squamous Cell Carcinoma (HNSC)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-HNSC"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   8 {:id 8
                      :code "kirc"
                      :name "Kidney Renal Clear Cell Carcinoma (KIRC)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-KIRC"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   9 {:id 9
                      :code "kirp"
                      :name "Kidney Renal Papillary Cell Carcinoma (KIRP)"
                      :link "https://portal.gdc.cancer.gov/projects/TCGA-KIRP"
                      :institute "tcga"
                      :group "The Cancer Genome Atlas (TCGA)"}
                   10 {:id 10 
                       :code "laml"
                       :name "Acute Myeloid Leukemia (LAML)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-LAML"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   11 {:id 11 
                       :code "lgg"
                       :name "Brain Lower Grade Glioma (LGG)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-LGG"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   12 {:id 12 
                       :code "lihc"
                       :name "Liver Hepatocellular Carcinoma (LIHC)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-LIHC"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   13 {:id 13
                       :code "luad"
                       :name "Lung Adenocarcinoma (LUAD)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-LUAD"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   14 {:id 14
                       :code "lusc"
                       :name "Lung Squamous Cell Carcinoma (LUSC)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-LUSC"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   15 {:id 15
                       :code "ov"
                       :name "Ovarian Serous Cystadenocarcinoma (OV)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-OV"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   16 {:id 16
                       :code "paad"
                       :name "Pancreatic Adenocarcinoma (PAAD)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-PAAD"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   17 {:id 17
                       :code "pcpg"
                       :name "Pheochromocytoma and Paraganglioma (PCPG)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-PCPG"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   18 {:id 18
                       :code "prad"
                       :name "Prostate Adenocarcinoma (PRAD)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-PRAD"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   19 {:id 19
                       :code "read"
                       :name "Rectum Adenocarcinoma (READ)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-READ"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   20 {:id 20
                       :code "sarc"
                       :name "Sarcoma (SARC)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-SARC"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   21 {:id 21
                       :code "skcm"
                       :name "Skin Cutaneous Melanoma (SKCM)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-SKCM"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   22 {:id 22
                       :code "stad"
                       :name "Stomach Adenocarcinoma (STAD)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-STAD"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   23 {:id 23
                       :code "tgct"
                       :name "Testicular Germ Cell Tumors (TGCT)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-TGCT"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   24 {:id 24
                       :code "thca"
                       :name "Thyroid Carcinoma (THCA)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-THCA"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   25 {:id 25
                       :code "thym"
                       :name "Thymoma (THYM)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-THYM"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   26 {:id 26 
                       :code "ucec"
                       :name "Uterine Corpus Endometrial Carcinoma (UCEC)"
                       :link "https://portal.gdc.cancer.gov/projects/TCGA-UCEC"
                       :institute "tcga"
                       :group "The Cancer Genome Atlas (TCGA)"}
                   51 {:id 51
                        :code "luad"
                        :name "Lung Adenocarcinoma (SMCLUAD)"
                        :institute "smc"
                        :group "Samsung Medical Center, Korea."}})
   ;; Clinical data for each cohort i.e. {cohort-id {...clinicals...}}
   :clinical-data {}
   :alert-list []
   :data-loading? false})

;; inspect the contents of app-db
#_(let [app-db (deref re-frame.db/app-db)]
    (js/console.log (get-in app-db [:mutation :landscape-data]))
    (js/console.log (get-in app-db [:expression :signature-data]))
    (js/console.log (get-in app-db [:expression :cluster-data]))
    ;(-> app-db :cohorts)
    )

