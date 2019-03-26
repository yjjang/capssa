(ns viz-stra.logger
  (:require [io.aviso.ansi :refer :all]
            [clojure.tools.logging :as log]))

(defn- req-method [request]
  (-> (:request-method request)
      (name)
      (clojure.string/upper-case)))

(defn- get-req-body! [{:keys [body] :as request}]
  ;(println request)
  (if (nil? body) "" (slurp body)))

(defn- refill-req-with-body [request body-str]
  (assoc request :body (java.io.ByteArrayInputStream. (.getBytes body-str))))

(defn- get-status-color [status]
  (condp > status
    300 green
    400 red
    500 blue
    600 red
    :else red))

(defn- get-res-status [{:keys [status] :as response}]
  (str ((get-status-color status) status) ""))

(defn- get-req-method [request res-status]
  (let [color (get-status-color res-status)]
    (str (-> (req-method request) (color) (bold)) "")))

(defn- get-req-path [{:keys [uri] :as request}]
  (str (bold-cyan uri) ""))

(defn- get-body [body]
  (if (string? body)
    (str (if (empty? body)
           (cyan "[no body]")
           (bold-white body)) "")
    (bold-white (type body))))


(defn wrap-with-logger [handler]
  (fn [request]
    (let [req-body (get-req-body! request)
          req-orig (if (empty? req-body)
                     request
                     (refill-req-with-body request req-body))
          response (handler req-orig)]
      (if (:status response)
        (log/info (bold-white (:remote-addr request))
                  ;(.format (new java.text.SimpleDateFormat "[yyyy-MM-dd HH:mm:ss] ") (java.util.Date.))
                  ":"
                  (get-res-status response)
                  (get-req-method request (:status response))
                  (get-req-path request))
        (log/info (bold-red (:remote-addr request)) ":" (red "[NIL STATUS]")))
      response)))
