(ns viz-stra.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [re-com.tour :as re-tour]
            [reforms.reagent :include-macros true :as f]
            [reforms.validation :include-macros true :as v]
            [clojure.string :as string]
            [cljs.core.async :refer [<! timeout]]
            [ajax.core :refer [POST]]
            [viz-stra.db :as d]
            [viz-stra.events :as e]
            [viz-stra.subs :as s]
            [viz-stra.exp.views :as exp-views]
            [viz-stra.exp.events :as exp-evts]
            [viz-stra.mut.views :as mut-views]
            [viz-stra.mut.events :as mut-evts]
            [viz-stra.comp :refer [Button Navbar Navbar-Header Navbar-Brand Nav NavItem
                                   NavDropdown MenuItem Glyphicon Fade
                                   modify-geneset-form]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


;; -- Home -----------------------------------------------------

(defn home-title []
  (let [name (re-frame/subscribe [::s/name])
        description (re-frame/subscribe [::s/description])]
    [re-com/v-box
     :children [[re-com/title :label @name :level :level1]
                [re-com/title :label @description :level :level2]]]))

(defn home-panel []
  [re-com/v-box
   :gap "20px"
   :children [[home-title]
              [re-com/h-box
               :gap "10px"
               :children [[Button {:bs-style "danger"
                                   :on-click #(re-frame/dispatch
                                                [::e/set-active-panel :upload-panel])}
                           "Go"]
                          [re-com/button
                           :label "Help"
                           :class "btn-primary"
                           :on-click #(re-frame/dispatch
                                        [::e/set-active-panel :help-panel])]]]
              [re-com/alert-box
               :heading "Browser compatibility:"
               :alert-type :info
               :style {:width "800px"}
               :body [:span
                      (deref (re-frame/subscribe [::s/name]))
                      " is multi-platform and works in all modern web browsers"
                      " (i.e. Firefox, IE, Chrome, Safari, Opera), but we recommend "
                      [:a {:href "http://www.google.com/chrome" :target "_blank"} "Google Chrome"]
                      " for the smoothest user experience."]]]])


;; -- Upload ---------------------------------------------------

#_(POST "/geneset"
        {:format :json
         :response-format :json
         :params {:genes ["TP53" "RB1" "ABCD" "BRCA1" "CDE"]}
         :handler #(do (println (% "valid"))
                       (println (% "unknown")))})

#_(filter (complement empty?) (string/split "ab,, bc" #"[\s+,]"))

(def geneset-to-upload (reagent/atom {}))

