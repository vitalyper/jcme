(ns jcmec.core
  (:gen-class)
  (:use 
    [ring.adapter.jetty]
    [ring.middleware.reload]
    [ring.middleware.stacktrace]
    [net.cgrand.moustache]
    [clojure.contrib.command-line]
    [clojure.contrib.json]
  )
  (:require 
    [clojure.pprint :as pp]
    [clojure.contrib.duck-streams :as ds :only (to-byte-array)]
  )
  (:import 
    [java.io PrintStream FileOutputStream]
    [org.apache.log4j Logger Level PatternLayout Appender DailyRollingFileAppender]
    [java.net Socket InetAddress DatagramSocket DatagramPacket]
  )
)

(def 
  #^{:doc
  "Jetty for stop/start control"}
  *jetty*)

(def 
  #^{:doc
  "Log4j root logger."}
  *logger*)

(def #^{:doc
  "Log4j levels"}
  *log-levels* {:debug Level/DEBUG, :info Level/INFO, :warn Level/WARN, :error Level/ERROR })

(defn init-max []
  {:b (float 0.0), :s (float 0.0)})

(defn init-refs [b-ref s-ref] 
  {:b b-ref, :s s-ref})

(defn init-stats []
  {:cnt 0, :min 999999.0, :max 0.0, :avg 0.0, :total 0.0 })

(def *max! (ref (init-max)))
(def *sides* {:b :s, :s :b})
(def *buy! (ref {}))
(def *sell! (ref {}))
(def *refs* (init-refs *buy! *sell!))
(def *stats! (ref (init-stats)))
(def *logging* true)

(defn log [level msg]
  (if *logging*
    (. *logger* log (level *log-levels*) msg)))

(defn istream-to-string [src]
  (String. (ds/to-byte-array src)))

(defn success-resp [resp-map body]
  (merge resp-map {:status 200, :body body}))

