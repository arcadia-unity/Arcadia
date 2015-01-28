(ns arcadia.config
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.data :as data])
  (:import
    [System DateTime]
    [System.IO File]
    [UnityEngine Debug]
    [UnityEditor EditorGUI EditorGUILayout]))

(def configuration (atom {}))
(def last-read-time (atom 0))

;; TODO account for multiple/different files
(defn should-update-from? [f]
  (and (not (nil? f))
       (File/Exists f)
       (< (or @last-read-time 0)
          (.Ticks (File/GetLastWriteTime f)))))

(defn update! 
  ([] (update! ClojureConfiguration/configFilePath))
  ([f]
   (if (should-update-from? f)
     (do
       (reset! last-read-time (.Ticks DateTime/Now))
       (reset! configuration (edn/read-string (File/ReadAllText f)))))))

;; update as soon as the file is required
(update!)

(declare widgets)

(def foldouts (atom {}))

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

(defn render-gui []
  (let [config (into (sorted-map) @configuration)]
    (EditorGUILayout/BeginVertical nil)
    (let [new-configuration (widgets config)]
      (if (not= @configuration new-configuration)
        (if-let [config-file ClojureConfiguration/configFilePath]
          (do
            (File/WriteAllText config-file
                               (binding [pprint/*print-pretty* true]
                                 (with-out-str (pprint/pprint new-configuration))))
            (update!)))))
    (EditorGUILayout/EndVertical)))