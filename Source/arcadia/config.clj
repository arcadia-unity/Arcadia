(ns arcadia.config
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.data :as data])
  (:import
    [System DateTime]
    [System.IO File]
    [UnityEngine Debug]))

(def configuration (atom {}))

(defn default-config 
  "Built in Arcadia default configuration file. Never changes."
  [] (if (File/Exists ClojureConfiguration/defaultConfigFilePath)
       (edn/read-string (slurp ClojureConfiguration/defaultConfigFilePath
                               :encoding "utf8"))
       (throw (Exception. (str "Default Arcadia configuration file missing. "
                               ClojureConfiguration/defaultConfigFilePath
                               " does not exist")))))

(defn inspector-config
  "User supplied configuration map from inspector"
  [] ClojureConfiguration/inspectorConfigMap)

(defn user-config
  "User supplied configuration file"
  [] (if (File/Exists ClojureConfiguration/userConfigFilePath)
       (edn/read-string (slurp ClojureConfiguration/userConfigFilePath
                               :encoding "utf8"))
       {}))

;; TODO (merge-with into ... ) ?
(defn merged-configs
  "Result of merger of all three configuration sources"
  [] (merge (default-config)
            (inspector-config)
            (user-config)))

(defn update!
  "Update the configuration atom"
  [] (reset! configuration (merged-configs)))

(declare widgets)

(defn widget [k v]
  (cond
    (or (vector? v) (map? v))
    (let [_ (set! EditorGUI/indentLevel (inc EditorGUI/indentLevel))
          v (widgets v)
          _ (set! EditorGUI/indentLevel (dec EditorGUI/indentLevel))]
      v)
    (list? v) (read-string (EditorGUILayout/TextField (str k) (pr-str v) nil))
    (keyword? v) (read-string (EditorGUILayout/TextField (str k) (pr-str v) nil))
    (symbol? v) (read-string (EditorGUILayout/TextField (str k) (pr-str v) nil))
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
                 (assoc c k (widget (str k) v))) m m)
    (sequential? m)
    (vec (map-indexed #(widget %1 %2) m))))

(defn render-gui []
  (let [config (into (sorted-map) ClojureConfiguration/inspectorConfigMap)]
    (EditorGUILayout/BeginVertical nil)
    (let [config* (widgets config)]
      (when (not= config config*)
        (set! ClojureConfiguration/inspectorConfigMap
              config*)
        (update!)))
    (EditorGUILayout/EndVertical)))