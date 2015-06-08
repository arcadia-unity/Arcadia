(ns arcadia.config
  (:require [arcadia.packages :as packages]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.data :as data]
            clojure.string)
  (:import
    [System DateTime]
    [System.IO File FileInfo DirectoryInfo Path]
    [UnityEngine Debug]
    [UnityEditor EditorGUI EditorGUILayout]))

(def configuration (atom {}))
(def last-read-time (atom 0))

(defn- combine-paths [& ps]
  (reduce #(Path/Combine %1 %2) ps))

;; TODO account for multiple/different files
(defn should-update-from? [f]
  (and f
    (File/Exists f)
    (< (or @last-read-time 0)
      (.Ticks (File/GetLastWriteTime f)))))

;; should probably make all this noise a bit more functional
(defn configuration-file-paths []
  [ClojureConfiguration/configFilePath
   ClojureConfiguration/userConfigFilePath])

(defn- deep-merge-maps [& ms]
  (apply merge-with
    (fn [v1 v2]
      (if (and (map? v1) (map? v2))
        (deep-merge-maps v1 v2)
        v2))
    ms))

(defn update! 
  ([] (update! (configuration-file-paths)))
  ([fs]
   (->> fs
     (keep
       (fn [f]
         (when (File/Exists f)
           (let [f2 (edn/read-string (File/ReadAllText f))]
             (reset! last-read-time (.Ticks DateTime/Now))
             f2))))
     (apply deep-merge-maps {})
     (reset! configuration))))

(defn checked-update! []
  (if (some should-update-from? (configuration-file-paths))
    (update!)
    @configuration))

;; update as soon as the file is required
(checked-update!)

;; TODO: put this function somewhere sensible
(defn- dedup-by [f coll]
  (letfn [(step [prv coll2]
            (lazy-seq
              (when-let [[x & rst] (seq coll2)]
                (let [v (f x)]
                  (if (prv v)
                    (step prv rst)
                    (cons x (step (conj prv v) rst)))))))]
    (step #{} coll)))

;; also in arcadia.compiler (which requires this namespace, so there
;; you go). should probably consolidate
(defn- load-path []
  (seq (.Invoke (.GetMethod clojure.lang.RT "GetFindFilePaths"
                            (enum-or BindingFlags/Static BindingFlags/NonPublic))
         clojure.lang.RT nil)))

(defn- leiningen-project-file? [^FileInfo fi]
  (= "project.clj" (.Name fi)))

(defn- leiningen-structured-directory? [^DirectoryInfo di]
  (boolean
    (some leiningen-project-file?
      (.GetFiles di))))

(defn leiningen-project-files []
  (vec
    (for [^DirectoryInfo di (.GetDirectories (DirectoryInfo. "Assets"))
          :when (leiningen-structured-directory? di)
          ^FileInfo fi (.GetFiles di)
          :when (leiningen-project-file? fi)]
      fi)))

(defn- leiningen-project-sourcepaths [^FileInfo fi]
  (let [p (Path/GetDirectoryName (.FullName fi))]
    (map #(combine-paths p %)
      (or (:source-paths (read-string (slurp fi))) ["src"]))))

;; ono phase "leiningen" is in code
(defn- leiningen-loadpaths []
  (mapcat leiningen-project-sourcepaths
    (leiningen-project-files)))

(defn configured-loadpath
  ([] (configured-loadpath @configuration))
  ([config]
   (clojure.string/join
     Path/PathSeparator
     (when (:detect-leiningen-projects config)
       (leiningen-loadpaths)))))

(defn normalize [coords]
  (let [artifact (name (first coords))
        group (or (namespace (first coords)) artifact)
        version (last coords)]
    [group artifact version]))

(defn version-sequence [v]
  (-> v
      (clojure.string/split #"[\.\-]")
      (concat (repeat 0))
      (->> (take 4)
           (map #(if (= % "SNAPSHOT") -1
                   (int %))))))

(defn version-newer? [a b]
  (->> (map #(if (= %1 %2) nil (> %1 %2))
            (version-sequence a)
            (version-sequence b))
       (remove nil?)
       first
       boolean))

(defn coordinate-newer? [a b]
  (version-newer? (last a)
                  (last b)))

(defn install-leiningen-dependencies!
  ([] (install-leiningen-dependencies! (leiningen-project-files)))
  ([project-files] 
   (->> project-files
        (map slurp)
        (map read-string)
        (map #(drop 3 %))
        (map #(apply hash-map %))
        (mapcat :dependencies)
        (remove #(= (first %) 'org.clojure/clojure))
        (group-by first)
        vals
        (map #(sort (comparator coordinate-newer?) %))
        (map first)
        (map normalize)
        (map packages/install)
        dorun)))

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
            (checked-update!)))))
    (EditorGUILayout/EndVertical)))
