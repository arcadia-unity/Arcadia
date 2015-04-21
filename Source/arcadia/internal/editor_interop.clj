(ns arcadia.internal.editor-interop
  (:import [System.IO File]
           [System.Reflection FieldInfo]))

(defn touch-dlls [^System.String folder]
  (doseq [dll (Directory/GetFiles folder "*.dll")]
    (File/SetLastWriteTime dll DateTime/Now)))

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