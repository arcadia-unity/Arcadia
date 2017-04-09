(ns arcadia.internal.benchmarking
  (:import
   [System TimeSpan]
   [System.Diagnostics Stopwatch]))

(defmacro timing [& body]
  `(let [^System.Diagnostics.Stopwatch sw# (System.Diagnostics.Stopwatch.)]
     (.Start sw#)
     (do ~@body)
     (.Stop sw#)
     (.TotalMilliseconds (.Elapsed sw#))))

(defmacro n-timing [n & body]
  `(let [n# ~n]
     (/ (timing
          (dotimes [_# n#]
            ~@body))
       n#)))
