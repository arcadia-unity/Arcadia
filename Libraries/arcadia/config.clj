(ns arcadia.config
  (:require [clojure.edn :as edn])
  (:import
    [UnityEngine Debug]
    [UnityEditor EditorGUILayout]
    ))

(def test-data "{
  :string-value \"foo\"
  :bool-value true
  :number-value 20
  }")

(defn render-gui []
  (doseq [[k v] (edn/read-string test-data)]
    (EditorGUILayout/BeginVertical nil)
    (cond 
      (string? v) (EditorGUILayout/TextField (name k) v nil)
      (= (type v) System.Boolean) (EditorGUILayout/Toggle (name k) v nil)
      (number? v) (EditorGUILayout/IntField (name k) v nil)
      )
    (EditorGUILayout/EndVertical)))