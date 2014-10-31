(ns arcadia.config
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.data :as data])
  (:import
    [System.IO File]
    [UnityEngine Debug]
    [UnityEditor EditorGUILayout]))

(def config (atom {}))

(def foldouts (atom {}))

(defn value [k]
  (@config k))

(defn value-in [p]
  (get-in @config p))

(defn update! [m]
  (reset! config m))

(defn update-from-file! [f]
  (Debug/Log (str "update-from-file " f))
  (update! (edn/read-string (File/ReadAllText f))))

(defn update-from-default-location! []
  (update-from-file! "Assets/Arcadia/configure.edn"))

(declare widgets)

(defn widget [k v]
  (cond
    (or (vector? v) (map? v))
    (do
      (swap! foldouts assoc k (EditorGUILayout/Foldout (@foldouts k) (str k)))
      (if (@foldouts k)
        (let [_ (set! EditorGUI/indentLevel (inc EditorGUI/indentLevel))
              v (widgets v)
              _ (set! EditorGUI/indentLevel (dec EditorGUI/indentLevel))]
          v)
        v))
    (= (type v) System.String) (EditorGUILayout/TextField (str k) v nil)
    (= (type v) System.Boolean) (EditorGUILayout/Toggle (str k) v nil)
    (= (type v) System.Int64) (EditorGUILayout/IntField (str k) v nil)
    (= (type v) System.Int32) (EditorGUILayout/IntField (str k) v nil)
    ;(= (type v) System.Single) (EditorGUILayout/FloatField (str k) v nil)
    ;(= (type v) System.Double) (double (EditorGUILayout/FloatField (str k) v nil))
    :else (let [^System.String label (str k)
                ^System.String value (with-out-str (pprint/pprint v))]
            (EditorGUILayout/LabelField label value nil)
            v)))

(defn widgets [m]
  (cond
    (map? m) (reduce-kv
               (fn [c k v] 
                 (assoc c k (widget (name k) v))) m m)
    (sequential? m)
    (vec (map-indexed #(widget %1 %2) m))))

(defn render-gui [config-file]
  (let [config (into (sorted-map) (edn/read-string (File/ReadAllText config-file)))]
    (EditorGUILayout/BeginVertical nil)
    (let [new-config (widgets config)]
      (if (not= config new-config)
        (do
          (File/WriteAllText config-file
                             (binding [pprint/*print-pretty* true]
                               (with-out-str (pprint/pprint new-config))))
          (update! new-config))))
    (EditorGUILayout/EndVertical)))

