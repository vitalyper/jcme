(ns 
  #^{:author "Vitaly Peressada",
     :doc "Generates input based on passed in configuration."} 
  jcmegen.inputgen 
  (:use 
    [clojure.contrib.math :only (abs)]
    [jcmegen.core]
  ))

(defn gen-n-floats 
  "Generates N random distinct floats.
  Uses rand-int for efficiency."
  [max-float how-many]
  (map #(float (/ % 100.0)) 
       (take how-many (distinct (repeatedly 
          #(rand-int (int (* max-float 100))))))))

(defn gen-floats 
  "Based on passed in configuration
  builds return map of intermediate data."
  [config-prcnt buy-sell-parity config-how-many max-float]
  (let [par-count (* (rationalize (min config-prcnt buy-sell-parity)) config-how-many)
        excess-side (cond
          (> config-prcnt buy-sell-parity) "B"
          (< config-prcnt buy-sell-parity) "S"
          :else "N")
        excess-count (abs (int (* config-how-many 
          (- (rationalize buy-sell-parity) (rationalize config-prcnt)))))]
    {:excess-side excess-side,
     :parity-floats (gen-n-floats max-float par-count)
     :excess-floats (gen-n-floats max-float excess-count)}))

(defn from-flts-to-seq 
  "Builds sequence based on intermediate data from passed in map."
  [flts-map]
  (let [
    ff (fn [prefix nbr] (format "%s%d" prefix nbr))
    imf (fn [prefix]
      (map #(vector (ff prefix %2) %1) 
           (:parity-floats flts-map) 
           (iterate inc 1)))
    ps (apply concat (map #(imf %1) ["B" "S"]))]
    (concat 
      ps
      (map #(vector (ff (:excess-side flts-map) %2) %1) 
           (:excess-floats flts-map) 
           (iterate inc (+ (/ (count ps) 2) 1))))))

(defn gen-input[conf-map buy-sell-parity]
  "Orchestrates generaration of result sequence
  and returns it."
  (let [flts-map (run-with-msg "Generating random input" (gen-floats 
           (:buy-vs-sell-length conf-map) 
           buy-sell-parity 
           (:how-many conf-map)
           (:max-price conf-map)))
        rand-seq (run-with-msg "Converting input to requested format" 
           (shuffle (from-flts-to-seq flts-map)))]
        {:input rand-seq
         :remain (:excess-floats flts-map)}))
