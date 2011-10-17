(ns 
  #^{:author "Vitaly Peressada",
     :doc "Common code for [j]ava [c]lojure [m]atching [e]ngine."} 
  jcmegen.core)

(defmacro run-with-msg 
  "Prints passed in message and \"done\" before and after
  invoking a body. Returns result of body invocation if not nil."
  [msg & body] 
  `(do 
     (printf "%s..." ~msg)
     (. *out* flush)
     (let [res# ~@body] 
       (println "done") 
       (if (not (nil? res#)) res#)))) 

(defmacro as-futures 
  "From book \"Joy of Clojure\" listing 11.8
  Takes a sequence, dispatches enclosed actions 
  across futures and runs task against results of futures."
  [[a args] & body]
    (let  [parts          (partition-by #{'=>} body)
          [acts _ [res]]  (partition-by #{:as} (first parts))
          [_ _ task]      parts]
    `(let [~res (for [~a ~args] (future ~@acts))]
       ~@task)))

