(defproject viz-stra "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.6"]
                 [day8.re-frame/http-fx "0.1.6"]
                 [re-com "2.1.0"]
                 [reagent-reforms "0.4.3"]
                 [org.clojars.frozenlock/reagent-table "0.1.5"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [secretary "1.2.3"]
                 [compojure "1.6.1"]
                 [yogthos/config "1.1.1"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [cljs-ajax "0.7.4"]
                 [cljsjs/react-bootstrap "0.31.5-0"]
                 [cljsjs/d3 "4.12.0-0"]
                 [cljsjs/jquery "3.2.1-0"]
                 [cheshire "5.8.0"]
                 [hikari-cp "2.6.0"]
                 [com.layerware/hugsql "0.4.9"]
                 [mysql/mysql-connector-java "5.1.47"]
                 [org.apache.commons/commons-math3 "3.6.1"]
                 [net.mikera/core.matrix "0.62.0"]
                 [incanter/incanter-core "1.9.3"]
                 [javastat "1.4.0-beta"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [io.aviso/pretty "0.1.33"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]

  :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler viz-stra.handler/dev-handler}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  [figwheel-sidecar "0.5.16"]
                                  [com.cemerick/piggieback "0.2.2"]]
                   :source-paths ["src/cljs"]
                   :plugins [[lein-cljsbuild "1.1.7"]
                             [lein-figwheel "0.5.16"]
                             [lein-doo "0.1.10"]]}}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                 :init (do (use 'figwheel-sidecar.repl-api)
                           (start-figwheel!))
                 :timeout 120000}

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]
                :figwheel {:on-jsload "viz-stra.core/mount-root"}
                :compiler {:main viz-stra.core
                           :output-to "resources/public/js/compiled/app.js"
                           :output-dir "resources/public/js/compiled/out"
                           :asset-path "js/compiled/out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]
                           :external-config {:devtools/config {:features-to-install :all}}
                           ;; Added for foreign libs
                           :foreign-libs [{:file "resources/public/js/biochart.js"
                                           :provides ["cgis.biochart"]
                                           :requires ["cljsjs.d3"]
                                           :global-exports {cgis.biochart bio}}
                                          {:file "resources/public/js/kinetic-v5.1.0.js"
                                           :provides ["kinetic"]}
                                          {:file "resources/public/js/jnchlib-1.2.0.js"
                                           :provides ["inchlib"]}
                                          {:file "resources/public/js/jquery.qtip.js"
                                           :provides ["jquery.qtip"]}
                                          {:file "resources/public/js/highcharts.js"
                                           :provides ["highcharts"]}
                                          {:file "resources/public/js/highcharts-more.js"
                                           :provides ["highcharts.more"]
                                           :requires ["highcharts"]}
                                          {:file "resources/public/js/exporting.js"
                                           :provides ["highcharts.modules.exporting"]}
                                          {:file "resources/public/js/sankey.js"
                                           :provides ["highcharts.modules.sankey"]}]}}
               {:id "min"
                :source-paths ["src/cljs"]
                :jar true
                :compiler {:main viz-stra.core
                           :output-to "resources/public/js/compiled/app.js"
                           :optimizations :advanced
                           :closure-defines {goog.DEBUG false}
                           :pretty-print false
                           ;; Added for foreign libs
                           :foreign-libs [{:file "resources/public/js/biochart.js"
                                           :provides ["cgis.biochart"]
                                           :requires ["cljsjs.d3"]
                                           :global-exports {cgis.biochart bio}}
                                          {:file "resources/public/js/kinetic-v5.1.0.js"
                                           :file-min "resources/public/js/kinetic-v5.1.0.min.js"
                                           :provides ["kinetic"]}
                                          {:file "resources/public/js/jnchlib-1.2.0.js"
                                           :file-min "resources/public/js/jnchlib-1.2.0.min.js"
                                           :provides ["inchlib"]}
                                          {:file "resources/public/js/jquery.qtip.js"
                                           :file-min "resources/public/js/jquery.qtip.min.js"
                                           :provides ["jquery.qtip"]}
                                          {:file "resources/public/js/highcharts.js"
                                           :provides ["highcharts"]}
                                          {:file "resources/public/js/highcharts-more.js"
                                           :provides ["highcharts.more"]
                                           :requires ["highcharts"]}
                                          {:file "resources/public/js/exporting.js"
                                           :provides ["highcharts.modules.exporting"]}
                                          {:file "resources/public/js/sankey.js"
                                           :provides ["highcharts.modules.sankey"]}]
                           ;; Added for externs
                           :externs ["public/js/jnchlib-1.2.0.min.js"
                                     "public/js/biochart.js"
                                     "public/js/highcharts.ext.js"]
                           :closure-warnings {:externs-validation :off
                                              :non-standard-jsdoc :off}}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs"]
                :compiler {:main viz-stra.runner
                           :output-to "resources/public/js/compiled/test.js"
                           :output-dir "resources/public/js/compiled/test/out"
                           :optimizations :none}}]}

:main viz-stra.server

:aot [viz-stra.server]

:uberjar-name "CaPSSA.jar"

;:prep-tasks [["cljsbuild" "once" "min"] "compile"]
)

