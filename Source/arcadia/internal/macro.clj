(ns arcadia.internal.macro)

(def alphabet
  (mapv str
    (sort "qwertyuiopasdfghjklzxcvbnm")))

;; ungry-bettos!

(defn classy-arg-strs
  ([]
   (for [n (cons nil (drop 2 (range)))
         c alphabet]
     (str c n)))
  ([n]
   (take n (classy-arg-strs))))

(defn classy-args
  ([] (map symbol (classy-arg-strs)))
  ([n] (map symbol (classy-arg-strs n))))

;; ============================================================
;; condcast->
;; ============================================================

(defn- maximize
  ([xs]
   (maximize (comparator >) xs))
  ([compr xs]
   (when (seq xs)
     (reduce
       (fn [mx x]
         (if (= 1 (compr mx x))
           x
           mx))
       xs))))

(defn- most-specific-type ^Type [& types]
  (maximize (comparator same-or-subclass?)
    (remove nil? types)))

(defn- contract-condcast-clauses [expr xsym clauses env]
  (let [etype (most-specific-type
                (type-of-reference expr env)
                (tag-type xsym))]
    (if etype
      (if-let [[_ then] (first
                          (filter #(= etype (ensure-type (first %)))
                            (partition 2 clauses)))]
        [then]
        (->> clauses
          (partition 2)
          (filter
            (fn [[t _]]
              (same-or-subclass? etype (ensure-type t))))
          (apply concat)))
      clauses)))

;; note this takes an optional default value. This macro is potentially
;; annoying in the case that you want to branch on a supertype, for
;; instance, but the cast would remove interface information. Use with
;; this in mind.
(defmacro condcast-> [expr xsym & clauses]
  (let [[clauses default] (if (even? (count clauses))
                            [clauses nil] 
                            [(butlast clauses)
                             [:else
                              `(let [~xsym ~expr]
                                 ~(last clauses))]])
        clauses (contract-condcast-clauses
                  expr xsym clauses &env)]
    (cond
      (= 0 (count clauses))
      `(let [~xsym ~expr]
         ~default) ;; might be nil obvi

      (= 1 (count clauses)) ;; corresponds to exact type match. janky but fine
      `(let [~xsym ~expr]
         ~@clauses)

      :else
      `(let [~xsym ~expr]
         (cond
           ~@(->> clauses
               (partition 2)
               (mapcat
                 (fn [[t then]]
                   `[(instance? ~t ~xsym)
                     (let [~(with-meta xsym {:tag t}) ~xsym]
                       ~then)]))))))))