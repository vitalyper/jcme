(ns
  #^{:author "Vitaly Peressada",
     :doc "Sends data to a web service."} 
  jcmegen.client
  (:use 
    [jcmegen.core]
  )
  (:require 
    [clj-http.client :as hcl]
    [clj-http.core :as hcore]
    [clojure.contrib.json :as js]
  )
)

(def *dflt-cntnt-tp* "text/plain")

(def *req-map*
  {:reset {:method :get, :path "/reset"},
   :match {:method :post, :path "/match", :content-type :json},
   :results {:method :get, :path "/results"}
   :extra {:method :get, :path "/extra"}
   :max {:method :get, :path "/max/"}})

(defn req-pipeline
  [request]
  (let [pc (hcore/pooled-http-client)]
  (-> request
      (hcl/wrap-client ,,, pc)
      hcl/wrap-redirects
      hcl/wrap-exceptions
      hcl/wrap-decompression
      hcl/wrap-input-coercion
      hcl/wrap-output-coercion
      hcl/wrap-query-params
      hcl/wrap-basic-auth
      hcl/wrap-accept
      hcl/wrap-accept-encoding
      hcl/wrap-content-type
      hcl/wrap-method
      hcl/wrap-url)))

(def req-fun (req-pipeline #'hcore/request))

(defn exec-http 
  ([method path] (exec-http method path *dflt-cntnt-tp* "no url"))
  ([method path content-type base-url] 
   (let [rf (fn
      [bd]
        (let [resp
          (req-fun
            {
             :throw-exceptions true
             :method method
             :url (str base-url path)
             :content-type content-type
             :body bd})]
            resp))]
     (fn 
       ([] (rf nil))
       ([bd] (rf bd))))))

(defn build-request 
  ([req-map req-key base-url] (build-request req-map req-key base-url ""))
  ([req-map req-key base-url extra-uri] 
  (when-not (contains? req-map req-key )
    (throw (IllegalArgumentException. (format "Request %s is not found in request map." req-key))))
  (let [ {m :method, p :path, ct :content-type} (req-key req-map) 
        eff-ct (or ct *dflt-cntnt-tp*)
        full-uri (str p extra-uri)]
  (exec-http m full-uri eff-ct base-url))))

(defn post-seq-single [base-url rand-seq]
    (let [match-req (build-request *req-map* :match base-url)
          good-cnt 0]
      (doseq [item rand-seq]
        (let [resp (match-req (js/json-str {:id (first item) :price (last item)}))]
          (if (= 200 (:status resp))
            (inc good-cnt)
            (println resp))))
      good-cnt))

(defn post-seq-multi [base-url rand-seq]
    (let [match-req (build-request *req-map* :match base-url)]
      (as-futures [item rand-seq]
        (match-req (js/json-str {:id (first item) :price (last item)}))
        :as results
      =>
        (reduce (fn [total res] 
                  (+ total (if (= 200 (:status @res)) 1 0)))
                0
                results))))

(defn get-max [base-url extra-uri]
  (let [resp ((build-request *req-map* :max base-url extra-uri))]
    (:body resp)))

(defn get-extra [base-url]
  (let [resp ((build-request *req-map* :extra base-url))]
    (:body resp)))

(defn get-reset [base-url]
  (let [resp ((build-request *req-map* :reset base-url))]
    (:body resp)))

(defn get-results [base-url]
  (let [resp ((build-request *req-map* :results base-url))]
    (:body resp)))
