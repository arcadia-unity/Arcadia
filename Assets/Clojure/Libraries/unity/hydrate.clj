(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

;; everything here is stupid, shouldn't be typing this out by hand.
;; scan the unity api and assemble all the necessary hydration stuff
;; automatically like a civilized human being

;; first pass via reflection

;; setter things take a component and a spec and set things for them.
;; one setter thing for each type.

(defn all-component-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat #(.GetTypes %))
    (filter #(isa? % UnityEngine.Component))))

(defn setter-pipeline-form [^System.MonoType t]
  (let [setter-]))

(defn generate-setter [^System.MonoType t]
  (let [csym     (with-meta (gensym "setter-target") {:tag type})
        specsym  (gensym)
        pipeline (setter-pipeline-form t)]
    (eval
      `(fn [~csym spec#]
         ~pipeline))))

(defn set-members [c spec]
  ((setter spec) c spec))

(defn hydrate-component [^GameObject obj, spec]
  (set-members (initialize-component obj, spec) spec))
