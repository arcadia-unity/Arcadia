(ns arcadia.internal.map-utils
  (:require clojure.set))

;;; other ways to do it, should benchmark all of this also could use
;;; transients. most maps are small tho. hm. how fast is count on
;;; maps? might be some testable inflection point where transient is
;;; worth it

(defn dissoc-in
  [m [k & ks]]
  (if ks
    (if (contains? m k)
      (assoc m k (dissoc-in (get m k) ks))
      m)
    (dissoc m k)))

(defn submap? [m1 m2]  
  (or (identical? m1 m2) ;; sometimes you get lucky
    (reduce-kv
      (fn [_ k v]
        (if (= v (m2 k))
          true
          (reduced false)))
      true
      m1)))

(defn supermap? [m1 m2]
  (submap? m2 m1)) ;; whee lookit that

(defn dissoc-ks [m key-coll]  ;; maybe faster
  (reduce dissoc m key-coll))

(defn difference
  ([m1] m1)
  ([m1 m2]
     (dissoc-ks (keys m2)))
  ([m1 m2 & ms]
     (reduce difference m1 (conj ms m2))))

;; bit of naming awkwardness due to overloaded "map"
(defn map-vals [m f]
  (persistent!
    (reduce-kv
      (fn [m' k v]
        (assoc! m' k (f v)))
      (transient m)
      m)))

(defn map-keys [m f]
  (persistent!
    (reduce-kv
      (fn [m' k v]
        (assoc! m' (f k) v))
      (transient (empty m))
      m)))

(defn filter-keys [m pred]
  (persistent!
    (reduce-kv
      (fn [m' k _]
        (if-not (pred k)
          (dissoc! m' k)
          m'))
      (transient m)
      m)))

(defn remove-keys [m pred]
  (filter-keys m (complement pred)))

(defn filter-vals [m pred]
  (persistent!
    (reduce-kv
      (fn [m' k v]
        (if (pred v)
          m'
          (dissoc! m' k)))
      (transient m)
      m)))

(defn remove-vals [m pred]
  (filter-vals m (complement pred)))

(defn vk-biject
  "Maps values in m to groups of keys associated with them (in m). Sounder than, and different from, clojure.set/map-invert."
  [m]
  (persistent!
    (reduce-kv
      (fn [m' k v]
        (assoc! m' v
          (conj (get m' v #{})
            k)))
      (transient {})
      m)))

;;; hard to think when you'd use the next one, but complements
;;; vk-biject, and you never know what might come up.

(defn kv-biject
  "Groups keys in m by values and maps them to values."
  [m]
  (clojure.set/map-invert (vk-biject m)))



;; ============================================================
;; predicates
;; ============================================================

(defn every-key? [m pred]
  (every? pred (keys m)))

(defn every-val? [m pred]
  (every? pred (vals m)))

(defn some-key [m pred]
  (some pred (keys m)))

(defn some-val [m pred]
  (some pred (vals m)))


;; ============================================================
;; sharpsmanship
;; ============================================================

(defmacro lit-map [& syms]
  (assert (every? symbol? syms))
  (zipmap (map keyword syms) syms))

(defmacro lit-assoc [m & syms]
  (assert (every? symbol? syms))
  `(assoc ~m
     ~@(interleave
         (map keyword syms)
         syms)))

(defn checked-get [m k]
  (let [v (get m k)]
    (cond
      (not (nil? v)) v
      (contains? m k) v
      :else (throw
              (Exception.
                (str "key " k " not found"))))))

(defmacro checked-keys [bndgs & body]
  (let [dcls (for [[ks m] (partition 2 bndgs),
                   :let [msym (gensym "map_")]]
               (->> ks
                 (mapcat
                   (fn [k] [(symbol (name k)) `(checked-get ~m ~(keyword k))]))
                 (list* msym m)))]
    `(let [~@(apply concat dcls)]
       ~@body)))

(defn apply-kv
  "Terrible, necessary function. Use with APIs employing horrific
  keyword-arguments pattern. Please do not write such APIs."
  [f & argsm]
  (apply f
    (concat
      (butlast argsm)
      (apply concat
        (last argsm)))))

(defn fill
  ([m k v]
     (if (contains? m k) m (assoc m k v)))
  ([m k v & kvs]
     (let [ret (fill m k v)]
       (if kvs
         (if (next kvs)
           (recur ret (first kvs) (second kvs) (nnext kvs))
           (throw "soft-assoc expects even number of arguments after map/vector, found odd number"))
         ret))))

;; o lord

(defmacro fill-> [m & clauses]
  (let [msym (gensym "msym_")]
    `(as-> ~m ~msym
       ~@(for [[k thn] (partition 2 clauses)]
           `(if (contains? ~msym ~k)
              ~msym
              (assoc ~msym ~k ~thn))))))

;; much of this might be condensed into a single alternate destructuring dsl for maps

;; ==================================================

(defn keysr
  "Returns reducible collection of keys in m. Fast."
  [m]
  (reify
    clojure.core.protocols/CollReduce
    (coll-reduce [this f]
      (clojure.core.protocols/coll-reduce this f (f)))
    (coll-reduce [_ f init]
      (letfn [(rfn [bldg k _]
                (f bldg k))]
        (reduce-kv rfn init m)))
    clojure.lang.Counted
    (count [_]
      (count m))))

(defn valsr
  "Returns reducible collection of vals in m. Fast."
  [m]
  (reify
    clojure.core.protocols/CollReduce
    (coll-reduce [this f]
      (clojure.core.protocols/coll-reduce this f (f)))
    (coll-reduce [_ f init]
      (letfn [(rfn [bldg _ v]
                (f bldg v))]
        (reduce-kv rfn init m)))
    clojure.lang.Counted
    (count [_]
      (count m))))



