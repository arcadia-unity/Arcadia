(ns arcadia.internal.seq-utils)

(defn seqable? [x]
  "Based on RT.cs, clojure.core/cast, clojure.core/instance. Uncertain whether it works in all cases"
  (or
    (nil? x)
    (instance? clojure.lang.ASeq x)
    (instance? clojure.lang.LazySeq x)
    (instance? clojure.lang.Seqable x)
    (array? x)
    (string? x)
    (instance? System.Collections.IEnumerable x)))
