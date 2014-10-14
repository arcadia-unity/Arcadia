(ns arcadia.docs
  (:use clojure.repl
        clojure.pprint
        arcadia.core)
  (:require clojure.string))

(defn doc-metas [ns]
  (remove #(empty? (:doc %))
          (map (comp meta resolve) (dir-fn ns))))

(defn print-toc [ns]
  (require ns)
  (doseq [m (doc-metas ns)]
    (println (str "* [" (:name m) "](#" (:name m) ")"))))

(defn print-docs [ns]
  (require ns)
  (doseq [m (doc-metas ns)]
    (println)
    (println (:name m))
    (println (clojure.string/join
      (map (constantly "-") (range (count (str (:name m)))))))
    (println "```clj")
    (doseq [args (sort (:arglists m))]
      (println
        (if (empty? args)
          (str "(" (:name m) ")")
          (str "(" (:name m) " "
               (clojure.string/join " " args) ")"))))
    (println "```")
    (println (str "  " (:doc m)))))