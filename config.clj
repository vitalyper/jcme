; Configuration for input generator (jcmegen)
{:buy-vs-sell-length 0.9 
 :how-many 10
 :min-price 0.0 
 :max-price 100.0
 :udp-port 42111
 :udp-timeout-millis 5000

 :repeat-times 50
 :clj {:url "http://localhost:4211" :jar "jcmec.jar"}
 :java {:url "http://localhost:4212" :jar "jcmej.jar"}}
