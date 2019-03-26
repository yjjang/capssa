(ns viz-stra.mut.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [re-com.core :as re-com]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :refer [<! timeout]]
            [goog.dom :as dom]
            [cgis.biochart :as bio]
            [viz-stra.events :as e]
            [viz-stra.subs :as s]
            [viz-stra.mut.db :as db]
            [viz-stra.mut.events :as events]
            [viz-stra.mut.subs :as subs]
            [viz-stra.comp :refer [Nav NavItem NavDropdown MenuItem Glyphicon Loader Spinner
                                   modify-geneset-form]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def spinning? (reagent/atom false))


;; -- Mutual Exclusivity UI ------------------------------------------------

(defn- draw-exclusivity [json]
  (let [data (get json "data")
        opts {:element "#main"
              :width 1200
              :height 800
              :data {:heatmap (get-in data ["file_list" 1 "contents"])
                     :network (get-in data ["file_list" 0 "contents"])
                     :sample (get data "sample_variants")
                     :survival {:patient (get data "patient_list")
                                :types (get data "variant_types")}
                     :type "LUAD"}}]
    (println "Drawing mutual exclusivity...")
    (dom/removeChildren (dom/getElement (name :main)))
    (bio/exclusivity (clj->js opts))
    (dom/removeNode (dom/getElement "exclusivity_title"))))

(defn exclusivity-panel [json]
  [:div {:dangerouslySetInnerHTML {:__html "<div id=\"main\"></div>"}
         :ref #(when %
                 (if json
                   (draw-exclusivity json)
                   (GET "/mutex"
                        {:response-format :json
                         :handler (fn [json]
                                    (re-frame/dispatch [::events/set-exclusivity-data json])
                                    (println "Mutual exclusivity data loaded.")
                                    (println (get json "message")))})))}])

#_(get-in (deref (re-frame/subscribe [::subs/exclusivity-data]))
          ["data" "file_list" 0 "contents"])
#_(re-frame/dispatch [::events/set-exclusivity-data nil])

;; -------------------------------------------------------------------------


;; -- Mutation Landscape UI ------------------------------------------------

(def landscape-loaded? (reagent/atom false))
(def landscape-division (reagent/atom {:enabled [] :disabled [] :others []}))
(def subtype-colors (atom {}))
(def subtype-data (atom {}))
(def on-subtype (reagent/atom nil))
(def visible-only? (reagent/atom false))

(defn mutation-landscape [json]
  (if (empty? (get-in json ["data" "gene_list"]))
    [:h4 [:i {:style {:width "22px"} :class "zmdi zmdi-alert-triangle"}]
     "None of these genes was altered."]
    [:div#mut-landscape
     {:dangerouslySetInnerHTML {:__html "<div id=\"main\"></div>"}
      :style {:margin-bottom "10px"}
      :ref #(if %
              (letfn [(on-landscape-load
                        [sdata scols]
                        ;(.log js/console sdata)
                        ;(.log js/console scols)
                        (reset! subtype-colors
                                (into {} (map (fn [[k v]] [k (get v "color")]) (js->clj scols))))
                        (reset! subtype-data
                                (into {} (map-indexed
                                           (fn [i title]
                                             [title (into {} (map (fn [p] [(.-x p) (.-value p)])
                                                                  (aget (.-group sdata) i)))])
                                           (map (fn [p] (.-y p)) (.-patient sdata)))))
                        (reset! landscape-loaded? true))
                      (on-subtype-click
                        [subtype]
                        (reset! on-subtype subtype))
                      (on-division
                        [e d o]
                        (reset! landscape-division
                                {:enabled (js->clj e)
                                 :disabled (js->clj d)
                                 :others (js->clj o)}))]
                (let [opts {:element "#main"
                            :width 1200
                            :height 800
                            :plot {:patient false
                                   :pq false}
                            :data {:pq "p"
                                   :data (get json "data")
                                   :type (get-in json ["data" "type"])
                                   :title (get-in json ["data" "name"])}
                            :clinicalFunc on-landscape-load
                            :onClickClinicalName on-subtype-click
                            :divisionFunc on-division}]
                  (println "Drawing mutation landscape ...")
                  (dom/removeChildren (dom/getElement "main"))
                  (bio/landscape (clj->js opts))
                  (dom/removeNode (dom/getElement "landscape_title"))))
              (do (reset! landscape-division {})
                  (reset! subtype-colors {})
                  (reset! subtype-data {})
                  (reset! on-subtype nil)
                  (reset! visible-only? false)
                  (reset! landscape-loaded? false)))}]))

(defn mutation-landscape-with-spinning [json]
  (go (<! (timeout 100)) (reset! spinning? false))
  (if @spinning?
    [Loader 1200]
    [mutation-landscape json]))

(defn landscape-panel []
  (if-let [json @(re-frame/subscribe [::subs/landscape-data])]
    (do (reset! spinning? true)
        [mutation-landscape-with-spinning json])
    (if @(re-frame/subscribe [::s/http-loading?])
      [Spinner 1200]
      (re-frame/dispatch [::events/http-load-landscape-data
                          @(re-frame/subscribe [::s/selected-geneset])
                          @(re-frame/subscribe [::s/selected-cohort])]))))

