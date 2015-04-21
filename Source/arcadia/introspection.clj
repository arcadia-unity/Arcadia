(ns arcadia.introspection)

(def inclusive-binding-flag
  (enum-or
    BindingFlags/Static
    BindingFlags/Instance
    BindingFlags/Public
    BindingFlags/NonPublic))

(defn- type? [x]
  (instance? System.MonoType x))

(defn fuzzy-finder-fn [name-fn resource-fn]
  (fn [string-or-regex]
    (let [f (if (instance? System.Text.RegularExpressions.Regex string-or-regex)
              #(re-find string-or-regex (name-fn %))
              (let [^String s string-or-regex]
                #(let [^String n (name-fn %)]
                   (.Contains n s))))]
      (filter f (resource-fn)))))

;; (def fuzzy-materials
;;   (fuzzy-finder-fn
;;     (fn [^Material m]
;;       (.name m))
;;     #(Resources/FindObjectsOfTypeAll Material)))

(defn fuzzy-methods [^Type t, sr]
  ((fuzzy-finder-fn
     (fn [^System.Reflection.MonoMethod m]
       (.Name m))
     #(.GetMethods t inclusive-binding-flag))
   sr))

;; hackishly disable stupid paredit-killing thing until I have time to
;; fix that problem correctly in emacs. Also left-justify the columns
;; because we aren't fucking animals.
(defn print-table-2
  "Prints a collection of maps in a textual table. Prints table headings
   ks, and then a line of output for each row, corresponding to the keys
   in ks. If ks are not specified, use the keys of the first item in rows."
  {:added "1.3"}
  ([ks rows]
     (let [s (with-out-str
               (when (seq rows)
                 (let [widths (map
                                (fn [k]
                                  (apply max (count (str k)) (map #(count (str (get % k))) rows)))
                                ks)
                       spacers (map #(apply str (repeat % "-")) widths)
                       fmts (map #(str "%-" % "s") widths)
                       fmt-row (fn [leader divider trailer row]
                                 (str leader
                                   (apply str (interpose divider
                                                (for [[col fmt] (map vector (map #(get row %) ks) fmts)]
                                                  (format fmt (str col)))))
                                   trailer))]
                   (println)
                   (println (fmt-row "| " " | " " |" (zipmap ks ks)))
                   (println (fmt-row "|-" "-+-" "-|" (zipmap ks spacers)))
                   (doseq [row rows]
                     (println (fmt-row "| " " | " " |" row))))))]
       (print
         (if (odd? (count (re-seq #"\|" s)))
           (str s "|")
           s))))
  ([rows] (print-table-2 (keys (first rows)) rows)))



;; update all this to use fuzzy finders

(defn print-properties [x & opts]
  (let [^Type t (if (type? x) x (class x))]
    (->> (apply clojure.reflect/reflect t opts)
      :members
      (filter #(instance? clojure.reflect.Property %))
      (sort-by :name)
      (print-table-2))))

(defn print-fields [x]
  (->> (.GetFields x)
    (map (fn [^System.Reflection.MonoField f]
           (.Name f)))
    (map println)
    doall))

;; check this scoops up statics etc
(defn print-methods [x & opts]
  (let [^Type t (if (type? x) x (class x))]
    (->> (apply clojure.reflect/reflect t opts)
      :members
      (filter #(instance? clojure.reflect.Method %))
      (sort-by :name)
      (print-table-2 [:name :return-type :parameter-types :flags :declaring-class]))))
