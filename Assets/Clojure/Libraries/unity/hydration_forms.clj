(ns unity.hydration-forms
  (require [unity.map-utils :as mu])
  (import '[UnityEngine
            Vector3]))

;; also in unity.hydrate. should pop out to some other, vaguer namespace
(defmacro cast-as [x type]
  (let [xsym (with-meta (gensym "caster_") {:tag type})]
    `(let [~xsym ~x] ~xsym)))

;; passing hdb because we want a standard signature for these things
;; and we'll need it for more complicated types

(defn vec2-hff [hdb, vsym]
  `(-> (cond
         (instance? UnityEngine.Vector2 ~vsym)
         ~vsym

         (vector? ~vsym)
         (let [[x y] ~vsym]
           (UnityEngine.Vector2. x y))
      
         :else
         (let [{:keys [x y]
                :or {x 0, y 0}} ~vsym]
           (UnityEngine.Vector2. x y)))
     (cast-as UnityEngine.Vector2)))

(defn vec3-hff [hdb, vsym]
  `(-> (cond
         (instance? UnityEngine.Vector3 ~vsym)
         ~vsym

         (vector? ~vsym)
         (let [[x y z] ~vsym]
           (UnityEngine.Vector3. x y z))
      
         :else
         (let [{:keys [x y z]
                :or {x 0, y 0, z 0}} ~vsym]
           (UnityEngine.Vector3. x y z)))
     (cast-as UnityEngine.Vector3)))

(defn vec4-hff [hdb, vsym]
  `(-> (cond
         (instance? UnityEngine.Vector4 ~vsym)
         ~vsym

         (vector? ~vsym)
         (let [[x y z w] ~vsym]
           (UnityEngine.Vector4. x y z w))
      
         :else
         (let [{:keys [x y z w]
                :or {x 0, y 0, z 0, w 0}} ~vsym]
           (UnityEngine.Vector4. x y z w)))
     (cast-as UnityEngine.Vector4)))

(defn quat-hff [hdb, vsym]
  `(-> (cond
         (instance? UnityEngine.Quaternion ~vsym)
         ~vsym

         (vector? ~vsym)
         (let [[x y z w] ~vsym]
           (UnityEngine.Quaternion. x y z w))
      
         :else
         (let [{:keys [x y z w]
                :or {x 0, y 0, z 0, w 0}} ~vsym]
           (UnityEngine.Quaternion. x y z w)))
     (cast-as UnityEngine.Quaternion)))

;; should automate boosting these into the general hydration
;; setter-forms in unity.hydrate

(def default-misc-hydration-form-fns
  {UnityEngine.Vector2 vec2-hff
   UnityEngine.Vector3 vec3-hff
   UnityEngine.Vector4 vec4-hff
   UnityEngine.Quaternion quat-hff})

(def default-hydration-setters-fn ;; blaaaaaaaarg
  )
