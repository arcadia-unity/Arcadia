(ns arcadia.internal.editor-interop
  (:require [clojure.string :as string]
            [arcadia.compiler :refer [asset->ns]]
            [arcadia.internal.name-utils :refer [title-case]])
  (:import [System.IO Directory File]
           [Arcadia AssetPostprocessor]
           [System.Reflection FieldInfo]
           [clojure.lang RT Symbol Keyword]
           [UnityEngine Debug GUILayout Vector2 Vector3 Vector4 AnimationCurve Color Bounds Rect]
           [UnityEditor EditorGUILayout MessageType]))

;; PERF memoize 
(defn all-user-namespaces-symbols []
  (->> AssetPostprocessor/cljFiles
       (map arcadia.compiler/asset->ns)
       (remove #(or (re-find #"^arcadia.*" (name %))
                    (re-find #"^clojure.*" (name %))
                    (= 'data-readers %)))))

(defn all-loaded-user-namespaces []
  (keep find-ns (all-user-namespaces-symbols)))

(defn load-user-namespaces! []
  (doseq [n (all-user-namespaces-symbols)]
    (Debug/Log (str "Loading " n))
    (require n)))

(defn reload-user-namespaces! []
  (doseq [n (all-user-namespaces-symbols)]
    (Debug/Log (str "Reloading " n))
    (require n :reload)))

(defn touch-dlls [^System.String folder]
  (doseq [dll (Directory/GetFiles folder "*.dll")]
    (File/SetLastWriteTime dll DateTime/Now)))

(defn grouped-state-map [m]
  (->> m
       (group-by (comp namespace first))
       sort))

(defn label-widget [k]
  (EditorGUILayout/LabelField
    #_
    (str k)
    #_
    (title-case (name k))
    (if (namespace k)
      (str "::" (name k))
      (str k))
    (into-array
      UnityEngine.GUILayoutOption
      [(GUILayout/MaxWidth 100)])))

(defmulti value-widget (fn [v] (type v)))

(defn inspector-widget [k v]
  (EditorGUILayout/BeginHorizontal nil)
  (label-widget k)
  (let [result (value-widget v)]
    (EditorGUILayout/EndHorizontal)
    result))

(defn start-state-group 
  ([] 
   (EditorGUILayout/BeginVertical (GUIStyle. EditorStyles/helpBox) nil)
   (GUILayoutUtility/GetRect 20 2))
  ([title]
   (if-not title
     (start-state-group)
     (let [_ (EditorGUILayout/BeginVertical (GUIStyle. EditorStyles/helpBox) nil)
           rect (GUILayoutUtility/GetRect 20 18)
           rect* (Rect. (+ 3 (.x rect)) (.y rect) (.width rect) (.height rect))
           style (.. (EditorGUIUtility/GetBuiltinSkin EditorSkin/Inspector)
                     (FindStyle "IN TitleText"))]
       #_ 
       (GUI/Toggle rect* true (title-case title) style)
       (GUI/Toggle rect* true title style)))))

(defn end-state-group [] 
  (GUILayoutUtility/GetRect 20 2)
  (EditorGUILayout/EndVertical))

(defn state-inspector [state]
  (apply merge state
         (reduce
           (fn [v [name body]]
             (start-state-group name)
             (let [group-map
                   (reduce
                     (fn [group-map* [k v]]
                       (assoc group-map* k (inspector-widget k v)))
                     {}
                     body)]
               (end-state-group)
               (conj v group-map)))
           []
           (grouped-state-map state))))

(defn state-inspector! [atm]
  (let [state @atm]
    (if (empty? state)
      (EditorGUILayout/HelpBox "Empty" MessageType/Info)
      (let [state* (state-inspector state)]
        (when-not (= state state*)
          (reset! atm state*))))))

(defmethod value-widget String [v]
  (EditorGUILayout/TextField (str v) nil))
 
(defmethod value-widget Boolean [v]
  (EditorGUILayout/Toggle (boolean v) nil))

(defmethod value-widget Int32 [v] 
  (EditorGUILayout/IntField v nil))
   
(defmethod value-widget Int64 [v]
  (EditorGUILayout/LongField v nil))

(defmethod value-widget Vector2 [v] 
  (EditorGUILayout/Vector2Field "" v (into-array [(GUILayout/MinWidth 50)])))
  
(defmethod value-widget Vector3 [v] 
  (EditorGUILayout/Vector3Field "" v (into-array [(GUILayout/MinWidth 50)])))
  
(defmethod value-widget Vector4 [v] 
  (EditorGUILayout/Vector4Field "" v (into-array [(GUILayout/MinWidth 50)])))

(defmethod value-widget Single [v] 
  (EditorGUILayout/FloatField (float v) nil))

(defmethod value-widget Double [v] 
  (EditorGUILayout/FloatField (float v) nil))

(defmethod value-widget Symbol [v] 
  (symbol (EditorGUILayout/TextField (str v) nil)))

(defmethod value-widget Keyword [v] 
  (keyword (EditorGUILayout/TextField (name v) nil)))

(defmethod value-widget AnimationCurve [v] 
  (EditorGUILayout/CurveField v nil))

(defmethod value-widget Color [v] 
  (EditorGUILayout/ColorField v nil))

(defmethod value-widget Bounds [v] 
  (EditorGUILayout/BoundsField v nil))

(defmethod value-widget Rect [v] 
  (EditorGUILayout/RectField v nil))

(defmethod value-widget UnityEngine.Object [v] 
  (EditorGUILayout/ObjectField v (type v) true nil))

(defmethod value-widget clojure.lang.IPersistentMap [v] 
  (state-inspector v))

(defmethod value-widget :default [v] 
  (EditorGUILayout/LabelField (pr-str v) nil)
  v)
 
(defn hash-map->generic-list [m]
  (new |System.Collections.Generic.List`1[System.Object]|
       (interleave (keys m)
                   (vals m))))

(defn generic-list->hash-map [l]
  (apply hash-map l))

(def types-unity-can-serialize
  [System.Int32
   System.Int64
   System.Double
   System.Single
   System.String
   System.Boolean
   System.Array
   System.Collections.IList
   UnityEngine.Object])

(defn can-unity-serialize? [t]
  (some #(isa? t %) types-unity-can-serialize))

(defn serializable-fields [obj]
  (filter
    #(can-unity-serialize? (.FieldType %))
    (-> obj
        .GetType
        .GetFields)))

(defn should-display-field? [^FieldInfo f]
  (not-any? #(= (-> % type .Name)
                "HideInInspector")
            (.GetCustomAttributes f true)))

;; TODO replace with dehydrate
(defn field-map
  "Get a map of all of an object's public fields. Reflects."
  ([obj] (field-map obj true))
  ([obj respect-attributes]
   (->> obj
        .GetType
        .GetFields
        (filter #(or (not respect-attributes)
                     (should-display-field? %)))
        (mapcat #(vector (.Name %)
                         (.GetValue % obj)))
        (apply hash-map))))

;; TODO replace with populate
(defn apply-field-map
  "Sets fields in obj to values in map m. Reflects."
  [m obj]
  (doseq [[field-name field-value] m]
    (.. obj
        GetType
        (GetField field-name)
        (SetValue obj field-value))))