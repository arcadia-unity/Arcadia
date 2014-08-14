(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su])
  (:import [UnityEngine
            Vector2
            Vector3
            Vector4
            Transform]))

(defn specload-fn
  [& key-transforms]
  (let [trns (mapv vec (partition 2 key-transforms))]
    (fn [csym specsym]
      (reduce
        (fn [c [k f]]
          (if-let [v (specsym k)]
            (f c v)
            c))
        csym
        trns))))

(defmacro setter
  ([type field] `(qwik-setter ~type ~field hydrate))
  ([type field field-hydrater]
     (let [csym (with-meta (gensym) {:tag type})]
       `(fn [~csym v#]
          (set! (. ~csym field)
            (~field-hydrater v#))))))

;; these should be in another namespace. also inlined &c. 

;; Vectors in Unity are mutable! These functions should guarantee
;; construction of a new vector, so no need to test whether incoming
;; is already a vector.
(defn v2 ^Vector2 [[x y]]
  (Vector2. x y))

(defn v3 ^Vector3 [[x y z]]
  (Vector3. x y z))

(defn v4 ^Vector4 [[x y z w]]
  (Vector4. x y z w))

(defn quat ^Quaternion [[x y z w]]
  (Quaternion. x y z w))

(def transform-specload
  (specload-fn
    :local-position     (setter Transform localPosition v3)
    :local-rotation     (setter Transform localRotation quat)
    :local-scale        (setter Transform localScale v3)
    :local-euler-angles (setter Transform eulerAngles v3)
    :euler-angles       (setter Transform eulerAngles v3)
    :position           (setter Transform position v3)
    :forward            (setter Transform forward v3)
    :right              (setter Transform right v3)
    :up                 (setter Transform up v3)))

(defn hydrate-Transform ^Transform
  [^GameObject obj, spec]
  (transform-specload
    (.AddComponent obj Transform)
    spec))

(def default-component-hydration-dispatch
  {:transform hydrate-transform
   Transform hydrate-Transform})

(def component-hydration-dispatch
  (atom default-hydration-dispatch
    :validator (fn [m] (and (map? m)))))

(defn hydrate-component [obj tag spec]
  (when-let [f (@component-hydration-dispatch tag)]
    (f obj spec)))

(defn hydrate-GameObject [obj]
  (reduce-kv
    hydrate-component
    (init-GameObject obj)
    (component-data obj)))
