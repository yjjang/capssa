(ns viz-stra.exp.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [reagent-table.core :refer [reagent-table]]
            [ajax.core :as ajax :refer [POST]]
            [cljs.core.async :refer [<! timeout]]
            [goog.dom :as dom]
            [goog.array :as array]
            [cgis.biochart :as bio]
            [viz-stra.utils :refer [save-as-text]]
            [viz-stra.events :as e]
            [viz-stra.subs :as s]
            [viz-stra.exp.events :as events]
            [viz-stra.exp.subs :as subs]
            [viz-stra.comp :refer [Nav NavItem NavDropdown MenuItem Glyphicon Loader Spinner
                                   modify-geneset-form export-popover export-button]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; JSON request and response testing
#_(POST "/echo"
        {:format :json
         :response-format :json
         :params {:genes ["TP53" "RB1"]
                  :cancer-type "luad"}
         :handler #(js/console.log %)})

;; JQuery style request form params
#_(POST "/echo.f"
        {:body (str "chi_square_score=" 0.2022848103007694)
         :response-format :raw
         :handler #(js/console.log %)})

;; P-value for chi-squared score
#_(POST "/chip"
        {:body (str "chi_square_score=" 0.34817053791078745)
         :response-format :raw
         :handler #(js/console.log %)});0.5551505694514616

;; -- Hierarchical clustering UI -------------------------------------------

(defonce inchlib-ref (reagent/atom nil))

(def inchlib-settings {:target "inchlib"
                       :metadata false
                       :legend_area_width 200
                       :column_metadata true
                       :column_metadata_row_height 12
                       :metadata_font_size 12
                       :width 1000
                       :max_height 1200
                       :min_height 820
                       :max_row_height 20
                       :draw_row_ids true
                       :fixed_row_id_size false
                       :independent_columns false
                       :heatmap_colors "BuWhRd"
                       ;:column_metadata_colors "YlOrB"
                       :column_metadata_colors "Y2OrB"
                       ;:metadata_colors "PRGn2"
                       :navigation_toggle {:distance_scale false
                                           :filter_button false}
                       :column_dendrogram true})

(def cluster-boxplot-feature (reagent/atom nil))
(def cluster-sankey-feature (reagent/atom nil))

;; Current selected patients by clicking a column dendrogram
(def current-patient-indices (reagent/atom nil))
;; Current selected genes by clicking a row dendrogram
(def current-cluster-genes (reagent/atom nil))

(def spinning? (reagent/atom false))


(defn- exps-by-gene [gene data objs c-indices]
  (let [exps (clj->js (get-in data [(get objs gene) "features"]))]
    (reduce (fn [v i] (conj v (aget exps i))) [] c-indices)))

(defn- exps-by-subtype-gene [subtype gene feature-idx data objs features c-indices]
  (let [exps (clj->js (get-in data [(get objs gene) "features"]))
        feature-vals (clj->js (get features feature-idx))]
    (reduce (fn [v i] (if (= subtype (aget feature-vals i)) (conj v (aget exps i)) v))
            [] c-indices)))

(defn- to-fixed
  [precision n]
  ;(js/parseFloat (.toFixed n precision))
  (let [pseudo (/ 1 (.pow js/Math 10 (inc (js/Number. precision))))
        fixed (.toFixed (+ (js/Number. n) pseudo) precision)]
    (js/parseFloat fixed)))

#_(to-fixed 2 859.3851)
#_(to-fixed 10 8.3851E-16)
#_(to-fixed 10 2.0986801217981466E-6)

(defn- five-numbers [numbers]
  (if (empty? numbers)
    []
    (#(let [min (first %)
            max (last %)
            len (count %)
            m-idx (dec (Math/round (/ len 2)))
            median (nth % m-idx)
            quarter (Math/round (/ len 4))
            lq-idx (- m-idx quarter)
            lower-q (if (neg? lq-idx) min (nth % lq-idx))
            uq-idx (+ m-idx quarter)
            upper-q (if (= uq-idx len) max (nth % uq-idx))]
        (mapv (partial to-fixed 2) [min lower-q median upper-q max]))
      (sort numbers))))

#_(five-numbers (range 1 8))
#_(five-numbers [6.28 6.00 5.43])
#_(five-numbers [4.04 3.18])
#_(five-numbers [4.04])

(defn- log2 [n] (/ (.log js/Math (inc n)) (.-LN2 js/Math)))
#_(log2 0)
#_(log2 1)

(defn- box-plot-series-data
  ;; For expression by gene in risk scores
  ([genes group all-exps]
   (let [in-group? (set group)]
     (mapv (fn [gene]
             (->> all-exps
                  (filter #(and (= gene (aget % "hugo_symbol"))
                                (in-group? (aget % "participant_id"))))
                  (map #(aget % "tpm"))
                  five-numbers))
           genes)))
  ;; For expression by subtype in risk scores
  ([genes group all-exps subtypes pats]
   (let [in-group? (set group)
         in-genes? (set genes)
         all-exps (filter #(and (in-genes? (aget % "hugo_symbol"))
                                (in-group? (aget % "participant_id")))
                          all-exps)]
     (mapv (fn [subtype]
             (let [exps (->> all-exps
                             (filter #(let [pid (aget % "participant_id")]
                                        (= subtype (get pats pid))))
                             (map #(aget % "tpm")))]
               (five-numbers exps)))
           subtypes)))
  ;; For expressions by gene in hierarchical clustering
  ([genes data objs c-indices]
   (mapv #(let [exps (exps-by-gene % data objs c-indices)]
            (five-numbers exps))
         genes))
  ;; For expressions by subtype and gene in hierarchical clustering
  ([subtypes genes feature-idx data objs features c-indices]
   (let [c-indices (clj->js c-indices)]
     (mapv (fn [types gs]
             (let [exps (apply concat
                               (map #(exps-by-subtype-gene
                                       %1 %2 feature-idx data objs features c-indices)
                                    types gs))]
               (five-numbers exps)))
           (map #(repeat (count genes) %) subtypes)
           (repeat (count subtypes) genes)))))

(defn- rgb->hex [rgb]
  (reduce str (cons "#" (map #(.slice (str "0" (.toString % 16)) -2)
                             (map js/parseInt (re-seq #"\d+" rgb))))))

#_(assert (= "#7878f0" (rgb->hex "rgb(120,120,240")))
#_(assert (= "#0570b0" (rgb->hex "rgb(5,112,176)")))

(defn- subtype->color [il feature-idx subtypes]
  (let [desc (js->clj (.-column_metadata_descs il))]
    (zipmap subtypes
            (map #(rgb->hex (._get_color_for_value
                              il
                              (if-let [str2num (get-in desc [(str feature-idx) "str2num"])]
                                (if-let [v (get str2num %)] v %) %)
                              (get-in desc [(str feature-idx) "min"])
                              (get-in desc [(str feature-idx) "max"])
                              (get-in desc [(str feature-idx) "middle"])
                              (js->clj (.. il -settings -column_metadata_colors))))
                 subtypes))))

#_(js/console.log (.-column_metadata_descs @inchlib-ref))

(defn- mk-cluster-boxplot-cfg [feature genes c-indices]
  (let [il @inchlib-ref
        feature-names (.. il -column_metadata -feature_names)
        features (js->clj (.. il -column_metadata -features))
        feature-idx (.indexOf feature-names feature)
        objs (js->clj (.-objects2leaves il))
        genes (if (nil? genes) (keys objs) genes)
        data (-> (.. il -data -nodes) js->clj)
        feature-vals (get features feature-idx)
        divided? (not (nil? c-indices))
        c-indices (if-not divided? (.. il -on_features -data) c-indices)
        subtypes
        #_(->> (get-in (js->clj (.-column_metadata_descs il)) [(str feature-idx) "str2num"]) keys sort)
        (->> c-indices (map #(get feature-vals %)) (filter #(not= % "NA")) set sort)
        series-data (box-plot-series-data
                      subtypes genes feature-idx data objs features c-indices)
        color-map (subtype->color il feature-idx subtypes)
        colors (-> (select-keys color-map subtypes) vals vec)]
    {:subtypes subtypes
     :colors colors
     :divided? divided?
     ;; Highcharts configurations
     :chart {:type "boxplot"}
     :title {:text feature}
     :legend {:enabled divided?
              :itemStyle {:fontSize "10px"}}
     :xAxis
     (if divided?
       {:categories ["Selected cluster" "Unselected cluster"]
        :labels
        {:style {:fontSize "12px"}
         :formatter
         #(this-as this
                   (let [v (.-value this)]
                     (case v
                       "Selected cluster"
                       (str "<span style=\"fill: #F5273C\">" v "</span>")
                       "Unselected cluster"
                       (str "<span style=\"fill: #808080\">" v "</span>")
                       v)))}}
       {:categories subtypes})
     :yAxis {:title {:text "Expressions: log2(n + 1)"} }
     :plotOptions {:boxplot {:grouping divided?
                             :groupPadding 0.1
                             :fillColor "#F0F0F0"
                             ;:lineWidth 2
                             :lineWidth 0
                             ;:medianColor"#0C5DA5"
                             ;:medianWidth 3
                             :medianColor"rgba(255,255,255,1)"
                             :medianWidth 2
                             ;:stemColor "#A63400"
                             ;:stemDashStyle "dot"
                             ;:stemWidth 1
                             :stemWidth 2
                             ;:whiskerColor "#3D9200"
                             :whiskerLength "20%"
                             :whiskerWidth 3}}
     :series
     (if divided?
       [{:data series-data}
        {:data (let [c-indices? (set c-indices)
                     d-indices (filter (complement #(c-indices? %))
                                       (.. il -on_features -data))]
                 (box-plot-series-data
                   subtypes genes feature-idx data objs features d-indices))}]
       [{:data series-data}])}))

(defn- draw-col-features [il]
  (let [settings (.-settings il)
        col-meta? (.-column_metadata settings)
        row-h (.-column_metadata_row_height settings)]
    (when col-meta?
      (loop [f-names (.. il -column_metadata -feature_names)
             y (.-header_height il)]
        (when f-names
          (let [hm-start (.-heatmap_distance il)
                hgap (.-dendrogram_heatmap_distance il)
                feature (first f-names)
                text (.clone (.. il -objects_ref -column_header)
                             (clj->js {:x hm-start
                                       :y y
                                       :fontSize (.-metadata_font_size settings)
                                       :text feature}))]
            (.setOffset text #js {:x (+ (.getWidth text) hgap)
                                  :y (- (/ (.getHeight text) 2) (/ row-h 2))})
            (.add (.-header_layer il) text)
            (.on text "click"
                 #(case @(re-frame/subscribe [::subs/cluster-sub-plot])
                    :box-plot (reset! cluster-boxplot-feature feature)
                    :sankey-plot (reset! cluster-sankey-feature feature)
                    nil))
            (.draw text))
          (recur (next f-names) (+ y row-h)))))))

(defn- show-tooltip [il evt msg]
  (when msg
    (let [attrs (.. evt -target -attrs)
          tooltip (.clone (.. il -objects_ref -tooltip_label)
                          #js {:x (.-x attrs) :y (.-y attrs) :id "click_help"})]
      (.add tooltip
            (.clone (.. il -objects_ref -tooltip_tag) #js {:pointerDirection "down"})
            (.clone (.. il -objects_ref -tooltip_text) #js {:text msg}))
      (doto (.-heatmap_overlay il)
        (.add tooltip) (.moveToTop) (.draw)))))

(defn- draw-legends [il]
  (when (.-column_metadata il)
    (let [settings (.-settings il)
          headers (.-column_metadata_header il)
          features (js->clj (.. il -column_metadata -features))
          p-indices (.. il -on_features -data)
          desc (js->clj (.-column_metadata_descs il))
          colors (.-column_metadata_colors settings)
          selected-sub-plot (re-frame/subscribe [::subs/cluster-sub-plot])]
      (loop [indices (keys desc)
             h-step 0]
        (if indices
          (let [i (first indices)
                feature-vals (get features (js/parseInt i))
                values (->> (map #(get feature-vals %) p-indices)
                            set vec (sort (fn [a b]
                                            (if (= a "NA") 1
                                              (if (= b "NA") -1
                                                (compare a b))))))
                header (aget headers i)
                cols (zipmap values (map #(._get_color_for_value
                                            il
                                            (if-let [str2num (get-in desc [i "str2num"])]
                                              (if-let [v (get str2num %)] v %) %)
                                            (get-in desc [i "min"])
                                            (get-in desc [i "max"])
                                            (get-in desc [i "middle"])
                                            colors)
                                         values))
                y (+ h-step (.-header_height il))
                l-margin 2
                text (.clone (.. il -objects_ref -column_header)
                             (clj->js {:x (+ l-margin (.-width settings))
                                       :y y
                                       :fontSize (.-metadata_font_size settings)
                                       :fontStyle "bold"
                                       :text header}))]
            (.add (.-header_layer il) text)
            (.on text "click"
                 #(case @selected-sub-plot
                    :box-plot (reset! cluster-boxplot-feature header)
                    :sankey-plot (reset! cluster-sankey-feature header)
                    nil))
            (.on text "mouseover"
                 #(show-tooltip
                    il % (case @selected-sub-plot
                           :box-plot "Click to use as a subtype category"
                           :sankey-plot "Click to use in the concordance view"
                           nil)))
            (.draw text)
            (loop [cols cols
                   y (+ 18 h-step (.-header_height il))]
              (when-not (empty? cols)
                (let [[v col] (first cols)
                      icon (.clone (.. il -objects_ref -legend_icon)
                                   (clj->js {:x (+ l-margin 1 (.-width settings))
                                             :y y
                                             :width 8 :height 8
                                             :fill col}))
                      text (.clone (.. il -objects_ref -column_header)
                                   (clj->js {:x (+ l-margin 13 (.-width settings))
                                             :y y
                                             :fontSize (.-metadata_font_size settings)
                                             :fontStyle "normal"
                                             :text v}))]
                  (.add (.-header_layer il) icon)
                  (.draw icon)
                  (.add (.-header_layer il) text)
                  (.draw text))
                (recur (next cols) (+ y 18))))
            (recur (next indices) (+ 36 h-step (* 18 (count values)))))
          h-step)))))

(defn- reg-inchlib-events [il]
  (aset (.-events il) "on_zoom"
        #(do (draw-col-features il) (draw-legends il)
             (reset! current-cluster-genes nil)))
  (aset (.-events il) "on_columns_zoom"
        #(do (draw-col-features il) (draw-legends il)
             (reset! current-patient-indices nil)))
  (doseq [e ["on_refresh" "on_redraw_heatmap"]]
    (aset (.-events il) e
          #(do (draw-col-features il) (draw-legends il)
               (reset! current-patient-indices nil)
               (reset! current-cluster-genes nil))))
  (doseq [e ["on_unzoom" "on_columns_unzoom"]]
    (aset (.-events il) e #(do (draw-col-features il) (draw-legends il))))
  (aset (.-events il) "column_dendrogram_node_highlight"
        (fn [column-ids _] (reset! current-patient-indices (js->clj column-ids))))
  (aset (.-events il) "column_dendrogram_node_unhighlight"
        #(reset! current-patient-indices nil))
  (aset (.-events il) "dendrogram_node_highlight"
        (fn [object-ids _] (reset! current-cluster-genes (js->clj object-ids))))
  (aset (.-events il) "dendrogram_node_unhighlight"
        #(reset! current-cluster-genes nil)))

(def inchlib-mounted? (reagent/atom false))

(defn cluster-heatmap []
  (letfn [(create-and-draw [data]
            (println "Drawing InCHlib ...")
            (let [il (js/InCHlib. (clj->js inchlib-settings))]
              (reg-inchlib-events il)
              (doto il (.read_data (clj->js data)) (.draw))
              (draw-col-features il)
              (draw-legends il)
              (reset! inchlib-ref il)))]
    (reagent/create-class
      {:reagent-render (fn [] [:div#inchlib])
       :component-did-mount
       (fn [this]
         (println "InCHlib did mounted")
         (create-and-draw (reagent/props this))
         (reset! inchlib-mounted? true))
       :component-did-update
       (fn [this]
         (println "InCHlib did updated")
         (reset! current-patient-indices nil)
         (reset! current-cluster-genes nil)
         (create-and-draw (reagent/props this)))
       :component-will-unmount
       (fn [this]
         (println "InCHlib will be unmounted")
         (._delete_all_layers @inchlib-ref)
         (reset! cluster-boxplot-feature nil)
         (reset! cluster-sankey-feature nil)
         (reset! current-patient-indices nil)
         (reset! current-cluster-genes nil)
         (reset! inchlib-mounted? false))
       :display-name "InCHlib-clustered-heatmap"})))

(defn cluster-heatmap-with-spinning [data]
  (go (<! (timeout 100)) (reset! spinning? false))
  (if @spinning?
    [Loader (+ (:width inchlib-settings) (:legend_area_width inchlib-settings))]
    [cluster-heatmap data]))

(defn inchlib []
  (if-let [json @(re-frame/subscribe [::subs/cluster-data])]
    ;[cluster-heatmap json]
    (do (reset! spinning? true) [cluster-heatmap-with-spinning json])
    (if @(re-frame/subscribe [::s/data-loading?])
      [Spinner (+ (:width inchlib-settings) (:legend_area_width inchlib-settings))]
      (re-frame/dispatch [::events/http-load-cluster-data
                          @(re-frame/subscribe [::s/selected-geneset])
                          @(re-frame/subscribe [::s/selected-cohort])]))))

#_(let [init (repeat 4 (vec (repeat 4 [])))
        datas [[1 2 3 4 5]
               [2 3 4 5 6]
               [3 4 5 6 7]
               [4 5 6 7 8]]
        idx (atom -1)]
    (reduce (fn [rs [e d]]
              (conj rs (assoc e (swap! idx inc) d)))
            [] (map list init datas)))

(defn- add-series [chart subtypes colors datas]
  (doseq [[s c d] (map list subtypes colors datas)]
    (.addSeries chart (clj->js {:name s
                                :tooltip {:headerFormat "<em>{point.key}</em><br/>"}
                                :type "boxplot"
                                :color c
                                :fillColor c
                                :data d}))))
(defn cluster-box-plot []
  (letfn [(plot [this]
            (let [node (reagent/dom-node this)
                  config (reagent/props this)
                  colors (:colors config)
                  subtypes (:subtypes config)
                  divided? (:divided? config)
                  datas (if divided?
                          (map :data (:series config))
                          (let [init (repeat (count subtypes) (vec (repeat (count subtypes) [])))
                                idx (atom -1)]
                            (reduce (fn [rs [e d]] (conj rs (assoc e (swap! idx inc) d)))
                                    [] (map list init (:data (first (:series config)))))))
                  config (dissoc config :series)
                  chart (js/Highcharts.Chart. node (clj->js config))]
              (if divided?
                (add-series chart subtypes colors
                            (partition (count datas) (apply interleave datas)))
                (add-series chart subtypes colors datas))))]
    (reagent/create-class
      {:reagent-render
       (fn []
         [:div#box-plot
          {:style {:min-width "400px" :max-width "600px"
                   :height "400px" :margin "0 auto" :padding "5px"
                   :border "solid #D2D2D2 1px"}}])
       :component-did-mount
       (fn [this]
         (println "Cluster box plot mounted")
         (plot this))
       :component-did-update
       (fn [this]
         (println "Cluster box plot updated")
         (plot this))
       :display-name "Cluster-box-plot"})))

(defn- mk-cluster-gene-boxplot-cfg [genes c-indices]
  (let [il @inchlib-ref
        objs (.-objects2leaves il)
        genes (if (nil? genes) (js->clj (.keys js/Object objs)) genes)
        objs (js->clj objs)
        data (-> (.. il -data -nodes) js->clj)
        divided? (not (nil? c-indices))
        c-indices (if-not divided? (.. il -on_features -data) c-indices)]
    {:chart {:type "boxplot"}
     :title {:text ""}
     :legend {:enabled divided?
              :itemStyle {:fontSize "10px"}}
     :xAxis {:categories genes
             :labels {:autoRotation [-60]}}
     :yAxis {:title {:text "Expressions: log2(n + 1)"} }
     :plotOptions {:boxplot {:grouping divided?
                             :groupPadding 0.1
                             :lineWidth 0
                             :medianColor"rgba(255,255,255,1)"
                             :medianWidth 2
                             ;:stemDashStyle "dot"
                             :stemWidth 2
                             :whiskerLength "20%"
                             :whiskerWidth 3}}
     :series
     (map #(merge % {:tooltip {:headerFormat
                               "<span style=\"color:blue;\">{point.key}</span><br/>"}})
          (if divided?
            [{:data (box-plot-series-data genes data objs c-indices)
              :name "Selected samples"
              :color "#F5273C"
              :fillColor "#F5273C"}
             {:data (let [c-indices? (set c-indices)
                          d-indices (filter (complement #(c-indices? %)) (.. il -on_features -data))]
                      (box-plot-series-data genes data objs d-indices))
              :name "Unselected samples"
              :color "#808080"
              :fillColor "#808080"}]
            [{:data (box-plot-series-data genes data objs c-indices)
              :name "All samples"
              :color "#808080"
              :fillColor "#808080"}]))}))

(defn cluster-gene-box-plot [genes c-indices]
  [:div#gene-box-plot
   {:style {:min-width "500px" :max-width "600px"
            :height "400px" :margin "0 auto" :padding "5px"
            :border "solid #D2D2D2 1px"}
    :ref #(when %
            (let [config (mk-cluster-gene-boxplot-cfg genes c-indices)]
              (println "Drawing box plot by gene ...")
              (.chart js/Highcharts "gene-box-plot" (clj->js config))))}])

(defn cluster-surv-plot [c-indices json]
  [:div#expression_survival
   {:ref #(when %
            (letfn [(->survdiv [c-indices]
                      (let [il @inchlib-ref
                            p-indices (.. il -on_features -data)
                            ;; total patients in the data
                            patients (-> (.-header il) js->clj)
                            ;; all visible patients on zoom events
                            all-pats (map (fn [i] (get patients i))
                                          p-indices)
                            ;; patients in the selected column cluster
                            selected? (->> c-indices
                                           (map (fn [i] (get patients i)))
                                           set)]
                        (zipmap all-pats
                                (map (fn [p] (if (selected? p) "altered" "unaltered"))
                                     all-pats))))]
              (do (println "Drawing survival plot ...")
                (let [data {:element "#expression_survival"
                            :margin [0 0 0 0]
                            :data json
                            :division (->survdiv c-indices)
                            :pvalueURL "/chip"
                            :styles {:size {:chartWidth 430
                                            :chartHeight 470}
                                     :position {:chartTop 10
                                                :chartLeft 50
                                                :axisXtitlePosX 240
                                                :axisXtitlePosY 465
                                                :axisYtitlePosX -240
                                                :axisYtitlePosY 10
                                                :pvalX 220
                                                :pvalY 40}}
                            :legends {:high {:text "Selected group"
                                             :color "#F5273C"}
                                      :low {:text "Unselected group"
                                            :color "#808080"}}}]
                  (bio/survival (clj->js data))))))}])

(defn- mk-cluster-sankey-cfg [feature c-indices]
  (let [il @inchlib-ref
        feature-names (.. il -column_metadata -feature_names)
        features (js->clj (.. il -column_metadata -features))
        feature-idx (.indexOf feature-names feature)
        feature-vals (get features feature-idx)
        p-indices (.. il -on_features -data)
        data (sort #(compare (second %1) (second %2))
                   (concat (->> (map #(get feature-vals %) c-indices)
                                frequencies
                                (#(dissoc % "NA" nil))
                                (map identity)
                                (map #(cons "Selected patients" %)))
                           (let [cset (set c-indices)]
                             (->> (filter #(not (contains? cset %)) p-indices)
                                  (map #(get feature-vals %))
                                  frequencies
                                  (#(dissoc % "NA" nil))
                                  (map identity)
                                  (map #(cons "Unselected patients" %))))))
        subtypes (set (map second data))
        color-map (subtype->color il feature-idx subtypes)
        nodes (concat [{:id "Selected patients"
                        :color "#f56b6b"}
                       {:id "Unselected patients"
                        :color "#bbbbbb"}]
                      (map (fn [[k v]] {:id k :color v}) color-map))]
    {:title {:text feature}
     :series [{:keys ["from" "to" "weight"]
               :data data
               :nodes nodes
               :type "sankey"
               :name (str "Concordance to " feature)}]}))

(defn cluster-sankey-plot [feature c-indices]
  [:div#cluster-sankey
   {:style {:min-width "400px" :max-width "600px"
            :height "400px" :margin "0 auto"
            :border "solid #D2D2D2 1px"}
    :ref #(when %
            (let [config (mk-cluster-sankey-cfg feature c-indices)]
              (println "Drawing sankey plot ...")
              (.chart js/Highcharts "cluster-sankey" (clj->js config))))}])

(defn cluster-sub-plot [selected-plot]
  (case selected-plot
    :surv-plot (if-let [c-indices @current-patient-indices]
                 (let [cohort @(re-frame/subscribe [::s/selected-cohort])]
                   (if-let [json @(re-frame/subscribe [::s/survival-data cohort])]
                     [cluster-surv-plot c-indices json]
                     (if @(re-frame/subscribe [::s/data-loading?])
                       [Spinner]
                       (if (:user? cohort)
                         (re-frame/dispatch [::e/local-load-clinical-data cohort])
                         (re-frame/dispatch [::e/http-load-clinical-data cohort])))))
                 [:div [Glyphicon {:glyph "hand-left"}] " Select a cluster in the dendrogram."])
    :box-plot (let [feature @cluster-boxplot-feature
                    c-indices @current-patient-indices
                    genes @current-cluster-genes]
                (if-not feature
                  [:div [Glyphicon {:glyph "hand-left"}] " Select a subtype category."]
                  [cluster-box-plot (mk-cluster-boxplot-cfg feature genes c-indices)]))
    :gene-box-plot (let [c-indices @current-patient-indices
                         genes @current-cluster-genes]
                     [cluster-gene-box-plot genes c-indices])
    :sankey-plot (let [feature @cluster-sankey-feature
                       c-indices @current-patient-indices]
                   (if-not feature
                     [:div [Glyphicon {:glyph "hand-left"}] " Select a subtype category."]
                     (if-not c-indices
                       [:div [Glyphicon {:glyph "hand-left"}] " Select a cluster in the dendrogram."]
                       [cluster-sankey-plot feature c-indices])))
    [:div]))

(defn cluster-panel []
  [re-com/h-box
   :gap "10px"
   :style {:flex-flow "wrap"}
   :children [(let [cohort @(re-frame/subscribe [::s/selected-cohort])]
                (if (and (:user? cohort) (nil? @(re-frame/subscribe [::s/clinical-data cohort])))
                  (when-not @(re-frame/subscribe [::s/data-loading?])
                    (re-frame/dispatch [::e/local-load-clinical-data cohort]))
                  [inchlib]))
              (if @inchlib-mounted?
                [re-com/v-box
                 :gap "10px"
                 :children
                 (let [selected-plot (re-frame/subscribe [::subs/cluster-sub-plot])]
                   [[re-com/single-dropdown
                     :choices [{:id :surv-plot :label "Survival analysis"}
                               {:id :gene-box-plot :label "Expressions by gene"}
                               {:id :box-plot :label "Expressions by subtype"}
                               {:id :sankey-plot :label "Concordance to subtype"}]
                     :model selected-plot
                     :on-change #(re-frame/dispatch [::events/set-cluster-sub-plot %])
                     :width "200px"]
                    [cluster-sub-plot @selected-plot]])]
                [:div])]])

#_(re-frame/dispatch [::events/set-cluster-sub-plot :surv-plot])
#_(re-frame/dispatch [::events/set-cluster-sub-plot :box-plot])

;; -------------------------------------------------------------------------


;; -- Risk signature UI ----------------------------------------------------

(def risk-subtype (reagent/atom nil))
(def risk-division (reagent/atom nil))
(def risk-sel-genes (reagent/atom #{}))
(def risk-all-genes (atom nil))
(def risk-all-exps (atom nil))

(def risk-median-scores (atom nil))
(def risk-pi-scores (atom nil))

(defn signature-panel [json]
  [:div#risk-signature
   {:dangerouslySetInnerHTML {:__html "<div id=\"main\"></div>"}
    :style {:margin-bottom "10px"}
    :ref #(if-not %
            (do (reset! risk-division nil)
                (reset! risk-subtype nil)
                (reset! risk-sel-genes #{})
                (reset! risk-all-genes nil)
                (reset! risk-all-exps nil)
                (reset! risk-median-scores nil)
                (reset! risk-pi-scores nil))
            (letfn [(on-division [left mid right genes exps]
                      (reset! risk-division {:left (js->clj left)
                                             :mid (js->clj mid)
                                             :right (js->clj right)})
                      (reset! risk-all-genes genes)
                      (reset! risk-all-exps exps))
                    (on-subtype-select [type-name cols model]
                      (let [values (.. model -now -subtypeSet)
                            cols (into (sorted-map)
                                       (map (fn [v] [v (aget cols v "color")]) values))
                            pats (->> (js->clj (.. model -data -patient_subtype))
                                      (map (fn [[k v]] [k (get v type-name)]))
                                      (into (sorted-map)))]
                        (reset! risk-subtype {:name type-name :cols cols :pats pats})))
                    (on-gene-click [e]
                      (let [g (.. e -target -textContent)
                            already-sel? (@risk-sel-genes g)]
                        (if already-sel?
                          (do (swap! risk-sel-genes disj g)
                              (dom/setProperties
                                (.-target e) #js {:style "fill: #000; cursor: pointer;"}))
                          (do (swap! risk-sel-genes conj g)
                              (dom/setProperties
                                (.-target e) #js {:style "fill: #00f;
                                                          font-weight: bold;
                                                          cursor: pointer;"})))
                        (.stopPropagation e)))
                    (->median [tpms]
                      (let [tpms (sort tpms)
                            midx (-> tpms count (/ 2) Math/round dec)
                            median (nth tpms midx)]
                        median))
                    (median-scores [data]
                      (let [scores (map (fn [d]
                                          {:pid (.-pid d)
                                           :score (->median (map (fn [v] (.-tpm v)) (.-values d)))})
                                        data)]
                        (reset! risk-median-scores scores)
                        (clj->js scores)))
                    (->pi [cox values]
                      (let [coeffs (zipmap (get cox "genes") (get cox "coefficients"))]
                        (reduce (fn [pi v] (+ pi (* (get coeffs (.-gene v) 0.0) (.-tpm v))))
                                0 values)))
                    (prognostic-index [data]
                      (let [cox (get json "cox")
                            scores
                            (map (fn [d]
                                   {:pid (.-pid d) :score (to-fixed 10 (->pi cox (.-values d)))})
                                 data)]
                        ;(println (sort (map :score scores)))
                        (reset! risk-pi-scores scores)
                        (clj->js scores)))
                    (farthest-centroid [data]
                      (let [correlations (get json "fc")
                            scores
                            (map (fn [d]
                                   {:pid (.-pid d) :score (get correlations (.-pid d) 0.0)})
                                 data)]
                        ;(println (sort (map :score scores)))
                        (clj->js scores)))]
              (let [genesets (map (fn [gs] {:signature (:name gs) :source (:desc gs)})
                                  @(re-frame/subscribe [::s/geneset-list]))
                    opts {:element "#main"
                          :width 1200
                          :height 800
                          :data (assoc (get json "data") "signature_list" genesets)
                          :divisionFunc on-division
                          :riskFunctions
                          (if (get json "cox")
                            (if (get json "fc")
                              [{:name "Median" :func median-scores}
                               {:name "Prognostic Index" :func prognostic-index
                                :isDefault true}
                               {:name "Farthest Centroid" :func farthest-centroid}]
                              [{:name "Median" :func median-scores}
                               {:name "Prognostic Index" :func prognostic-index
                                :isDefault true}])
                            (if (get json "fc")
                              [{:name "Median" :func median-scores :isDefault true}
                               {:name "Farthest Centroid" :func farthest-centroid}]
                              [{:name "Median" :func median-scores :isDefault true}]))
                          :onSubtypeSelection on-subtype-select}]
                (println "Drawing expression signature ...")
                (dom/removeChildren (dom/getElement "main"))
                (bio/expression (clj->js opts))
                (doseq [id ["expression_title"
                            "expression_signature"]]
                  (dom/removeNode (dom/getElement id)))
                (let [heatmap (dom/getElement "expression_heatmap")
                      genes (->> (dom/getElement "expression_heatmap_svg")
                                 (dom/getElementByClass "left-axis-g-tag")
                                 (dom/getChildren)
                                 (array/toArray)
                                 (map dom/getFirstElementChild))]
                  (dom/setProperties
                    heatmap #js {:onclick
                                 (fn [e]
                                   (doseq [g genes]
                                     (dom/setProperties
                                       g #js {:style "fill: #000; cursor: pointer;"}))
                                   (reset! risk-sel-genes #{}))})
                  (doseq [g genes]
                    (dom/setProperties g #js {:onclick on-gene-click
                                              :style "cursor: pointer;"}))))))}])

(defn signature-panel-with-spinning [json]
  (go (<! (timeout 100)) (reset! spinning? false))
  (if @spinning?
    (do (reset! risk-subtype nil)
        [Loader 1200])
    [signature-panel json]))

(defn risk-signature []
  (if-let [json @(re-frame/subscribe [::subs/signature-data])]
    ;[signature-panel json]
    (do (reset! spinning? true)
        [signature-panel-with-spinning json])
    (if @(re-frame/subscribe [::s/data-loading?])
      [Spinner 1200]
      (re-frame/dispatch[::events/http-load-signature-data
                         @(re-frame/subscribe [::s/selected-geneset])
                         @(re-frame/subscribe [::s/selected-cohort])]))))

(defn- combinations [coll]
  (loop [[x & xs] coll
         rs []]
    (if (nil? xs) rs (recur xs (apply conj rs (map #(vector x %) xs))))))

#_(combinations [1 2 3 4])
#_(combinations '(a b c d))

;;TODO gamma coefficient?
#_(.log js/console @risk-subtype)
#_(.log js/console @risk-division)
#_(let [subtypes (-> (group-by #(second %) (:pats @risk-subtype))
                     (dissoc nil "NA")
                     (#(into {} (map (fn [[v p]] [v (map first p)]) %))))
        div (juxt (comp nil? (set (:left @risk-division)))
                  (comp nil? (set (:right @risk-division))))
        gamma (fn [[subtype pats]]
                (let [pairs (combinations pats)
                      {:keys [nc nd]}
                      (reduce (fn [m [a b]] (update m (if (= (div a) (div b)) :nc :nd) inc))
                              {:nc 0 :nd 0} pairs)]
                  [subtype (/ (- nc nd) (+ nc nd))]))]
    (into (sorted-map) (map gamma subtypes)))

(defn- mk-risk-sankey-cfg [subtype division]
  (when subtype
    (let [data
          (->> [[:right "High score group"]
                [:mid "Mid score group"]
                [:left "Low score group"]]
               (map #(concat
                       (->> (select-keys (:pats subtype) ((first %) division))
                            vals sort frequencies
                            ((fn [m] (dissoc m "NA" nil)))
                            (map identity)
                            (map (fn [freq] (cons (second %) freq))))))
               (apply concat))
          nodes (concat [{:id "High score group" :color "#f56b6b"}
                         {:id "Mid score group" :color "#bbbbbb"}
                         {:id "Low score group" :color "#5cd65c"}]
                        (map (fn [[k v]] {:id k :color v}) (:cols subtype)))
          type-name (:name subtype)]
      {:title {:text type-name}
       :series [{:keys ["from" "to" "weight"]
                 :data data
                 :nodes nodes
                 :type "sankey"
                 :name (str "Concordance to " type-name)}]})))

(defn risk-sankey-plot [config]
  [:div#risk-sankey
   {:style {:min-width "400px" :max-width "600px"
            :height "400px" :margin "0 auto"
            :border "solid #D2D2D2 1px"}
    :ref #(when % (.chart js/Highcharts "risk-sankey" (clj->js config)))}])

(defn- mk-risk-gene-boxplot-cfg [division genes all-exps]
  {:chart {:type "boxplot"}
   :title {:text ""}
   :legend {:enabled true
            :itemStyle {:fontSize "10px"}}
   :xAxis {:categories genes
           :labels {:autoRotation [-60]}}
   :yAxis {:title {:text "Expressions: log2(n + 1)"} }
   :plotOptions {:boxplot {:grouping true
                           ;:groupPadding 0.1
                           :lineWidth 0
                           :medianColor"rgba(255,255,255,1)"
                           :medianWidth 2
                           ;:stemDashStyle "dot"
                           :stemWidth 2
                           :whiskerLength "20%"
                           :whiskerWidth 3}}
   :series
   (map #(merge % {:tooltip {:headerFormat
                             "<span style=\"color:blue;\">{point.key}</span><br/>"}})
        (filter #(not-every? empty? (:data %))
                [{:data (box-plot-series-data genes (:left division) all-exps)
                  :name "Low score group"
                  :color "#00AC52"
                  :fillColor "#00AC52"}
                 {:data (box-plot-series-data genes (:mid division) all-exps)
                  :name "Mid score group"
                  :color "#808080"
                  :fillColor "#808080"}
                 {:data (box-plot-series-data genes (:right division) all-exps)
                  :name "High score group"
                  :color "#FF6252"
                  :fillColor "#FF6252"}]))})

(defn risk-gene-box-plot [division genes]
  [:div#gene-box-plot
   {:style {:min-width "500px" :max-width "600px"
            :height "400px" :margin "0 auto" :padding "5px"
            :border "solid #D2D2D2 1px"}
    :ref #(when %
            (let [config (mk-risk-gene-boxplot-cfg division genes @risk-all-exps)]
              (println "Drawing box plot by gene ...")
              (.chart js/Highcharts "gene-box-plot" (clj->js config))))}])

(defn- mk-risk-subtype-boxplot-cfg [subtype division genes all-exps]
  (let [feature (:name subtype)
        subtypes (filter #(not (= % "NA")) (keys (:cols subtype)))
        colors (vals (select-keys (:cols subtype) subtypes))
        pats (:pats subtype)]
    {:subtypes subtypes
     :colors colors
     ;; Highcharts configurations
     :chart {:type "boxplot"}
     :title {:text feature}
     :legend {:enabled true :itemStyle {:fontSize "10px"}}
     :xAxis
     {:categories (if (empty? (:mid division))
                    ["Low score group" "High score group"]
                    ["Low score group" "Mid score group" "High score group"])
      :labels
      {:style {:fontSize "12px"}
       :formatter
       #(this-as this
                 (let [v (.-value this)]
                   (case v
                     "Low score group"
                     (str "<span style=\"fill: #00AC52\">" v "</span>")
                     "Mid score group"
                     (str "<span style=\"fill: #808080\">" v "</span>")
                     "High score group"
                     (str "<span style=\"fill: #FF6252\">" v "</span>")
                     v)))}}

     :yAxis {:title {:text "Expressions: log2(n + 1)"} }
     :plotOptions {:boxplot {:grouping true
                             :groupPadding 0.1
                             :fillColor "#F0F0F0"
                             :lineWidth 0
                             :medianColor"rgba(255,255,255,1)"
                             :medianWidth 2
                             ;:stemDashStyle "dot"
                             :stemWidth 2
                             :whiskerLength "20%"
                             :whiskerWidth 3}}
     :series
     (filter #(not-every? empty? (:data %))
             [{:data (box-plot-series-data genes (:left division) all-exps subtypes pats)}
              {:data (box-plot-series-data genes (:mid division) all-exps subtypes pats)}
              {:data (box-plot-series-data genes (:right division) all-exps subtypes pats)}])}))

(defn risk-subtype-box-plot [subtype division genes]
  [:div#subtype-box-plot
   {:style {:min-width "500px" :max-width "600px"
            :height "400px" :margin "0 auto" :padding "5px"
            :border "solid #D2D2D2 1px"}
    :ref #(when %
            (let [config (mk-risk-subtype-boxplot-cfg subtype division genes @risk-all-exps)
                  subtypes (:subtypes config)
                  colors (:colors config)
                  datas (map :data (:series config))
                  config (dissoc config :series)
                  chart (.chart js/Highcharts "subtype-box-plot" (clj->js config))]
              (add-series chart subtypes colors
                          (partition (count datas) (apply interleave datas)))))}])

(defn- render-table-cell
  [{:keys [format attrs path]
    :or {format identity
         attrs (fn [_] {})}}
   row _ _]
  (let [data (get-in row path)]
    [:span (attrs data) (format data)]))

(defn- sort-table-rows
  [rows cols sorting]
  (let [col-keys (map :key cols)
        col-idx (first (map first sorting))
        dir (first (map second sorting))
        col-key (nth col-keys col-idx)]
    (case dir
      :asc (sort-by col-key rows)
      :desc (sort-by col-key (comp - compare) rows)
      rows)))

(defn- cox-table [json]
  (let [cox (get json "cox")
        f4 (partial to-fixed 4)
        data (reagent/atom
               (->> (map (fn [g e p z]
                           {:gene g :coeff (f4 e) :p-value (f4 p) :z-stats (f4 z)})
                         (get cox "genes")
                         (get cox "coefficients")
                         (get cox "p-values")
                         (get cox "z-statistics"))
                    (sort-by :p-value) vec))
        right-align (fn [_] {:style {:text-align "right" :display "block"}})
        cols [{:path [:gene] :header "GENE" :key :gene}
              {:path [:coeff] :header "COEFF." :key :coeff
               :attrs right-align}
              {:path [:p-value] :header "P-VALUE" :key :p-value
               :attrs right-align}
              {:path [:z-stats] :header "Z-SCORE" :key :z-stats
               :attrs right-align}]
        state (atom {:draggable true})]
    [reagent-table data
     {:table {:class "table table-hover table-striped table-bordered table-transition
                      table-condensed"
              :style {:border-spacing 0
                      :border-collapse "separate"
                      :max-width 460}}
      :th {:style {:border "1px solid white" :background-color "lightgray"}}
      :table-state  state
      ;:scroll-height "70vh"
      :row-key (fn [row _] (get-in row [:gene]))
      :column-model cols
      :render-cell render-table-cell
      :sort sort-table-rows}]))

(defn risk-sub-plot [selected-plot]
  (let [genes (if (empty? @risk-sel-genes)
                @risk-all-genes
                (filter @risk-sel-genes @risk-all-genes))]
    (case selected-plot
      :gene-box-plot [risk-gene-box-plot @risk-division genes]
      :subtype-box-plot (if @risk-subtype
                          [risk-subtype-box-plot @risk-subtype @risk-division genes]
                          [:div [Glyphicon {:glyph "hand-left"}] " Select a subtype category."])
      :sankey-plot (if-let [config (mk-risk-sankey-cfg @risk-subtype @risk-division)]
                     [risk-sankey-plot config]
                     [:div [Glyphicon {:glyph "hand-left"}] " Select a subtype category."])
      :cox-table [cox-table @(re-frame/subscribe [::subs/signature-data])]
      [:div])))

(defn risk-panel []
  [re-com/h-box
   :gap "10px"
   :style {:flex-flow "wrap"}
   :children [(let [cohort @(re-frame/subscribe [::s/selected-cohort])]
                (if (and (:user? cohort) (nil? @(re-frame/subscribe [::s/clinical-data cohort])))
                  (when-not @(re-frame/subscribe [::s/data-loading?])
                    (re-frame/dispatch-sync [::e/local-load-clinical-data cohort]))
                  [risk-signature]))
              (if @risk-division
                [re-com/v-box
                 :gap "10px"
                 :children
                 (let [selected-plot (re-frame/subscribe [::subs/risk-sub-plot])]
                   [[re-com/single-dropdown
                     :choices [{:id :gene-box-plot :label "Expressions by gene"}
                               {:id :subtype-box-plot :label "Expressions by subtype"}
                               {:id :sankey-plot :label "Concordance to subtype"}
                               {:id :cox-table :label "Cox regression"}]
                     :model selected-plot
                     :on-change #(re-frame/dispatch [::events/set-risk-sub-plot %])
                     :width "200px"]
                    [risk-sub-plot @selected-plot]])]
                [:div])]])

#_(js/console.log @(re-frame/subscribe [::subs/signature-data]))
#_(js/console.log @(re-frame/subscribe [::subs/cluster-data]))

;; -------------------------------------------------------------------------


;; -- Expression menu UI ---------------------------------------------------

(defn- panels [panel-id]
  (let [co @(re-frame/subscribe [::s/selected-cohort])]
    (if (and (:user? co) (not (:exp co))) 
      [:h4 [:i {:style {:width "22px" :margin-left "5px"} :class "zmdi zmdi-alert-triangle"}]
       "Expression data is not available."]
      (case panel-id
        :risk-panel [risk-panel]
        :cluster-panel
        (let [gs @(re-frame/subscribe [::s/selected-geneset])]
          (if (> (count (:genes gs)) 1)
            [cluster-panel]
            [:h4 [:i {:style {:width "22px" :margin-left "5px"} :class "zmdi zmdi-alert-triangle"}]
             "At least more than 2 genes are required."]))
        [:div]))))

(defn- navs [active-panel]
  [re-com/h-box
   :width "100%"
   :height "50px"
   :align :center
   :justify :between
   :children
   [[Nav {:bs-style "tabs" :active-key @active-panel
          :on-select #(when % (re-frame/dispatch [::events/set-active-panel (keyword %)]))}
     [NavItem {:event-key "risk-panel"}
      [:span {:style {:font-size "16px"}} "Risk Scores"
       (let [cohort @(re-frame/subscribe [::s/selected-cohort])
             showing? (reagent/atom false)
             disabled? (or (not= @active-panel :risk-panel)
                           (and (:user? cohort) (not (:exp cohort))))]
         [export-popover [export-button disabled? :showing? showing?]
          :showing? showing?
          :save-all
          (fn []
            (reset! showing? false)
            (let [div @risk-division
                  pats (->> div vals (apply concat) set)
                  genes @risk-all-genes
                  exprs (js->clj @risk-all-exps)
                  clinicals @(re-frame/subscribe [::s/clinical-data cohort])
                  cnames (when-let [c (first clinicals)] (-> (dissoc c "participant_id" "class") keys sort))
                  fields (concat [:class :median]
                                 (when @risk-pi-scores [:pi])
                                 (when (@(re-frame/subscribe [::subs/signature-data]) "fc") [:fc])
                                 genes cnames)
                  data (->> ; Initialize the result map as {"pid" {:participant_id "pid" :field "value" ...}}
                            (reduce (fn [m p]
                                      (assoc m p (apply array-map
                                                        (concat [:participant_id p]
                                                                (reduce #(conj %1 %2 nil) [] fields)))))
                                    (sorted-map) pats)
                            ; Stratification result -> :class
                            (#(reduce (fn [d p] (assoc-in d [p :class] "LO")) % (:left div)))
                            (#(reduce (fn [d p] (assoc-in d [p :class] "ME")) % (:mid div)))
                            (#(reduce (fn [d p] (assoc-in d [p :class] "HI")) % (:right div)))
                            ; Median scores
                            (#(reduce (fn [d m] (assoc-in d [(:pid m) :median] (:score m))) % @risk-median-scores))
                            ; Prognostic index scores
                            (#(reduce (fn [d m] (assoc-in d [(:pid m) :pi] (:score m))) % @risk-pi-scores))
                            ; Farthest centroid scores
                            (#(reduce (fn [d [pid score]] (assoc-in d [pid :fc] score))
                                      % (@(re-frame/subscribe [::subs/signature-data]) "fc")))
                            ; Gene expression values: log2(tpm+1)
                            (#(reduce (fn [d e]
                                        (let [pid (e "participant_id")]
                                          (if (pats pid) (assoc-in d [pid (e "hugo_symbol")] (e "tpm")) d)))
                                      % exprs))
                            ; Clinical information
                            (#(reduce (fn [d c]
                                        (let [pid (c "participant_id")]
                                          (if (pats pid)
                                            (reduce (fn [e n] (assoc-in e [pid n] (c n))) d cnames) d)))
                                      % clinicals)))
                  csv (.unparse js/Papa (clj->js (vals data))
                                #js {:header true :delimiter "\t"})]
              (save-as-text csv "expr_signature_all.tsv")))
          :save-group
          (fn []
            (reset! showing? false)
            (let [div @risk-division
                  data (->> (concat 
                              (reduce #(conj %1 [%2 "LO"]) [] (:left div))
                              (reduce #(conj %1 [%2 "ME"]) [] (:mid div))
                              (reduce #(conj %1 [%2 "HI"]) [] (:right div)))
                            (sort-by first)
                            (cons [:participant_id :class]))
                  csv (.unparse js/Papa (clj->js data)
                                #js {:header true :delimiter "\t"})]
              (save-as-text csv "expr_signatrue_groups.tsv")))])]]
     [NavItem {:event-key "cluster-panel"}
      [:span {:style {:font-size "16px"}} "Hierarchical Clsutering"
       (let [cohort @(re-frame/subscribe [::s/selected-cohort])
             showing? (reagent/atom false)
             disabled? (or (not= @active-panel :cluster-panel)
                           (and (:user? cohort) (not (:exp cohort))))]
         [export-popover [export-button disabled? :showing? showing?]
          :showing? showing?
          :save-all
          (fn []
            (reset! showing? false)
            (let [il @inchlib-ref
                  ; patients in data
                  patients (js->clj (.-header il))
                  ; patient indices are changed by zooming event
                  p-indices (-> il .-on_features .-data)
                  ; patient ids mapped to current zoomed indices
                  pats (->> p-indices (map (fn [i] (get patients i))) set)
                  ; patient indices in the selected cluster
                  c-indices @current-patient-indices
                  cats (map (fn [i] (get patients i)) c-indices)
                  genes (as-> @current-cluster-genes ?
                          (if (nil? ?) (:genes @(re-frame/subscribe [::s/selected-geneset])) ?)
                          (set ?))
                  tpms (let [dataset @(re-frame/subscribe [::subs/cluster-data])
                             pnames (get-in dataset ["data" "feature_names"])]
                         (reduce #(if-let [[g] (%2 "objects")]
                                    (concat %1 (map (fn [p exp] {:participant_id p :gene g :tpm exp})
                                                    pnames (%2 "features")))
                                    %1)
                                 [] (vals (get-in dataset ["data" "nodes"]))))
                  clinicals @(re-frame/subscribe [::s/clinical-data cohort])
                  cnames (when-let [c (first clinicals)] (-> (dissoc c "participant_id" "class") keys sort))
                  fields (concat [:class] genes cnames)
                  data (->> ; Initialize the result map as {"pid" {:participant_id "pid" :field "value" ...}}
                            (reduce (fn [m p]
                                      (assoc m p (apply array-map
                                                        (concat [:participant_id p]
                                                                (reduce #(conj %1 %2 nil) [] fields)))))
                                    (sorted-map) pats)
                            ; Stratification result -> :class
                            (#(reduce (fn [d p] (assoc-in d [p :class] "C2")) % pats))
                            (#(reduce (fn [d p] (assoc-in d [p :class] "C1")) % cats))
                            ; Gene expression values: log2(tpm+1)
                            (#(reduce (fn [d e]
                                        (let [p (e :participant_id)
                                              g (e :gene)]
                                          (if (and (genes g) (pats p)) (assoc-in d [p g] (e :tpm)) d)))
                                      % tpms))
                            ; Clinical information
                            (#(reduce (fn [d c]
                                        (let [pid (c "participant_id")]
                                          (if (pats pid)
                                            (reduce (fn [e n] (assoc-in e [pid n] (c n))) d cnames) d)))
                                      % clinicals)))
                  csv (.unparse js/Papa (clj->js (vals data)) #js {:header true :delimiter "\t"})]
              (save-as-text csv "expr_hcluster_all.tsv")))
          :save-group
          (fn []
            (reset! showing? false)
            (let [il @inchlib-ref
                  patients (js->clj (.-header il))
                  p-indices (-> il .-on_features .-data)
                  pats (->> p-indices (map (fn [i] (get patients i))) set)
                  c-indices @current-patient-indices
                  cats (map (fn [i] (get patients i)) c-indices)
                  data (->> (concat 
                              (reduce #(conj %1 [%2 "C1"]) [] cats)
                              (reduce #(conj %1 [%2 "C2"]) [] (apply (partial disj pats) cats)))
                            (sort-by first)
                            (cons [:participant_id :class]))
                  csv (.unparse js/Papa (clj->js data)
                                #js {:header true :delimiter "\t"})]
              (save-as-text csv "expr_hcluster_groups.tsv")))])]]]
    [re-com/alert-box
     :alert-type :info
     :style {:color "#222"
             :background-color "rgba(255, 165, 0, 0.1)"
             :border-top "none"
             :border-right "none"
             :border-bottom "none"
             :border-left "4px solid rgba(255, 165, 0, 0.8)"
             :border-radius "0px"}
     :heading (let [gs @(re-frame/subscribe [::s/selected-geneset])
                    co @(re-frame/subscribe [::s/selected-cohort])
                    showing? (reagent/atom false)
                    geneset-to-edit (reagent/atom nil)
                    cancel-popover #(do (reset! showing? false)
                                        (reset! geneset-to-edit nil))]
                [:span
                 (when (:user? gs)
                   [re-com/popover-anchor-wrapper
                    :showing? showing?
                    :position :below-center
                    :anchor [re-com/row-button
                             :md-icon-name "zmdi-edit"
                             :style {:margin-right "5px"}
                             :mouse-over-row? true
                             :tooltip "Edit this gene set"
                             :on-click #(when-not @showing?
                                          (do (reset! geneset-to-edit gs)
                                              (reset! showing? true)))]
                    :popover [re-com/popover-content-wrapper
                              :width "460px"
                              :title "Modify a gene set"
                              :backdrop-opacity 0.3
                              :on-cancel cancel-popover
                              :body [modify-geneset-form geneset-to-edit cancel-popover]]])
                 (if-let [link (:link gs)]
                   [:a {:href link :target "_blank"} (:name gs)]
                   (:name gs))
                 " @ "
                 (if-let [link (:link co)]
                   [:a {:href link :target "_blank"} (:name co)]
                   (:name co))
                 [:label {:for "upload-clinical"}
                  [:input.hidden
                   {:type "file"
                    :id "upload-clinical"
                    :accept "*.csv"
                    :on-change
                    #(let [target (.-currentTarget %)
                           file (-> target .-files (aget 0))]
                       (set! (.-value target) "")
                       (js/Papa.parse
                         file
                         #js {:header true
                              :dynamicTyping true
                              :skipEmptyLines "greedy"
                              :complete
                              (fn [result]
                                (let [data (.-data result)]
                                  (re-frame/dispatch-sync [::e/add-clinical-data co data])
                                  (re-frame/dispatch [::events/on-clinical-added (:id co)])
                                  (if (= @active-panel :cluster-panel)
                                    (re-frame/dispatch
                                      [::events/update-cluster-data (:id gs) (:id co) data]))))}))}]
                  [re-com/md-icon-button
                   :md-icon-name "zmdi-collection-plus"
                   :style {:margin-left "5px"}
                   :size :smaller
                   ;:mouse-over-row? true
                   :tooltip "Add more clinical informaton"]]])]]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])]
    [re-com/v-box
     :height "100%"
     :children [[navs active-panel]
                [re-com/gap :size "10px"]
                [panels @active-panel]]]))

;; -------------------------------------------------------------------------

