(defproject jcmec "0.0.1"
  :description "Clojure web service on top of ring."
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [ring/ring-core "0.3.8"]
                 [ring/ring-jetty-adapter "0.3.8"]
                 [net.cgrand/moustache "1.0.0"]
                 [log4j/log4j "1.2.16"]]
  :dev-dependencies
                  [[ring/ring-devel "0.3.8"]]
  :main jcmec.core)
            
