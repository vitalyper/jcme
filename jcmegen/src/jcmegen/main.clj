(ns
  #^{:author "Vitaly Peressada",
     :doc "Main program: generates input, invokes httpclient, processes results
          and prints the report."} 
  jcmegen.main
  (:gen-class)
  (:use 
    [clojure.contrib.command-line]
    [clojure.pprint] 
    [clojure.contrib.math :only (abs)])
  (:require 
    [clojure.contrib.json :as ccjs]
    [clojure.contrib.duck-streams :as ds :only (to-byte-array)]
    [jcmegen.client :as jcl]
    [jcmegen.core :as jcr]
    [jcmegen.inputgen :as jin])
  (:import 
    [java.io InputStream]
    [java.net Socket])
)

(def 
  #^{:doc "Global configuration map
          loaded from external file."}
  *config*)

(defonce 
  #^{:doc "Constant of buy vs. sell parity."}
  *buy-sell-parity* 1.0)

(def *env*
  {
   :java {:results []}
   :clj {:results []}
   })

(def *processes* '())

(defn load-config
  "Loads config file specified by command line arg."
  [fname]  
    (do 
      (jcr/run-with-msg "Loading config file"
        (def *config* (load-file fname)))
      (println "Input config")
      (pprint *config*)
      *config*))

(defn calc-max 
  "Calculates buy and sell max from passed in sequece."
  [rand-seq]
  (let [get-max-fun
        (fn [rand-seq side]
          (apply max
            (map #(last %1)
               (filter 
                 #(. (first %1) startsWith side)
                 rand-seq))))]
    [(get-max-fun rand-seq "B") (get-max-fun rand-seq "S")]))

(defn verify-max 
  "Verifies max for a side against web service url"
  [base-url side before]
  (let [after (Float/parseFloat (jcl/get-max base-url side))]
    (when-not (= before after)
      (throw (RuntimeException. 
           (format "before max %f != after %f for %s." before after side))))))

(defn verify-extra 
  "Verifies before extra against web service url. 
  After match is completed extras should match."
  [before-extra after-extra]
  (when-not (= (apply sorted-set before-extra) 
               (apply sorted-set after-extra))
    (throw (RuntimeException. 
         (format "before extra %s != after %s." 
                 (with-out-str (pprint before-extra)) (with-out-str (pprint after-extra)))))))

(defn avg-mtrc 
  "Calculates passed metric from vector of maps"
  [v-of-maps mtrc-key]
  (/ 
    (reduce
      +
      (map #(mtrc-key %1) v-of-maps))
    (count v-of-maps)))

(defn print-results [iter-count] 
  (doseq [impl (keys *env*)]
    (printf "%s averages in millis over %d iteration/s. avg match: %.2f, total: %.2f.%n"
            impl, 
            iter-count, 
            (avg-mtrc (:results (impl *env*)) :avg), 
            (avg-mtrc (:results (impl *env*)) :total))))

(defn shutdown [] 
  (shutdown-agents))

(defn stop-processes [] 
  (doseq [ {:keys [j p]} *processes*]
    (printf "Destroying process %s%n" j)
    (. p destroy)
    (. p waitFor)))

(defn connect-to-socket 
  "Opens soket to verify that server is up.
  On my MacBook Air sleep of 1 sec was not enough - 
  (with-open ... was blocking indefinetly."
  ([host port] (connect-to-socket host port 5000))
  ([host port millis-to-sleep]
   (Thread/sleep millis-to-sleep)
    (try 
      (with-open [sckt (Socket. host port)]
        (not (= nil (. sckt getInputStream))))
      (catch Throwable t
        (printf "Failed to connect to %s:%d%n%s%n" host port t)
        false))))

(defn start-process [jar url]
  (let [tkns (re-seq #"http://(\w+):(\d+)" url)
        host (first (rest (first tkns)))
        port (Integer/parseInt (last (first tkns)))
        pb (ProcessBuilder. (into-array ["java" "-jar" (str "bin/" jar) "--url" url]))]
    (doto pb
      (.redirectErrorStream true)
      ; use same working dir
      (.directory nil))
    (let [prcs (. pb start)
          is-started (connect-to-socket host port)]
      (if is-started
        (def *processes* (conj *processes* {:p prcs, :j jar} ))
        (throw (Exception. 
                 (printf "Failed to start process on %s%n%s" 
                    url
                    (String. (ds/to-byte-array (. prcs getInputStream))))))))))

(defn send-verify 
  "Sends generated sequence for matching to web service.
  Verifies correctnes and stores results."
  [config rand-seq extra-seq max-buy max-sell impl]
  (let [n (:repeat-times config)
        base-url (:url (impl config))
        jar (:jar (impl config))]
    (printf "Starting server process %s with jar %s at %s%n" impl jar base-url)
    (start-process jar base-url)
    (printf "Running %d iterations for %s against %s%n" n impl base-url)
    (flush)
    (doseq [i (range n)] 
      (jcl/post-seq-multi base-url rand-seq)
      (do
        (verify-max base-url "b" max-buy)
        (verify-max base-url "s" max-sell)
        (verify-extra 
          extra-seq 
          (map #(float %1) (ccjs/read-json (jcl/get-extra base-url)))))
          ((fn [] 
            (let [results (ccjs/read-json (jcl/get-results base-url))]
              (jcl/get-reset base-url)
              (def *env* (update-in *env* [impl :results] conj results))))))))

(defn -main
  "Loads configuration from command line.
  Generates input. Calls send-verify for keys in *env* map"
  ([] (-main "--input-file" "/Users/spiceworks/Dropbox/clojure/jcme/config.clj"))
  ([& args]
  (with-command-line args
    "Agruments spec for inputgen"
    [[input-file "Input file with config settings" "config.clj"]]
        (let [config (load-config input-file)
            mtmp (jin/gen-input config *buy-sell-parity*)
            rand-seq (:input mtmp)
            extra-seq (:remain mtmp)
            [max-buy max-sell] (calc-max rand-seq)]
          (printf "Generated %d input entries and %d extra values %n" 
            (count rand-seq)
            (count extra-seq))
          (doseq [impl (keys *env*)]
            (send-verify config rand-seq extra-seq max-buy max-sell impl))
          (stop-processes)
          (print-results (:repeat-times config))
          (shutdown)))))

; script based invocation
(apply -main *command-line-args*)

