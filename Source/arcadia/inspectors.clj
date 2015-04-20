(ns arcadia.inspectors
  (:use arcadia.core)
  (:require [arcadia.internal.editor-interop :as interop])
  (:import UnityEditor.EditorGUILayout))

(defmulti widget (fn [label value] (type value)))
(defmethod widget :default [k v] (let [^System.String label (str k)
                                          ^System.String value (pr-str v)]
                                      (EditorGUILayout/LabelField label (str "? " value) nil)
                                      v))
(defn widgets [m]
  (cond
    (map? m) (reduce-kv
               (fn [c k v] 
                 (assoc c k (widget (str k) v))) m m)
    (sequential? m)
    (vec (map-indexed #(widget (str "Element " %1) %2) m))))

; TODO support collapsing 
(defmethod widget clojure.lang.IPersistentCollection
  [k v] 
  ; (EditorGUILayout/Foldout true (str k))
  (EditorGUILayout/LabelField k (if (map? v) "{}" "[]") nil)
  (set! EditorGUI/indentLevel (inc EditorGUI/indentLevel))
  (let [v (widgets v)]
    (set! EditorGUI/indentLevel (dec EditorGUI/indentLevel))
    v))

(defmethod widget System.String
  [k v] (EditorGUILayout/TextField k v nil))
(defmethod widget System.Boolean
  [k v] (EditorGUILayout/Toggle k v nil))
(defmethod widget System.Int64
  [k v] (EditorGUILayout/IntField k v nil))
(defmethod widget System.Int32
  [k v] (EditorGUILayout/LongField k v nil))
(defmethod widget System.Single
  [k v] (EditorGUILayout/FloatField k v nil))
(defmethod widget System.Double
  [k v] (double (EditorGUILayout/FloatField k v nil)))
(defmethod widget UnityEngine.Bounds
  [k v] (EditorGUILayout/BoundsField k v nil))
(defmethod widget UnityEngine.Rect
  [k v] (EditorGUILayout/RectField k v nil))
(defmethod widget UnityEngine.Color
  [k v] (EditorGUILayout/ColorField k v nil))
(defmethod widget UnityEngine.Vector2
  [k v] (EditorGUILayout/Vector2Field k v nil))
(defmethod widget UnityEngine.Vector3
  [k v] (EditorGUILayout/Vector3Field k v nil))
(defmethod widget UnityEngine.Vector4
  [k v] (EditorGUILayout/Vector4Field k v nil))
(defmethod widget UnityEngine.Quaternion
  [k v] (let [v4 (UnityEngine.Vector4. (.x v) (.y v) (.z v) (.w v))
              v4* (EditorGUILayout/Vector4Field k v4 nil)]
          (UnityEngine.Quaternion. (.x v4) (.y v4) (.z v4) (.w v4))))
(defmethod widget UnityEngine.AnimationCurve
  [k v] (EditorGUILayout/CurveField k v nil))
(defmethod widget System.Enum
  [k v] (EditorGUILayout/EnumPopup k v (type v) nil))
(defmethod widget UnityEngine.Object
  [k v] (EditorGUILayout/ObjectField k v (type v) true nil))


(defn render-gui [obj]
  (EditorGUILayout/BeginVertical nil)
  (interop/apply-field-map
    (widgets (interop/field-map obj))
    obj)
  (EditorGUILayout/EndVertical))