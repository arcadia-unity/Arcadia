(ns arcadia.internal.nrepl-support
  (:require
   [arcadia.internal.autocompletion :as ac]
   [arcadia.introspection :as i])
  (:import [BList]
           [BDictionary]))

(defn bencode-completion-result
  "Converts a seq of completion maps into a BList of BDictionary"
  [completions]
  (let [blist (BList.)]
    (doseq [candidate completions]
      (.Add blist (doto (BDictionary.)
                    (.Add "candidate" candidate))))
    blist))

(defn complete [^String prefix]
  (bencode-completion-result
   (ac/completions prefix)))


(defn eldoc-methods
  "Returns eldoc info data
  for static class members."
  [symbol-str]
  (when-let
      [sym (symbol symbol-str)]
      (when-let
          [ns (namespace sym)])
      (let [methods
            (i/methods-report
             (resolve (symbol (namespace sym)))
             (re-pattern (format "^%s$" (name sym))))]
        (when (seq methods)
          {:name
           (:name (first methods))
           :arglists
           (mapv
            (fn [m]
              (into [(:return-type m)] (:parameters m))) methods)}))))

;; and now provide the doc string
;; by checking the assembly xml


(comment
  (eldoc-methods "fo")
  (symbol "fo")
  (namespace nil)
  )