(defn- upload-geneset! [geneset ui-state]
  (when (v/validate!
          geneset
          ui-state
          (v/present [:name] "Enter the name of geneset!")
          (v/present [:genes] "You should provide a list of gene symbols!"))
    (let [gs (-> @geneset
                 (update :genes #(filter (complement empty?) (string/split % #"[\s+,]"))))]
      (println "Uploading a geneset : " gs)
      (swap! ui-state assoc :doing? true)
      (POST "/geneset"
            {:format :json
             :response-format :json
             :params {:genes (:genes gs)}
             :handler #(let [valids (% "valid")
                             unknowns (% "unknown")]
                         (if (empty? valids)
                           (re-frame/dispatch
                             [::e/add-alert {:alert-type :danger
                                             :heading "Can not upload a gene set."
                                             :body "No valid gene symbols."}])
                           (do (re-frame/dispatch
                                 [::e/add-a-geneset
                                  (-> gs (assoc :genes valids) (assoc :unknowns unknowns))])
                               (reset! geneset {})))
                         (swap! ui-state assoc :doing? false))
             :error-handler (fn [{:keys [status status-text]}]
                              (re-frame/dispatch
                                [::e/add-alert {:alert-type :danger
                                                :heading status
                                                :body status-text}])
                              (swap! ui-state assoc :doing? false))}))))

(def eg-steps (re-com/make-tour [:upload :geneset :cohort :analyses]))

(defn upload-form []
  (let [ui-state (reagent/atom {})]
    (fn []
      (f/panel
        {:style {:width "800px"}}
        "Upload a gene set"
        (f/with-options {:form {:horizontal true}}
          (v/form ui-state
                  {:on-submit #(upload-geneset! geneset-to-upload ui-state)}
                  (v/text "Name" geneset-to-upload [:name]
                          :placeholder "A name of the gene set")
                  (v/text "Description (optional)" geneset-to-upload [:desc]
                          :placeholder "Description of the gene set")
                  (v/textarea {:rows 8}
                              "Genes" geneset-to-upload [:genes]
                              :placeholder "Space, comma or new-line separated gene symbols")
                  (f/form-buttons
                    (f/button-primary {:style {:margin-top "-4px"}}
                                      "Upload"
                                      #(upload-geneset! geneset-to-upload ui-state)
                                      :in-progress (:doing? @ui-state)
                                      :disabled (:doing? @ui-state))
                    [re-com/popover-anchor-wrapper
                     :showing? (:upload eg-steps)
                     :position :below-center
                     :anchor (f/button-default
                               "Example"
                               #(do (swap! geneset-to-upload assoc :name "KRAS/CDKN2AB/NKX2-1")
                                    (swap! geneset-to-upload assoc :genes "KRAS CDKN2A CDKN2B NKX2-1")
                                    (re-com/start-tour eg-steps)))
                     :style {:margin-left "10px"}
                     :popover [re-com/popover-content-wrapper
                               :width "350px"
                               :close-button? false
                               :on-cancel #(re-tour/finish-tour eg-steps)
                               :backdrop-opacity 0.5
                               :title [:strong "An example set of genes"]
                               :body [:div "So, you entered a set of genes. Click the '"
                                      [:strong "Next"] "' button to upload and proceed to the next step."
                                      [re-com/make-tour-nav eg-steps]]]])))))))

#_(POST "/msig" {:format :json
                 :response-format :json
                 :params {:word "p53"}
                 :handler #(println "OK")
                 :error-handler #(println "ERROR")})

(defn msigdb-suggestion-form []
  (let [input-status (reagent/atom nil)]
    [re-com/typeahead
     :data-source (fn [word callback]
                    (POST "/msig"
                          {:format :json
                           :response-format :json
                           :params {:word word}
                           :handler #(do (callback %)
                                         (reset! input-status nil))
                           :error-handler (fn [{:keys [status status-text]}]
                                            (re-frame/dispatch
                                              [::e/add-alert {:alert-type :danger
                                                              :heading status
                                                              :body status-text}])
                                            (reset! input-status :error))})
                    nil)
     :on-change #(let [name (get % "name")
                       link (get % "link")
                       genes (get % "genes")]
                   (swap! geneset-to-upload assoc :name (apply str (take 20 name)))
                   (swap! geneset-to-upload assoc :desc "Gene set from MSigDB")
                   (swap! geneset-to-upload assoc :link link)
                   (swap! geneset-to-upload assoc :genes (string/join "\n" genes)))
     :change-on-blur? true
     :suggestion-to-string #(get % "name")
     :render-suggestion #(let [name (get % "name")
                               link (get % "link")]
                           [:span [re-com/hyperlink-href
                                   :label [:i {:style {:width "20px"} :class "zmdi zmdi-link"}]
                                   :href link
                                   :target "_blank"
                                   :tooltip "Link to the gene set in MSigDB"
                                   :attr {:on-mouse-down (fn [e] (.stopPropagation e))}]
                            (apply str (take 43 name))])
     :status @input-status
     :debounce-delay 1000
     :rigid? true
     :width "400px"
     :placeholder "Type a name of gene or pathway to search in MSigDB"]))


(defn- upload-geneset-panel []
  [re-com/h-box
   :gap "10px"
   :children [[re-com/v-box
               :gap "10px"
               :children [[upload-form]
                          [re-com/alert-box
                           :heading "For first-time users:"
                           :alert-type :info
                           :style {:width "800px"}
                           :body [:span  "Click the '" [:strong "Example"] "' button. "
                                  "It will show you how to start an analysis in "
                                  (deref (re-frame/subscribe [::s/name]))
                                  " using an example set of genes by walking you through it step by step."]]]]
              [re-com/v-box
               :children [[:h4 {:style {:margin-top "0px"}}
                           "Import gene sets from "
                           [re-com/hyperlink-href
                            :label "MSigDB"
                            :href "http://software.broadinstitute.org/gsea/msigdb"
                            :target "_blank"
                            :tooltip "MSigDB Home"]
                           ": "]
                          [msigdb-suggestion-form]
                          [:span {:style {:color "grey"}} "* Press ESC key to reset"]]]]])

