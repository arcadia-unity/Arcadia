(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su])
  (:import [UnityEngine
            Vector2
            Vector3
            Vector4
            Transform]))

;; everything here is stupid, shouldn't be typing this out by hand.
;; scan the unity api and assemble all the necessary hydration stuff
;; automatically like a civilized human being

(defn specload-fn
  [key-transforms]
  (let [trns (mapv vec (partition 2 key-transforms))]
    (fn [csym specsym]
      (reduce
        (fn [c [k f]]
          (if-let [v (specsym k)]
            (f c v)
            c))
        csym
        trns))))

(defn hydrater-fn [^System.MonoType type, specload]
  (fn [^GameObject obj, spec]
    (specload
      (.AddComponent obj type)
      spec)))

(def component-hydration-dispatch
  (atom default-hydration-dispatch
    :validator (fn [m] (and (map? m)))))

(declare
  default-tag-type-table
  default-component-hydration-dispatch)

(def tag-type-table
  (atom default-tag-type-table))

(defn hydrate-component [obj tag spec]
  (when-let [chd @component-hydration-dispatch
             f (or (chd tag)
                 (chd (@tag-type-table tag)))]
    (f obj spec)))

(defn hydrate-GameObject [obj]
  (reduce-kv
    hydrate-component
    (init-GameObject obj)
    (component-data obj)))

;; ------------------------------------------------------

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
    [:local-position     (setter Transform localPosition v3)
     :local-rotation     (setter Transform localRotation quat)
     :local-scale        (setter Transform localScale v3)
     :local-euler-angles (setter Transform eulerAngles v3)
     :euler-angles       (setter Transform eulerAngles v3)
     :position           (setter Transform position v3)
     :forward            (setter Transform forward v3)
     :right              (setter Transform right v3)
     :up                 (setter Transform up v3)]))

(def collider-specloadspec ;; fnd bttr nm
  [:attachedRigidbody (setter Transform RigidBody)
   :bounds            ()
   :enabled           ()
   :isTrigger         
   :material
   :sharedMaterial])

(def collider-specload
  (specload-fn collider-specloadspec))

(def boxcollider-specloadspec
  (concat
    collider-specloadspec
    [:center (setter BoxCollider center v3)
     :size   (setter BoxCollider size v3)]))

(def boxcollider-specload
  (specload-fn boxcollider-specloadspec))

(def default-component-hydration-dispatch
  {Transform   (hydrater-fn Transform #'transform-specload)
   Collider    (hydrater-fn Collider #'collider-specload)
   BoxCollider (hydrater-fn BoxCollider #'boxcollider-specload)})

(def default-tag-type-table
  {:transform Transform
   :collider Collider
   :boxcollider BoxCollider})