(defn landscape-surv-plot [json div v-only?]
  [:div#expression_survival
   {:ref #(when %
            (do (println "Drawing survival plot ...")
                (let [altered (:enabled div)
                      unaltered (if (and (pos? (count (:disabled div))) v-only?)
                                  (:disabled div)
                                  (concat (:disabled div) (:others div)))
                      division (merge (zipmap altered (repeat (count altered) "altered"))
                                      (zipmap unaltered (repeat (count unaltered) "unaltered")))
                      data {:element "#expression_survival"
                            :margin [0 0 0 0]
                            :data json
                            :division division
                            :pvalueURL "/chip"
                            :styles {:size {:chartWidth 430
                                            :chartHeight 430}
                                     :position {:chartTop 10
                                                :chartLeft 50
                                                :axisXtitlePosX 240
                                                :axisXtitlePosY 425
                                                :axisYtitlePosX -225
                                                :axisYtitlePosY 10
                                                :pvalX 220
                                                :pvalY 40}}
                            :legends {:high {:text "Altered group"
                                             :color "#F5273C"}
                                      :low {:text "Unaltered group"
                                            :color "#3385FF"}}}]
                  (bio/survival (clj->js data)))))}])

(defn- mk-landscape-sankey-cfg [on div]
  (let [data (letfn [(triplet [from pats]
                       (->> (select-keys (get @subtype-data on) pats)
                            vals sort frequencies
                            (#(dissoc % "NA"))
                            (map identity)
                            (map #(cons from %))))]
               (concat
                 (triplet "Altered group" (:enabled div))
                 (triplet "Unaltered group (visible)" (:disabled div))
                 (triplet "Unaltered group (invisible)" (:others div))))
        nodes (concat [{:id "Altered group" :color "#F56B6B"}
                       {:id "Unaltered group (visible)" :color "#66A8FF"}
                       {:id "Unaltered group (invisible)" :color "#BBBBBB"}]
                      (map (fn [[k v]] {:id k :color v}) @subtype-colors))]
    {:title {:text on}
     :series [{:keys ["from" "to" "weight"]
               :data data
               :nodes nodes
               :type "sankey"
               :name (str "Concordance to " on)}]}))

(defn landscape-sankey-plot [on div]
  [:div#landscape-sankey
   {:style {:min-width "400px" :max-width "600px"
            :height "400px" :margin "0 auto"
            :border "solid #D2D2D2 1px"}
    :ref #(when % (.chart js/Highcharts "landscape-sankey"
                          (clj->js (mk-landscape-sankey-cfg on div))))}])

(defn landscape-sub-plot [plot]
  (case plot
    :surv-plot [re-com/v-box
                :gap "5px"
                :children
                [(let [cohort @(re-frame/subscribe [::s/selected-cohort])]
                   (if-let [json @(re-frame/subscribe [::s/survival-data cohort])]
                     [landscape-surv-plot json @landscape-division @visible-only?]
                     (if (or (nil? @(re-frame/subscribe [::subs/landscape-data]))
                             @(re-frame/subscribe [::s/http-loading?]))
                       [Spinner]
                       (re-frame/dispatch [::e/http-load-clinical-data cohort]))))
                 (when (pos? (count (:disabled @landscape-division)))
                   [re-com/checkbox
                    :label "Includes visible samples only"
                    :model visible-only?
                    :on-change #(reset! visible-only? %)
                    :style {:margin-left "15px"}])]]
    :sankey-plot (if-let [on @on-subtype]
                   [landscape-sankey-plot on @landscape-division]
                   [:div [Glyphicon {:glyph "hand-left"}] " Select a subtype category."])
    [:div]))

;; -------------------------------------------------------------------------


;; -- Mutation menu UI -----------------------------------------------------

(defn selected-panel [active-panel-id]
  (case @active-panel-id
    :exclusivity-panel
    (let [json @(re-frame/subscribe [::subs/exclusivity-data])]
      [exclusivity-panel json])
    :landscape-panel
    [re-com/h-box
     :gap "10px"
     :style {:flex-flow "wrap"}
     :children [[landscape-panel]
                (if @landscape-loaded?
                  [re-com/v-box
                   :gap "10px"
                   :children
                   (let [sub-plot (re-frame/subscribe [::subs/landscape-sub-plot])]
                     [[re-com/single-dropdown
                       :choices [{:id :surv-plot :label "Survival analysis"}
                                 {:id :sankey-plot :label "Concordance to subtype"}]
                       :model sub-plot
                       :on-change #(re-frame/dispatch [::events/set-landscape-sub-plot %])
                       :width "200px"]
                      [landscape-sub-plot @sub-plot]])]
                  [:div])]]
    [:div]))

(defn navs [active-panel-id]
  [re-com/h-box
   :width "100%"
   :height "50px"
   :align :center
   :justify :between
   :children [[Nav {:bs-style "tabs" :active-key @active-panel-id
                    :on-select #(when % (re-frame/dispatch [::events/set-active-panel (keyword %)]))}
               [NavItem {:event-key "landscape-panel"} "Mutational patterns"]
               ;[NavItem {:event-key "exclusivity-panel"} "Mutual Exclusivity"]
               ]
              [re-com/alert-box
               :alert-type :info
               :style {:color "#222"
                       :background-color "#eff9e3"
                       :border-top "none"
                       :border-right "none"
                       :border-bottom "none"
                       :border-left "4px solid green"
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
                                        :on-cancel cancel-popover
                                        :body [modify-geneset-form geneset-to-edit cancel-popover]]])
                           (if-let [link (:link gs)]
                             [:a {:href link :target "_blank"} (:name gs)]
                             (:name gs))
                           " @ "
                           (if-let [link (:link co)]
                             [:a {:href link :target "_blank"} (:name co)]
                             (:name co))])]]])

(defn main-panel []
  (let [active-panel-id (re-frame/subscribe [::subs/active-panel])]
    [re-com/v-box
     :height "100%"
     :children [[navs active-panel-id]
                ;[re-com/gap :size "10px"]
                [selected-panel active-panel-id]]]))

;; -------------------------------------------------------------------------