(defn- data-desc [data-type]
  (case data-type
    :alters
    [re-com/v-box
     :children
     [[:p.info-heading "Data format for genomic changes"]
      [:p "You can select a file with " [:span.info-bold {:style {:color "pink"}} "somatic mutations"] " and "
       [:span.info-bold {:style {:color "pink"}} "CNVs"] " for upload."]
      [:p "Mutation files should have " [:span.info-bold "comma-separated"] " or " [:span.info-bold "tab-delimited"]
       " " [:span.info-bold "three"] " columns without header line."]
      [:p "Each rows should have values for the followings in the given order: "]
      [:ol
       [:li [:span.info-bold {:style {:color "yellow"}} "Patient"] " : Patient ID"]
       [:li [:span.info-bold {:style {:color "yellow"}} "Gene"] " : HUGO gene symbol"]
       [:li [:span.info-bold {:style {:color "yellow"}} "Alteration"]
        " : An alteration event should be one of the following definitions: "
        [:ul
         [:li "For CNVs, "
          [:ul
           [:li [:span.info-bold "Amplification"]]
           [:li [:span.info-bold "Homozygous_Deletion"]]]]
         [:li "For somatic matations, "
          [:ul
           [:li [:span.info-bold "Nonsense_Mutation"]]
           [:li [:span.info-bold "Splice_site"]]
           [:li [:span.info-bold "Translation_Start_Site"]]
           [:li [:span.info-bold "Missense_Mutation"]]
           [:li [:span.info-bold "Nonstop_Mutation"]]
           [:li [:span.info-bold "Frame_Shift_Ins"] ", " [:span.info-bold "Frame_Shift_Del"]]
           [:li [:span.info-bold "In_Frame_Ins"] ", " [:span.info-bold "In_Frame_Del"]]
           [:li [:span.info-bold "RNA"] ", " [:span.info-bold "lincRNA"]]
           [:li [:span.info-bold "3'Flank"] ", " [:span.info-bold "3'UTR"] ", "
            [:span.info-bold "5'Flank"] ", " [:span.info-bold "5'UTR"]]
           [:li [:span.info-bold "Silent"]]]]]]]
      [:p "An example is below:"]
      [:code
       "Pat01	MIR1253	Homozygous_Deletion" [:br]
       "Pat01	UBA7	Missense_Mutation" [:br]
       "Pat01	AQP8	Silent" [:br]
       "Pat01	OXR1	Amplification" [:br]
       "Pat01	EGFR	In_Frame_Del" [:br]
       "Pat01	PSMA7	Nonsense_Mutation" [:br]]]]
    :exp
    [re-com/v-box
     :children
     [[:p.info-heading "Data format for gene expressions"]
      [:p "You can select a file with " [:span.info-bold {:style {:color "pink"}} "gene expressions"] " for upload."]
      [:p "Expression files should have " [:span.info-bold "comma-separated"] " or " [:span.info-bold "tab-delimited"]
       " " [:span.info-bold "three"] " columns without a header line."]
      [:p "Each rows should have values for the followings in the given order: "]
      [:ol
       [:li [:span.info-bold {:style {:color "yellow"}} "Paient"] " : Patient ID"]
       [:li [:span.info-bold {:style {:color "yellow"}} "Gene"] " : HUGO gene symbol"]
       [:li [:span.info-bold {:style {:color "yellow"}} "Estimated count"]
        " : Normalized read count. e.g. " [:span.info-bold "FPKM"] " or " [:span.info-bold "TPM"]]]
      [:p "An example is below:"]
      [:code
       "Pat01	ADAM33	3.02" [:br]
       "Pat01	ACOX3	24.51" [:br]
       "Pat01	ABI3	9.37" [:br]
       "Pat01	ADPRHL2	33.82" [:br]
       "Pat01	ADAM32	0.4" [:br]
       "Pat01	ACSL1	123.77" [:br]]]]
    :clinical
    [re-com/v-box
     :children
     [[:p.info-heading "Data format for clinical information"]
      [:p "You can select a file with " [:span.info-bold {:style {:color "pink"}} "clinical information"] " for upload."]
      [:p "Files should have " [:span.info-bold "comma-separated"] " or " [:span.info-bold "tab-delimited"]
       " arbitrary number of columns " [:span.info-bold "with headers"] "."]
      [:p "Headers can have any names you want. Exceptionally, "
       [:span.bold " header names holding patient IDs and survival information must be one of the followings: "]]
      [:ol
       [:li [:span.info-bold {:style {:color "yellow"}} "participant_id"] " : Patient ID"]
       [:li [:span.info-bold {:style {:color "yellow"}} "os_days"] " : Overall survival days"]
       [:li [:span.info-bold {:style {:color "yellow"}} "os_status"]
        " : Indication if a patient's overall survival time is cencored"]
       [:li [:span.info-bold {:style {:color "yellow"}} "dfs_days"] " : Disease-free survival days"]
       [:li [:span.info-bold {:style {:color "yellow"}} "dfs_status"]
        " : Indication if a patient's disease-free survival time is cencored"]]
      [:p "An example is below:"]
      [:code
       "participant_id	os_days	os_status	dfs_days	dfs_status	smoke	subtype" [:br]
       "Pat01	1567	0	393	0	No	Magnoid" [:br]
       "Pat02	1506	0	1500	0	No	Squamoid" [:br]
       "Pat03	708	1	511	1	No	Bronchioid" [:br]
       "Pat04	1470	0	1171	1	No	Squamoid" [:br]
       "Pat05	959	1	339	1	No	Bronchioid" [:br]]]]))

