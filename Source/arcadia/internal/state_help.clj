(ns arcadia.internal.state-help
  (:require arcadia.literals
            ;; just for deserialized-var-form?, var-form->var
            [arcadia.internal.hook-help :as hh])
  (:import [UnityEngine Debug]
           ArcadiaState))

(defn awake [^ArcadiaState as]
  (let [state (.state as)]
    (.BuildDatabaseAtom as true)
    (let [objdb (.objectDatabase as)]
      (binding [arcadia.literals/*object-db* objdb
                *data-readers* (if-not (empty? *data-readers*)
                                 (merge *data-readers* arcadia.literals/the-bucket)
                                 arcadia.literals/the-bucket)]
        (try
          ;; new atom ensures clones made via .instantiate don't share the same atom
          (set! (.state as)
            (atom
              ;; need to convert (quote (var xyz)) to #'xyz
              (clojure.walk/prewalk
                (fn [x]
                  (if (hh/deserialized-var-form? x)
                    (hh/var-form->var x)
                    x))
                (read-string (.edn as)))))
          (catch Exception e
            (Debug/Log "Exception encountered in ArcadiaState.Awake:")
            (Debug/Log e)
            (Debug/Log "arcadia.literals/*object-db*:")
            (Debug/Log arcadia.literals/*object-db*)
            (throw e)))))))
