(ns unity.map-utils)

;; other ways to do it, should benchmark all of this

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

(defn dissoc-ks [m key-coll]  ;; maybe faster (doesn't call seq)
  (reduce dissoc m key-coll))

(defn difference
  ([m1] m1)
  ([m1 m2]
     (dissoc-ks (keys m2)))
  ([m1 m2 & ms]
     (reduce difference m1 (conj ms m2))))

;; bit of naming awkwardness due to overloaded "map"
(defn map-vals [m f] 
  (reduce-kv
    (fn [m' k v]
      (assoc m' k (f v)))
    m
    m))

(defn map-keys [m f]
  (reduce-kv
    (fn [m' k v]
      (assoc m' (f k) v))
    (empty m)
    m))

(defn filter-keys [m pred]
  (reduce-kv
    (fn [m' k _]
      (if-not (pred k)
        (dissoc m' k)
        m'))
    m
    m))

(defn remove-keys [m pred]
  (filter-keys m (complement pred)))

(defn filter-vals [m pred]
  (reduce-kv
    (fn [m' k v]
      (if (pred v)
        m'
        (dissoc m k)))
    m
    f))

(defn remove-vals [m pred]
  (filter-vals m (complement pred)))