(defn- upload-btn [up-files data-type]
  (let [file (@up-files data-type)]
    [re-com/v-box
     :style {:background (case data-type :clinical "#F9F9F9" "#F0F0F0") :padding-left "10px"}
     :gap "10px"
     :children
     [[re-com/gap :size "10px"]
      [:h4 (case data-type
             :alters "Somatic mutations and CNVs"
             :exp "Gene expressions"
             :clinical "Clinical information")
       [re-com/info-button
        :width "500px"
        :style {:color "blue" :margin-left "5px"}
        :info [data-desc data-type]]]
      [re-com/h-box
       :children
       [[:label.semibold {:for (str "upload" data-type) :style {:cursor "pointer"}}
         [re-com/h-box
          :gap "10px"
          :children
          [[:input.hidden
            {:type "file"
             :id (str "upload" data-type)
             :accept "*.csv"
             :on-change #(let [target (.-currentTarget %)
                               f (-> target .-files (aget 0))]
                           (set! (.-value target) "")
                           (swap! up-files assoc data-type f))}]
           [:i.fa.fa-upload.fa-lg]
           [re-com/label :label (if file (.-name file) " Click here to upload a csv ...")]]]]
        [re-com/gap :size "5px"]
        (if file
          [:i.fa.fa-times {:style {:color "red" :cursor "pointer"}
                           :on-click #(swap! up-files assoc data-type nil)}]
          [:span
           [:i {:style {:margin-left "15px" :margin-right "5px"} :class "zmdi zmdi-file-text"}]
           [re-com/hyperlink-href
            :label "example"
            :href (case data-type
                    :alters "data/alteration_sample.csv"
                    :exp "data/expression_sample.csv"
                    :clinical "data/clinical_sample.csv")
            :target "_blank"
            :tooltip "Download an example csv"]])]]]]))

(defn- upload-cohort-panel [up-files]
  (let [cohort-name (reagent/atom "")]
    (fn []
      [re-com/v-box
       :width "400px"
       :gap "10px"
       :children
       [[upload-btn up-files :alters]
        [upload-btn up-files :exp]
        [upload-btn up-files :clinical]
        [re-com/gap :size "10px"]
        [re-com/h-box
         :gap "10px"
         :align :center
         :children
         [[re-com/label :label "Cohort name: "]
          [re-com/input-text
           :width "300px"
           :placeholder "Enter a display name here."
           :model cohort-name
           :change-on-blur? false
           :on-change #(reset! cohort-name %)]]]
        [re-com/gap :size "20px"]
        [re-com/button
         :label [:span "Upload " [:i.zmdi.zmdi-hc-fw-rc.zmdi-upload]]
         :disabled? (not (and (or (@up-files :alters) (@up-files :exp)) (not-empty @cohort-name)))
         :on-click
         #(let [cohort-desc {:id (-> @(re-frame/subscribe [::s/cohort-ids])
                                     last
                                     ((fn [id] (inc (if (> id 100) id 100)))))
                             :uuid (str (random-uuid))
                             :name @cohort-name}]
            (re-frame/dispatch [::e/add-a-cohort cohort-desc])
            (doseq [[type file] @up-files]
              (when file
                (js/Papa.parse
                  file
                  #js {:header (case type :clinical true false)
                       :dynamicTyping true
                       :skipEmptyLines "greedy"
                       :complete
                       (fn [result]
                         (let [data (.-data result)]
                           (re-frame/dispatch [::e/store-cohort-data cohort-desc type data])))})))
            (reset! up-files {:alters nil :exp nil :clinical nil})
            (reset! cohort-name ""))
         :style {:color "white"
                 :background-color "#4d90fe"
                 :font-size "18px"
                 :font-weight "250"
                 :border "none"
                 :border-radius "0px"
                 :padding "6px 10px"}]]])))

(def active-upload-panel (reagent/atom :geneset))
(def upload-files (reagent/atom {:alters nil :exp nil :clinical nil}))

