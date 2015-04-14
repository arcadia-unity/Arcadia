(ns arcadia.linear
  (:use arcadia.core)
  (:require [arcadia.internal.meval :as mvl
             clojure.zip :as zip])
  )


;; (defn- dedup-by [f coll]
;;   (map peek (vals (group-by f coll))))

(comment
  (defmacro ^:private sig-match [args env & clauses]
  (let [rs (take-nth 2 clauses)
        cs (->> (apply map vector rs)
             (sort-by #(count (set %)))
             vec)
        ttwist (fn [i]
                 ())]
    ()))

  (something args env
    [Vector3 Float]
    [Float Vector3]))

