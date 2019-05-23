(ns viz-stra.utils)

(defn save-as-text [data file-name]
  (let [a (.createElement js/document "a")]
    (doto a
      (.setAttribute "href" (str "data:text/plain;charset=utf-8," (js/encodeURIComponent data)))
      (.setAttribute "download" file-name))
    (set! (-> a .-style .-display) "none")
    (.appendChild js/document.body a)
    (.click a)
    (.removeChild js/document.body a)))