(defn upload-panel []
  [re-com/v-box
   :height "100%"
   :children
   [[Nav {:bs-style "tabs" :active-key @active-upload-panel
          :on-select #(when % (reset! active-upload-panel (keyword %)))}
     [NavItem {:event-key "geneset"} "Gene set"]
     [NavItem {:event-key "cohort"} "Cohort data"]]
    [re-com/gap :size "10px"]
    (case @active-upload-panel
      :geneset [upload-geneset-panel]
      :cohort [upload-cohort-panel upload-files])]])

;; -------------------------------------------------------------


;; -- Help -----------------------------------------------------

(defn help-title []
  [re-com/title
   :label "Supplementary information and demo videos"
   :level :level1])

(defn help-panel []
  [re-com/v-box
   :gap "1em"
   :children [[help-title]
              [re-com/p "Yeongjun Jang <yjjang2050@gmail.com>"]
              [:h4 {:style {:margin-top "0px"}}
               "** Download a supplementary document "
               [re-com/hyperlink-href
                :label "here."
                :href "data/CaPSSA_Supplementary.pdf"
                :target "_blank"
                :tooltip "Supplementray information"]]

              [:h3 "1. Enter a gene set"]
              [re-com/p {:style {:width "800px"}}
               "You can upload a set of genes of interest to be used for the analysis via the page labeled 'Upload'. One can paste one or more gene symbols separated by either whitespace, new line, or comma in the text area."]
              [:iframe {:src "https://www.youtube.com/embed/wGJb9clF_Ss?rel=0&vq=hd720"
                        ;:allow "accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "2. An example set of genes for case study"]
              [re-com/p {:style {:width "800px"}}
               "This demo shows you how to start an analysis in CaPSSA using an example set of genes by walking you through it step by step."]
              [:iframe {:src "https://www.youtube.com/embed/xkp5-bS-KKw?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "3. Import pathway genes from MsigDB"]
              [re-com/p {:style {:width "800px"}}
               "You can import gene sets by searching a name of gene or pathway in "
               [re-com/hyperlink-href
                :label "MSigDB"
                :href "http://software.broadinstitute.org/gsea/msigdb"
                :target "_blank"
                :tooltip "MSigDB Home"]
               "."]
              [:iframe {:src "https://www.youtube.com/embed/QsnTDZMP1MA?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "4. Gene set management"]
              [re-com/p {:style {:width "800px"}}
               "You can modify or delete imported gene sets."]
              [:iframe {:src "https://www.youtube.com/embed/Er35YaOkqTE?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "5. Pattern of genomic alterations across patients"]
              [re-com/p {:style {:width "800px"}}
               "A heatmap in the center depicts distinct color-coded genetic alterations, including somatic mutations or copy number alterations across patients. Individual genes and patients are represented as rows and columns, respectively. The order of genes can be changed by dragging a gene name into the desired position."]
              [re-com/p {:style {:width "800px"}}
               "Each bar above the alteration heatmap shows a representative clinical feature (i.e., Expression Subtype). The user can sort patients by their clinical values by clicking the feature’s name."]
              [re-com/p {:style {:width "800px"}}
               "Horizontally and vertically stacked bars on the left and top of the heatmap indicate the number of alterations and their color-coded types across patients and genes."]
              [:iframe {:src "https://www.youtube.com/embed/_kn_dykHu1A?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "6. Patient subgrouping based on genomic alterations"]
              [re-com/p {:style {:width "800px"}}
               "Users may define patient subgroups according to genomic alterations such as somatic mutations and copy number alterations. Patterns of mutually exclusive or co-occurring alterations can be immediately recognized to define patient subgroups and exploring survival characteristics."]
              [re-com/p {:style {:width "800px"}}
               "The user can interactively define patient groups, based on combinations of alteration patterns between genes, via checkbox UIs beside the gene symbol labels. Check-boxes in the first column, tagged by ‘G’ (Grouping), are for choosing whether to designate patients with alterations in a given gene into an alteration group. Checkboxes tagged by ‘F’ (Filtering) are for choosing whether to display patients having alterations in a given gene only, or all the patients. Each alteration group is visually divided by a vertical dashed line."]
              [re-com/p {:style {:width "800px"}}
               "In this example, patients were separated by KRAS mutations that possess CDKN2A/B inactivation by homozygous deletion versus patients with KRAS mutations having wildtype CDKN2A/B locus."]
              [re-com/p {:style {:width "800px"}}
               "A Kaplan-Meier plot is highlighting the significance of prognostic genes in a given cancer type (in this case, TCGA Lung Adenocarcinoma) based on overall survival. Log-rank p-values are shown in the upper right corner of the Kaplan-Meier plot. The red and blue colors of the lines indicate altered and unaltered patient groups in selected genes, respectively."]
              [:iframe {:src "https://www.youtube.com/embed/B8Jq0sdPx-4?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "7. Coherence of differentially altered groups to known subtypes"]
              [re-com/p {:style {:width "800px"}}
               "This demo shows how concordant the stratification results are with known molecular or clinical sub-groups, such as PAM50 subtype in breast cancer or the expression subtype in lung and brain cancer."]
              [re-com/p {:style {:width "800px"}}
               "Patients are sub-grouped by selected clinical feature (in this case, by clicking “Expression Subtype”), then ordered again based on mutational patterns in each sub-group."]
              [re-com/p {:style {:width "800px"}}
               "In the right panel, a Sankey plot shows how concordant the stratification results are with known molecular or clinical subtypes. In each stacked box on each side, a subgroup is encoded by a box whose height is proportional to the number of patients within that subgroup. Colors of the boxes indicate corresponding subgroups. On the left side, the red/light-blue/grey bars represent patients with alterations, patients without alterations initially loaded, and patients without alterations not initially loaded, respectively. On the right side, the blue/yellow/purple bars indicate three groups of expression subtypes (Bronchoid, Magnoid, and Squamoid) in TCGA Lung Adenocarcinoma, which is consistent with the color of the sub-group shown in the bars for clinical features on the main panel. The bands connecting boxes represent matched patients in different groups. The width of the bands is proportional to the number of patients."]
              [:iframe {:src "https://www.youtube.com/embed/KJIZhnPliDY?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "8. Risk groups stratified using gene expression data"]
              [re-com/p {:style {:width "800px"}}
               "Alternatively, patients can be divided into the high and low risk groups where the risk score for each patient is calculated from gene expression data using the Cox proportional hazards model or centroid distance to the good prognostic patient group."]
              [re-com/p {:style {:width "800px"}}
               "Red and green colors in the following plots represent patient groups with high and low risk scores, respectively. Sample names and associated values can be visualized by placing the cursor over any part of the plot. Prognosis of each risk group of patients was examined by Kaplan-Meier survival estimators, and the survival outcomes of different groups were compared by log-rank tests."]
              [re-com/p {:style {:width "800px"}}
               "Lower heatmap depicts normalized and log2 transformed TPM (transcripts per million) of putative bio-marker genes, estimated by RSEM (Li and Dewey 2011) from low to high expression (green to red) across patients with TCGA Lung Adenocarcinoma; A scatter plot shows the vitality status and survival days of patients."]
              [re-com/p {:style {:width "800px"}}
               "In this example, a waterfall plot represents ordered risk scores calculated using prognostic index by Cox regression. Bar colors in the waterfall plot represent clinical values of a chosen feature, in this case “Expression Subtype”. To choose a group with significant risk, patients with risk score in upper 10th percentiles are classified into the high-risk group and the remaining patients into the low-risk group. This is accomplished by dragging arrows on both ends of the vertical division line."]
              [:iframe {:src "https://www.youtube.com/embed/CrRfT62qp_o?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "9. Additional outputs of gene expression-based risk estimation"]
              [re-com/p {:style {:width "800px"}}
               "In the order in which it appears on the video,"]
              [re-com/p {:style {:width "800px"}}
               "A box plot shows differential expression levels of the genes in low and high risk groups. The user can correlate the expression direction of each gene with patient survival."]
              [re-com/p {:style {:width "800px"}}
               "A box plot represents relative expression of genes in each clinical sub-group of low and high risk groups."]
              [re-com/p {:style {:width "800px"}}
               "A Sankey plot shows the coherence of stratified patients with other clinical subtypes such as expression subtype, race, or smoking status, where applicable."]
              [re-com/p {:style {:width "800px"}}
               "Cox regression outputs in tabular form. Negative regression coefficients indicate favorable prognostic genes for which higher expression of a given gene is correlated with longer patient survival outcome. Positive regression coefficients indicate unfavorable prognostic genes for which higher expression of a given gene is correlated with poor patient survival outcome."]
              [:iframe {:src "https://www.youtube.com/embed/PqwGqHhwJtY?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "10. Hierarchical clustering using gene expression data"]
              [re-com/p {:style {:width "800px"}}
               "We also support users to define a patient subgroup from hierarchical clustering and explore the survival characteristics and their association with specific clinical parameters."]
              [re-com/p {:style {:width "800px"}}
               "The results of hierarchical clustering are shown as a tree structure called a dendrogram. The dendrogram shows the arrangement of individual clusters, a heatmap that is used for visualizing gene expression patterns in a grid panel (rows and columns represent genes and patients, respectively), and upper bars showing color-coded clinical features."]
              [re-com/p {:style {:width "800px"}}
               "Interactive zoom-in-out of a cluster enables choosing particular sub-clusters. Clicking the magnifier icon in the upper right corner of the heatmap panel allows the user to choose a sub-group with a more significant association with survival outcome."]
              [re-com/p {:style {:width "800px"}}
               "When a patient (column) cluster (dendrogram) is selected, a KM-plot displays survival difference between patients (red color) in the cluster and other patients (grey color) in the cohort."]
              [:iframe {:src "https://www.youtube.com/embed/BdsIAZx3ZSA?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]

              [:h3 "11. Additional outputs of gene expression-based hierarchical clustering"]
              [re-com/p {:style {:width "800px"}}
               "Once a patient cluster is selected, in addition to Kaplan-Meier plot, three additional plots are available on the right panel. These include two box plots of gene expression values across risk groups for each gene and clinical sub-group, and a Sankey plot showing visual coherence of available clinical information with clustered groups."]
              [:iframe {:src "https://www.youtube.com/embed/t7Zj6QO1fhk?rel=0&vq=hd720"
                        :width 800 :height 450 :frameborder 0 :allowFullScreen true}]
              ]])


;; -- Main ------------------------------------------------------

(defn- panels [active-panel]
  (case @active-panel
    :home-panel [home-panel]
    :upload-panel [upload-panel]
    :mutation-panel [mut-views/main-panel]
    :expression-panel [exp-views/main-panel]
    :help-panel [help-panel]
    [:div]))

(def geneset-to-delete (reagent/atom nil))
(def geneset-to-edit (reagent/atom nil))
(def cohort-to-delete (reagent/atom nil))

(defn- top-navbar [active-panel]
  (let [brand @(re-frame/subscribe [::s/name])
        default-genesets @(re-frame/subscribe [::s/default-genesets])
        user-genesets @(re-frame/subscribe [::s/user-genesets])
        default-cohorts @(re-frame/subscribe [::s/default-cohorts])
        user-cohorts @(re-frame/subscribe [::s/user-cohorts])]
    [Navbar {:inverse true :fluid true}
     [Navbar-Header
      [Navbar-Brand [:a {:href "#"
                         :on-click #(re-frame/dispatch [::e/set-active-panel :home-panel])
                         :style {:color "pink"}}
                     brand]]]
     [re-com/popover-anchor-wrapper
      :showing? (:analyses eg-steps)
      :position :below-center
      :style {:float "left"}
      :anchor [Nav {:active-key @active-panel :bs-style "pills"
                    :on-select #(re-frame/dispatch [::e/set-active-panel (keyword %)])}
               [NavItem {:event-key "upload-panel"} [Glyphicon {:glyph "cloud-upload"}] " Upload"]
               [NavItem {:event-key "mutation-panel"}
                [Glyphicon {:glyph "tasks" :style {:color "yellow"}}]
                [:span {:style {:color "yellow"}} " Mutation"]]
               [NavItem {:event-key "expression-panel"}
                [Glyphicon {:glyph "signal" :style {:color "yellow"}}]
                [:span {:style {:color "yellow"}} " Expression"]]]
      :popover [re-com/popover-content-wrapper
                :width "400px"
                :close-button? false
                :on-cancel #(re-tour/finish-tour eg-steps)
                :backdrop-opacity 0.5
                :title [:strong "Start to evaluate a biomarker"]
                :body [:div
                       "Now you can evaluate a biomarker (genes) based on mutation/CNV or expression data via '"
                       [:strong "Mutation"] "' or '" [:strong "Expression"] "' menu respectively."
                       [re-com/make-tour-nav eg-steps]]]]
     ;[:span {:style {:vertical-align "middle"}} [:strong "Gene set: "]]
     [re-com/popover-anchor-wrapper
      :showing? (:geneset eg-steps)
      :position :below-left
      :style {:float "left"}
      :anchor [re-com/single-dropdown
               :choices (concat
                          (mapv #(-> {:id (:id %) :label (:name %) :group "User's gene sets" :geneset %})
                                user-genesets)
                          (mapv #(-> {:id (:id %) :label (:name %) :group "Examples" :geneset %})
                                default-genesets))
               :model (re-frame/subscribe [::s/selected-geneset-id])
               :placeholder "Choose a set of genes"
               :width "280px"
               :max-height "390px"
               :filter-box? true
               :style {:margin "8px"}
               :render-fn #(let [gs (:geneset %)]
                             [:div
                              [:span (:name gs) " (" (count (:genes gs)) ")"]
                              (when (> (:id gs) 100)
                                [re-com/h-box
                                 :style {:float "right"}
                                 :children
                                 [[re-com/row-button
                                   :md-icon-name "zmdi-edit"
                                   :mouse-over-row? true
                                   :tooltip "Edit this gene set"
                                   :attr {:on-mouse-down
                                          (fn [e] (do (.stopPropagation e)
                                                      (reset! geneset-to-edit gs)))}]
                                  [re-com/row-button
                                   :md-icon-name "zmdi-delete"
                                   :mouse-over-row? true
                                   :tooltip "Delete this gene set"
                                   :attr {:on-mouse-down
                                          (fn [e] (do (.stopPropagation e)
                                                      (reset! geneset-to-delete gs)))}]]])])
               :on-change #(re-frame/dispatch [::e/select-a-geneset %])]
      :popover [re-com/popover-content-wrapper
                :width "350px"
                :title [:strong "Select a set of genes"]
                :body [:div "Now select a uploaded gene set here. Then, click the '"
                       [:strong "Next"] "' button to advance to the next step"
                       [re-com/make-tour-nav eg-steps]]]]
     ;[:span {:style {:vertical-align "middle"}} [:strong "Cohort: "]]
     [re-com/popover-anchor-wrapper
      :showing? (:cohort eg-steps)
      :position :below-left
      :style {:float "left"}
      :anchor [re-com/single-dropdown
               :choices (concat
                          (mapv #(-> {:id (:id %) :label (:name %) :group (:group %) :it %})
                                user-cohorts)
                          (mapv #(-> {:id (:id %) :label (:name %) :group (:group %) :it %})
                                default-cohorts))
               :model (re-frame/subscribe [::s/selected-cohort-id])
               :placeholder "Choose a cohort"
               :width "360px"
               :max-height "420px"
               :filter-box? true
               :style {:margin "8px"}
               :render-fn #(let [cohort (:it %)]
                             [:div [:span (:name cohort)]
                              (when (> (:id cohort) 100)
                                [re-com/h-box
                                 :style {:float "right"}
                                 :children
                                 [[re-com/row-button
                                   :md-icon-name "zmdi-delete"
                                   :mouse-over-row? true
                                   :tooltip "Delete this cohort"
                                   :attr {:on-mouse-down
                                          (fn [e] (do (.stopPropagation e)
                                                      (reset! cohort-to-delete cohort)))}]]])])
               :on-change #(re-frame/dispatch [::e/select-a-cohort %])]
      :popover [re-com/popover-content-wrapper
                :width "350px"
                :title [:strong "Select a cohort of interest"]
                :body [:div "Select a cancer cohort of interest. Then, click the '"
                       [:strong "Next"] "' button to proceed to the sections of analyses."
                       [re-com/make-tour-nav eg-steps]]]]
     [Nav {:pull-right true :active-key @active-panel :bs-style "pills"}
      [NavItem {:event-key "help-panel"
                :on-select #(re-frame/dispatch [::e/set-active-panel :help-panel])}
       [Glyphicon {:glyph "question-sign"}] " Help"]]]))

(defn- geneset-del [gs]
  (let [gs-id (:id gs)
        curr-gs-id (:id @(re-frame/subscribe [::s/selected-geneset]))]
    (println "Deleting geneset:" (:name gs))
    (when (= curr-gs-id gs-id)
      (re-frame/dispatch [::e/select-a-geneset 1]))
    (re-frame/dispatch-sync [::e/remove-a-geneset gs])
    (re-frame/dispatch [::exp-evts/on-geneset-deleted gs-id])
    (re-frame/dispatch [::mut-evts/on-geneset-deleted gs-id])))

(defn- cohort-del [co]
  (let [co-id (:id co)
        curr-co-id (:id @(re-frame/subscribe [::s/selected-cohort]))]
    (println "Deleting cohort:" (:name co))
    (when (= curr-co-id co-id)
      ; TCGA LUAD as default
      (re-frame/dispatch [::e/select-a-cohort 13]))
    (re-frame/dispatch-sync [::e/remove-a-cohort co])
    (re-frame/dispatch [::exp-evts/on-cohort-deleted co-id])
    (re-frame/dispatch [::mut-evts/on-cohort-deleted co-id])))

(defn deletion-modal-panel [to-delete del-fn]
  (when-let [it @to-delete]
    [re-com/modal-panel
     ;:backdrop-on-click #(reset! to-delete nil)
     :child [re-com/v-box
             :width "400px"
             :children [[:h4 "Are you sure to delete " [:code (:name it)] "?"]
                        [re-com/gap :size "20px"]
                        [re-com/h-box
                         :gap "15px"
                         :children
                         [[re-com/button
                           :label "Delete"
                           :class "btn-danger"
                           :on-click #(do (del-fn it) (reset! to-delete nil))]
                          [re-com/button
                           :label "Cancel"
                           :class "btn-primary"
                           :on-click #(reset! to-delete nil)]]]]]]))

(defn geneset-edit-modal-panel []
  (when @geneset-to-edit
    [re-com/modal-panel
     ;:backdrop-on-click #(reset! geneset-to-edit nil)
     :child [re-com/v-box
             :width "450px"
             :gap "10px"
             :children [[re-com/title :label "Modify a gene set" :level :level2]
                        [re-com/line :color "#ddd"]
                        [modify-geneset-form geneset-to-edit #(reset! geneset-to-edit nil)]]]]))

(defn alerts-list-panel [active-panel]
  (let [alerts (re-frame/subscribe [::s/alert-list])]
    [:span {:style {:position "relative"}}
     [re-com/alert-list
      :alerts alerts
      :on-close #(re-frame/dispatch [::e/remove-alert %])
      :style {:position "absolute"
              :width "20%"
              :top (case @active-panel
                     :mutation-panel "60px"
                     :expression-panel "60px"
                     "50px")
              :left "80%"
              :opacity "0.7"
              :z-index "300"}
      :border-style "0px"]]))

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::s/active-panel])]
    [re-com/v-box
     :height "100%"
     :margin "20px"
     :children [[top-navbar active-panel]
                [alerts-list-panel active-panel]
                [panels active-panel]
                [deletion-modal-panel geneset-to-delete geneset-del]
                [deletion-modal-panel cohort-to-delete cohort-del]
                [geneset-edit-modal-panel]]]))

(comment
  "Select a tab via the figwheel repl"
  (re-frame/dispatch [::e/set-active-panel :home-panel])
  (re-frame/dispatch [::e/set-active-panel :help-panel]))