; Not used. Modify to parellelize update max and updating buy/sell refs.
; Did not appear to be faster.
;(defn update-max [side price]
;    (dosync 
;      (let [max-float (side @*max!)]
;        (if (> price max-float)
;            (alter *max! assoc side price)))))
;
;(defn update-maps [side price id]
;    (dosync 
;        ; only add to passed in side if other doesn't have it
;        (when-not (contains? @((side *sides*) *refs*) price)
;            (alter (side *refs*) assoc price id))
;        ; always remove from other side
;        (alter ((side *sides*) *refs*) dissoc price)))
;
;(defn match2 
;  "Parallelize version of match. Appears to be slower."
;  [side price id] 
;  {:pre [(keyword? side)
;         (= (class price) java.lang.Double)]}
;  (let [new-float (. price floatValue)]
;    (pcalls
;      #(update-max side new-float)
;      #(update-maps side new-float id))
;    (log :info (format "match. side %s, new-float: %f, max-float: %f, in-sz-a: %d, other-sz-a: %d" 
;                 side price (side @*max!) (count (deref(side *refs*))) (count (deref((side *sides*) *refs*)))))))

(defn match 
  "Updates max value for a side"
  [side price id] 
  {:pre [(keyword? side)
         (= (class price) java.lang.Double)]}
  (let [new-float (. price floatValue)]
    (dosync 
      (let [max-float (side @*max!)]
        (if (> new-float max-float)
            (alter *max! assoc side new-float)))
      ; only add to passed in side if other doesn't have it
      (when-not (contains? @((side *sides*) *refs*) new-float)
          (alter (side *refs*) assoc new-float id))
      ; always remove from other side
      (alter ((side *sides*) *refs*) dissoc new-float))
  (log :debug (format "match. side %s, new-float: %f, max-float: %f, in-sz-a: %d, other-sz-a: %d" 
               side new-float (side @*max!) (count (deref(side *refs*))) (count (deref((side *sides*) *refs*)))))))

(defn def-handler [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Todo: write endpoints summary"})

(defn update-stats [{:keys [cnt min max avg total]} new-drtn]
          {:cnt (inc cnt)
          :total (+ total new-drtn)
          :min (if (> min new-drtn) new-drtn min)
          :max (if (> new-drtn max) new-drtn max)
          :avg (/ (+ total new-drtn) (inc cnt))})

(defn record-stats[st et]
  (let [drtn (/ (double (- et st)) 1000000.0)]
    (dosync
      (alter *stats! update-stats drtn))
    (log :debug (with-out-str (pp/pprint @*stats!)))))

(defn with-stats [f]
  (fn [& args]
    (let [st (. System (nanoTime))]
      (apply f args)
      ; use future to record stats on seperate thread 
      (future (record-stats st (. System (nanoTime)))))))

(defn post-match [req]
  (let [in-entry (read-json (istream-to-string (:body req)))
        side (->
           (:id in-entry)
           (. ,,, substring 0 1)
           (. ,,, toLowerCase)
           keyword)]
    (match side (:price in-entry) (:id in-entry))
    (success-resp {:headers {"Content-Type" "text/plain"}} "ok")))

(defn post-match-with-stats [req]
  (binding [post-match (with-stats post-match)]
    (post-match req)))

(defn get-reset [req]
  (dosync
    (ref-set *max! (init-max))
    (ref-set *buy! {})
    (ref-set *sell! {})
    (ref-set *stats! (init-stats))
    (def *refs* (init-refs *buy! *sell!)))
  (log :debug "Reset refs.")
  (success-resp {:headers {"Content-Type" "text/plain"}} "ok"))

(defn get-extra [req]
    (let [json-resp
          (-> 
            (concat (keys @*buy!) (keys @*sell!))
            json-str)]
  (log :debug (format "Returing %d extras" (count (concat (keys @*buy!) (keys @*sell!)))))
  (success-resp {:headers {"Content-Type" "text/plain"}} json-resp)))

(defn get-max [#^String side] 
  (let [side-lower (. side toLowerCase)
        max-val ((keyword side-lower) @*max!)]
  (log :debug (format "max for %s is %f" side max-val))
  (success-resp {:headers {"Content-Type" "text/plain"}} (str max-val))))

(defn get-results [req]
  (success-resp {:headers {"Content-Type" "text/plain"}} (json-str @*stats!)))

(def jcmec-webapp 
  (app
    ; wrap-reload causes ref reset to default value
    ;(wrap-reload '(jcmec.core))
    (wrap-stacktrace)
    [] def-handler
    [&]
      (app
        ["match" &]
          (app :post
            (fn [req] (post-match-with-stats req)))
        ["reset" &]
          (app :get
           (fn [req] (get-reset req)))
        ["results" &]
          (app :get
           (fn [req] (get-results req)))
        ["extra" &]
          (app :get
           (fn [req] (get-extra req)))
        ["max" side]
           (fn [req] (get-max side))
        )))

(defn start-jetty [url]
  (let [port (last (seq (. url split ":")))]
    (log :info (printf "Starting jetty on port %s" port))
    (def *jetty* (run-jetty #'jcmec-webapp {:port (Integer/parseInt port) :join? false}))))

(defn stop-jetty []
  (. *jetty* stop))

(defn setup-log4j
  "Sets up log4j root looger with file and level.
  Expects log4j jar to be on a classpath."
  [{:keys [fname level] :or {level (:info *log-levels*)}}] 
    (let [ptrn (PatternLayout. "%d{ISO8601} %-5p [%t]: %m %n")
         appender (DailyRollingFileAppender. ptrn fname "'.'yyyy-MM-dd")]
     (defonce *logger* (Logger/getRootLogger))
     (doto *logger*
       (.removeAllAppenders)
       (.setLevel level)
       (.addAppender appender))
      (printf "Logging to %s.%n" (. appender getFile))
     *logger*))

(defn get-home-dir []
  (first (filter #(not (= nil %1))
     (map #(System/getenv %1) ["HOME" "HOMEPATH"]))))

(defn send-start-msg [url udp-port]
  (let [socket (DatagramSocket.)
        buf (byte-array (. url getBytes))
        packet (DatagramPacket. buf (. buf length) (InetAddress/getLocalHost)(Integer/parseInt udp-port))]
    (with-open [s socket]
      (.s send packet))
    (log :info (printf "Sent started msg %s to udp port %s%n" url udp-port))))

(defn -main [& args]
  (let [log-fname (str (get-home-dir) "/jcmec.log")]
    (with-command-line args
      "Agruments spec"
      [[url "Url to listen on" "http://localhost:4211"]
       [udp-port "Udp port to send start msg to" "42111"]]
        (do
          (setup-log4j {:fname log-fname, :level (:info *log-levels*)})
          (start-jetty url)
          (send-start-msg url udp-port)))))
