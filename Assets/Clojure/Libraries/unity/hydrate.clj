(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su]
            [unity.reflect-utils :as ru]
            [clojure.string :as string])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

;; everything here is stupid, shouldn't be typing this out by hand.
;; scan the unity api and assemble all the necessary hydration stuff
;; automatically like a civilized human being

;; first pass via reflection

;; setter things take a component and a spec and set things for them.
;; one setter thing for each type.
(defmacro cast-as [x type]
  (let [xsym (with-meta (gensym "caster_") {:tag type})]
    `(let [~xsym ~x] ~xsym)))

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

;; generate more if you feel it worth testing for
(defn keys-for-setable [{n :name}]
  [(keyword (string/lower-case (camels-to-hyphens (name n))))])

(defn type-for-setable [{typ :type}]
  typ) ;; good enough for now

(defn setable-properties [typ]
  (ru/properties typ))

(defn setable-fields [typ]
  (->> typ
    ru/fields
    (filter
      (fn [{fs :flags}]
        (and
          (:public fs)
          (not (:static fs)))))))

(defn setables [typ]
  (concat
    (setable-properties typ)
    (setable-fields typ)))

;; insert converters etc here if you feel like it
(defn setter-key-clauses [targsym typ vsym]
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables typ)
            :let [st (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n) 
               (cast-as ~vsym ~typ))]))))

;; BUG: case doesn't work for types!
(defn setter-reducing-fn-form [^System.MonoType typ]
  (let [ksym (gensym "spec-key")
        vsym (gensym "spec-val")
        targsym (gensym "targ")
        skcs (setter-key-clauses targsym typ vsym)]
    `(fn [targ# [~ksym ~vsym]] 
       (case ~ksym
         ~@setter-key-clauses))))

(defn generate-setter [^System.MonoType t]
  (let [targsym  (with-meta (gensym "setter-target") {:tag type})
        specsym  (gensym "spec")
        ;;pipeline (setter-pipeline-form t targsym specsym)
        sr       (setter-reducing-fn-form t targsym)]
    (eval
      `(fn [~targsym spec#]
         (reduce
           ~sr
           ~targsym
           (prepare-spec spec#))))))

(defn all-component-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat #(.GetTypes %))
    (filter #(isa? % UnityEngine.Component))))

(defn set-members [c spec]
  ((setter spec) c spec))

(defn hydrate-component [^GameObject obj, spec]
  (set-members (initialize-component obj, spec) spec))
