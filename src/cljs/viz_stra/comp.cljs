(ns viz-stra.comp
  (:require [reagent.core :refer [adapt-react-class] :as reagent]
            [re-frame.core :as re-frame]
            [cljsjs.react-bootstrap]
            [clojure.string :as string]
            [ajax.core :refer [POST]]
            [reforms.reagent :include-macros true :as f]
            [reforms.validation :include-macros true :as v]
            [viz-stra.events :as e]
            [viz-stra.exp.events :as exp-evts]
            [viz-stra.mut.events :as mut-evts]))


;; -- Reagent compoents for React-Bootstrap components -----------------
;;
(def Button (adapt-react-class (aget js/ReactBootstrap "Button")))
(def Glyphicon (adapt-react-class (aget js/ReactBootstrap "Glyphicon")))
(def Alert (adapt-react-class (aget js/ReactBootstrap "Alert")))
(def Navbar (adapt-react-class (aget js/ReactBootstrap "Navbar")))
(def Navbar-Header (adapt-react-class (aget js/ReactBootstrap "Navbar" "Header")))
(def Navbar-Brand (adapt-react-class (aget js/ReactBootstrap "Navbar" "Brand")))
(def Navbar-Toggle (adapt-react-class (aget js/ReactBootstrap "Navbar" "Toggle")))
(def Navbar-Collapse (adapt-react-class (aget js/ReactBootstrap "Navbar" "Collapse")))
(def Nav (adapt-react-class (aget js/ReactBootstrap "Nav")))
(def NavItem (adapt-react-class (aget js/ReactBootstrap "NavItem")))
(def NavDropdown (adapt-react-class (aget js/ReactBootstrap "NavDropdown")))
(def MenuItem (adapt-react-class (aget js/ReactBootstrap "MenuItem")))
(def SplitButton (adapt-react-class (aget js/ReactBootstrap "SplitButton")))
(def Fade (adapt-react-class (aget js/ReactBootstrap "Fade")))
;;
;; ---------------------------------------------------------------------


;; -- Reagent compoents for react-virtualized ---------------
;;
;;
;; ---------------------------------------------------------------------


(defn Spinner
  ([]
   [:div.spinner
    [:div.rect1]
    [:div.rect2]
    [:div.rect3]
    [:div.rect4]
    [:div.rect5]])
  ([width]
   [:div {:style {:width (str width "px")}}
    [Spinner]]))

(defn Loader
  ([]
   [:div.loader])
  ([width]
   [:div {:style {:width (str width "px") :text-align "center"}}
    [Loader]]))

(defn- modify-geneset! [geneset old-genes ui-state close-fn]
  (when (v/validate!
          geneset
          ui-state
          (v/present [:name] "Enter the name of geneset!")
          (v/present [:genes] "You should provide a list of gene symbols!"))
    (let [gs (-> @geneset
                 (update :genes #(filter (complement empty?) (string/split % #"[\s+,]"))))
          new-genes (:genes gs)]
      (println "Modifying a geneset : " gs)
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
                                             :heading "Can not modify a gene set."
                                             :body "No valid gene symbols."}])
                           (do (re-frame/dispatch
                                 [::e/modify-a-geneset
                                  (-> gs (assoc :genes valids) (assoc :unknowns unknowns))])
                               ;; Delete obsolete cache then reload automatically
                               (when-not (= old-genes new-genes)
                                 (do (re-frame/dispatch [::exp-evts/on-geneset-deleted (:id gs)])
                                     (re-frame/dispatch [::mut-evts/on-geneset-deleted (:id gs)])))
                               (close-fn)))
                         (swap! ui-state assoc :doing? false))
             :error-handler (fn [{:keys [status status-text]}]
                              (re-frame/dispatch
                                [::e/add-alert {:alert-type :danger
                                                :heading status
                                                :body status-text}])
                              (swap! ui-state assoc :doing? false))}))))

(defn modify-geneset-form [geneset-to-edit close-fn]
  (let [ui-state (reagent/atom {})
        old-genes (:genes @geneset-to-edit)]
    (swap! geneset-to-edit update :genes #(string/join "\n" %))
    (fn []
      (f/with-options {:form {:horizontal true}}
        (v/form ui-state
                {:style {:width "500px"}
                 :on-submit #(modify-geneset! geneset-to-edit old-genes ui-state close-fn)}
                (v/text "Name" geneset-to-edit [:name]
                        :placeholder "A name of the gene set")
                (v/text "Description" geneset-to-edit [:desc]
                        :placeholder "Description of the gene set")
                (v/textarea {:rows 8} "Genes" geneset-to-edit [:genes]
                            :placeholder "Space, comma or new-line separated gene symbols"
                            :help (when-let [link (@geneset-to-edit :link)]
                                    [:a {:href link :target "_blank" :style {:color "#337ab7"}}
                                     [:i {:class "zmdi zmdi-link"}] " See the gene set in MSigDB"]))
                (f/form-buttons
                  (f/button-primary [:span [:i {:class "zmdi zmdi-check"}] " Apply"]
                                    #(modify-geneset! geneset-to-edit old-genes ui-state close-fn)
                                    :in-progress (:doing? @ui-state)
                                    :disabled (:doing? @ui-state))
                  (f/button-default {:style {:font-weight "400" :margin-left "10px"}}
                                    [:span [:i {:class "zmdi zmdi-close"}] " Cancel"]
                                    close-fn)))))))

