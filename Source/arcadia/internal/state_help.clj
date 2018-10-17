(ns arcadia.internal.state-help
  (:require arcadia.data
            ;; just for deserialized-var-form?, var-form->var
            [arcadia.internal.hook-help :as hh]
            [clojure.edn :as edn])
  (:import [UnityEngine Debug]
           [Arcadia JumpMap JumpMap+KeyVal JumpMap+PartialArrayMapView]
           ArcadiaState))

(defn jumpmap-to-map [^JumpMap jm]
  (persistent!
    (reduce (fn [m, ^JumpMap+KeyVal kv]
              (assoc! m (.key kv) (.val kv)))
      (transient {})
      (.. jm KeyVals))))

(defn deserialize [^ArcadiaState as]
  (.BuildDatabaseAtom as true)
  (let [objdb (.objectDatabase as)]
    (binding [arcadia.data/*object-db* objdb]
      (try
        (let [^JumpMap jm (.state as)]
          (.Clear jm)
          (.AddAll jm
            (edn/read-string {:readers *data-readers*} (.edn as))))
        (catch Exception e
          (Debug/Log "Exception encountered in arcadia.internal.state-help/deserialize:")
          (Debug/Log e)
          (Debug/Log "arcadia.data/*object-db*:")
          (Debug/Log arcadia.data/*object-db*)
          (throw (Exception. "wrapper" e)))))))

(defn initialize [^ArcadiaState as]
  (deserialize as)
  (.RefreshAll as))
