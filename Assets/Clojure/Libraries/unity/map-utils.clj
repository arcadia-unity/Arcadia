(ns unity.map-utils)

;;; perhaps this doesn't belong in unity.*, however there are some
;;; missing functions (like submap?) whose absence drives me so
;;; bonkers I'm putting them here anyway.

;;; other ways to do it, should benchmark all of this also could use
;;; transients. most maps are small tho. hm. how fast is count on
;;; maps? might be some testable inflection point where transient is
;;; worth it

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


;;; better names? there's doubtless some superior category-theoretical
;;; way to talk about this, if anyone knows it I'm all ears

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
