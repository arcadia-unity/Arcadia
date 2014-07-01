(ns test-clj-clr.evaluation
  (:use clojure.repl clojure.pprint))

(defn evaluate-data [d]
  (try
    (with-out-str
      (pr
        (eval
          (read-string d))))
    (catch Exception e
      "looks like we hit some sort of exception or something I guess")))
