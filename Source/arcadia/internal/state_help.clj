(ns arcadia.internal.state-help
  (:require arcadia.literals
            ;; just for deserialized-var-form?, var-form->var
            [arcadia.internal.hook-help :as hh])
  (:import [UnityEngine Debug]
           [Arcadia JumpMap JumpMap+KeyVal JumpMap+PartialArrayMapView]
           ArcadiaState))

(defn jumpmap-to-map [^JumpMap jm]
  (persistent!
    (reduce (fn [m, ^JumpMap+KeyVal kv]
              (assoc! m (.key kv) (.val kv)))
      (transient {})
      (.. jm KeyVals))))

(defn convert-vars
  "converts (quote (var xyz)) to #xyz"
  [x]
  (clojure.walk/prewalk
    (fn [x]
      (if (hh/deserialized-var-form? x)
        (hh/var-form->var x)
        x))
    x))

(defn deserialize [^ArcadiaState as]
  (.BuildDatabaseAtom as true)
  (let [objdb (.objectDatabase as)]
    (binding [arcadia.literals/*object-db* objdb
              *data-readers* (if-not (empty? *data-readers*)
                               (merge *data-readers* arcadia.literals/the-bucket)
                               arcadia.literals/the-bucket)]
      (try
        (let [^JumpMap jm (.state as)]
          (.Clear jm)
          (.AddAll jm
            (convert-vars
              (read-string (.edn as)))))
        (catch Exception e
          (Debug/Log "Exception encountered in arcadia.internal.state-help/deserialize:")
          (Debug/Log e)
          (Debug/Log "arcadia.literals/*object-db*:")
          (Debug/Log arcadia.literals/*object-db*)
          (throw e))))))

(defn initialize [^ArcadiaState as]
  (deserialize as)
  (.RefreshAll as))
