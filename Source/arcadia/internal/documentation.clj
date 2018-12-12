(ns arcadia.internal.documentation
  (:require [clojure.string :as s])
  (:import [System.IO Path Directory]))

(defn- methods-named [t name]
  (->> t
       .GetMethods
       (filter #(= name (.Name %)))))

(defn unity-linkify-fn [[_ string]]
  (cond
    (.Contains string "/")
    (let [[type-name member] (s/split string #"/")]
      (if-let [type (clojure.lang.RT/classForName (str "UnityEngine." type-name))]
        (cond
          (.GetField type member)
          (str "[`" string "`](https://docs.unity3d.com/ScriptReference/" type-name "-" member ".html)")
          (.GetProperty type member)
          (str "[`" string "`](https://docs.unity3d.com/ScriptReference/" type-name "-" member ".html)")
          (> (count (methods-named type member)) 0)
          (str "[`" string "`](https://docs.unity3d.com/ScriptReference/" type-name "." member ".html)")
          :else
          (str "`" string "`"))))
    :else
    (if (clojure.lang.RT/classForName (str "UnityEngine." string))
      (str "[`" string "`](https://docs.unity3d.com/ScriptReference/" string ".html)")
      (str "`" string "`"))))

(defn unity-linkify [s]
  (s/replace s #"`([^`]*)`" #'unity-linkify-fn))

(defn print-docs [ns]
  (require ns :reload)
  (let [metas (->> (find-ns ns)
                   ns-publics
                   vals
                   (map meta)
                   (filter #(not (:private %)))
                   (filter #(:doc %)))]
    (doseq [{:keys [file line name arglists macro doc/no-syntax doc/syntax doc]}
            (sort-by :line metas)]
      (let [url (when file
                  (str "https://github.com/arcadia-unity/Arcadia/blob/beta/Source/"
                       (let [idx (s/last-index-of file "Assets/Arcadia/Source/")
                             file (if (not (nil? idx))
                                    (-> file (.Substring idx) (s/replace "Assets/Arcadia/Source/" ""))
                                    file)]
                         (-> file
                             (s/replace ".clj" "")
                             (s/replace "." "/")))
                       ".clj#L"
                       line))]
        (print (str "### [`" name "`](" url ") "))
        (when macro (print "*macro*"))
        (println)
        (when-not no-syntax
          (println "#### Syntax")
          (doseq [a (or syntax arglists)]
            (println (str "`" (pr-str (concat [(symbol name)] a)) "`  "))))
        (println "#### Description")
        (println (unity-linkify doc))
        (println "\n---\n")))))

(defn write-docs [file ns]
  (let [txt (with-out-str
              (print-docs 'arcadia.linear))]
    (spit file txt :encoding "utf8")))

(def public-namespaces
  '[arcadia.core
    arcadia.linear
    arcadia.debug
    arcadia.introspection])

(defn write-public-docs []
  (Directory/CreateDirectory "docs")
  (doseq [ns public-namespaces]
    (write-docs (Path/Combine "docs" (str ns ".md"))
                ns)))
