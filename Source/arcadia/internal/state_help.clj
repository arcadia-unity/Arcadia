(ns arcadia.internal.state-help
  (:require arcadia.literals)
  (:import [UnityEngine Debug]
           ArcadiaState))

(defn awake [^ArcadiaState as]
  (let [state (.state as)]
    (.BuildDatabaseAtom as true)
    (let [objdb (.objectDatabase as)]
      (binding [arcadia.literals/*object-db* objdb
                *data-readers* arcadia.literals/the-bucket]
        (try
          ;; this weirdness is what it does in the C#:
          (set! (.state as)
            (atom (read-string (.edn as))))
          (catch Exception e
            (Debug/Log "Exception encountered in ArcadiaState.Awake:")
            (Debug/Log e)
            (Debug/Log "arcadia.literals/*object-db*:")
            (Debug/Log arcadia.literals/*object-db*)
            (throw e)))))))
