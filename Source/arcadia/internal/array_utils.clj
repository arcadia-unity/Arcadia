(ns arcadia.internal.array-utils)

(defmacro lit-array [t & contents]
  (let [len (count contents)
        arsym (with-meta (gensym "array_")
                {:tag (symbol
                        (.FullName
                          (type (. Array (CreateInstance (resolve t) 0)))))})
        assgns (->> contents
                 (map-indexed
                   (fn [n content]
                     `(aset ~arsym ~n ~content))))]
    `(let [~arsym (. Array (CreateInstance ~t ~len))]
       ~@assgns
       ~arsym)))
