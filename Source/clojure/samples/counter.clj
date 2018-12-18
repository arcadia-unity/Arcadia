(ns clojure.samples.counter)

(defn tt [x y] (clojure.lang.Util/identical x y))

(def s (apply str (repeat 20 "This is a really long string"))) 

(set! *unchecked-math* true)

(defn count-num-chars ^long [^String s] 
  (let [l (.Length s) 
        c \space] 
    (loop [i 0 acc 0] 
      (if (< i l) 
        (recur (inc i) 
               (if (identical? (.get_Chars s i) c) acc 
                   (inc acc))) 
        acc)))) 


(defn cnc [n]
  (dotimes [_ n] (count-num-chars s)))

(defn f []
  (let [sw (System.Diagnostics.Stopwatch.)
        nanosec-per-tick (/ 1000000000 System.Diagnostics.Stopwatch/Frequency)]
	(.Start sw)
	(dotimes [_ 1000]
	   (count-num-chars s))
    (.Stop sw)
	(println "Time (nsec): " (* (.ElapsedTicks sw) nanosec-per-tick))))

(defn g [n]
  (time (cnc n)))

