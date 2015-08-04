(ns arcadia.config
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.data :as data]
            [clojure.clr.io :as io]
            [arcadia.packages :as packages]
            clojure.string)
  (:import
   ClojureConfiguration
   [System DateTime]
   [System.IO File FileInfo DirectoryInfo Path]
   [UnityEngine Debug]
   [UnityEditor EditorGUI EditorGUILayout]))

;; also in arcadia.compiler (which requires this namespace, so there
;; you go). should probably consolidate
(defn- load-path []
  (seq (.Invoke (.GetMethod clojure.lang.RT "GetFindFilePaths"
                            (enum-or BindingFlags/Static BindingFlags/NonPublic))
         clojure.lang.RT nil)))

(def configuration (atom {}))
(def last-read-time (atom 0))

(defn- file-exists? [file]
  (.Exists (io/as-file file)))

(defn- combine-paths [& ps]
  (reduce #(Path/Combine %1 %2) ps))

;; TODO account for multiple/different files
(defn should-update-from? [f]
  (boolean
    (let [f (io/as-file f)]
      (and f
        (file-exists? f)
        (< (or @last-read-time 0)
          (.Ticks (.LastWriteTime f)))))))

(defn- standard-project-files []
  [ClojureConfiguration/configFilePath
   ClojureConfiguration/userConfigFilePath])

(declare leiningen-project-files)

(defn configuration-files []
  (vec
    (concat
      (leiningen-project-files)
      (standard-project-files))))

;; should probably make all this noise a bit more functional
;; (defn configuration-file-paths []
;;   [ClojureConfiguration/configFilePath
;;    ClojureConfiguration/userConfigFilePath])

(defn- deep-merge-maps [& ms]
  (apply merge-with
    (fn [v1 v2]
      (if (and (map? v1) (map? v2))
        (deep-merge-maps v1 v2)
        v2))
    ms))

(defn- read-lein-project-file [file]
  (let [f (->> (slurp file)
            (str "'") ;; leiningen allows unquotes, we don't yet
            load-string)]
    (reset! last-read-time (.Ticks DateTime/Now))
    f))

;; stupid for now, expand to deal with exclusions etc
(defn- normalize-lein-coordinate [coord]
  (vec
    (take 3
      (map str
        (if (< (count coord) 3)
          (cons (first coord) coord)
          coord)))))

(defn- load-leiningen-configuration-map [file]
  (let [[_ name version & rst] (read-lein-project-file file)
        m (apply hash-map rst)]
    (-> m 
      (select-keys [:dependencies :source-paths])
      (assoc
        :path (.FullName (io/as-file file)),
        :name (str name),
        :version version,
        :type :leiningen)
      (update :dependencies
        (fn [ds]
          (->> ds
            (map normalize-lein-coordinate)
            (remove #(= ["org.clojure/clojure" "org.clojure/clojure"]
                       (take 2 %)))))))))

(defn- load-basic-configuration-map [file]
  (let [f2 (edn/read-string (slurp file))]
    (reset! last-read-time (.Ticks DateTime/Now))
    (assert (map? f2))
    (assoc f2
      :type :basic,
      :path (.FullName (io/as-file file)))))

(defn- configuration-maps []
  (vec
    (concat
      (map load-leiningen-configuration-map (leiningen-project-files))
      (map load-basic-configuration-map
        (filter file-exists? (standard-project-files))))))

(defn- merge-with-keyed
  "Like merge-with, but f takes shared key as first argument: (f key v1
  v2)"
  [f & maps]
  (when (some identity maps)
    (let [merge-entry (fn [m e]
			(let [k (key e) v (val e)]
			  (if (contains? m k)
			    (assoc m k (f k (get m k) v)) 
			    (assoc m k v))))
          merge2 (fn [m1 m2]
		   (reduce merge-entry (or m1 {}) (seq m2)))]
      (reduce merge2 maps))))

(defn- merge-configuration-maps [& maps]
  (apply merge-with-keyed
    (fn [k v1 v2]
      (if (= k :dependencies)
        (vec
          (packages/most-recent-versions
            (concat v1 v2)))
        v2))
    maps))

(defn update! 
  ([] (update! (configuration-maps)))
  ([ms]
   (let [m (->> ms
             (map #(if-not (= :basic (:type %))
                     (select-keys % [:dependencies])
                     %))
             (apply merge-configuration-maps {}))]
     (reset! configuration
       (-> m
         (dissoc :path :name)
         (assoc :sources ms))))))

(defn checked-update! []
  (if (some should-update-from? (configuration-files))
    (update!)
    @configuration))

(defn deps []
  (checked-update!)
  (doseq [c (:dependencies @configuration)]
    (packages/install c)))

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

(defn- leiningen-project-file? [fi]
  (= "project.clj" (.Name (clojure.clr.io/as-file fi))))

(defn- leiningen-structured-directory? [^DirectoryInfo di]
  (boolean
    (some leiningen-project-file?
      (.GetFiles di))))

(defn- leiningen-project-directories []
  (->> (.GetDirectories (DirectoryInfo. "Assets"))
    (filter leiningen-structured-directory?)
    vec))

(defn- leiningen-project-files []
  (vec
    (for [^DirectoryInfo di (leiningen-project-directories)
          ^FileInfo fi (.GetFiles di)
          :when (leiningen-project-file? fi)]
      fi)))

(defn- leiningen-project-sourcepaths [fi]
  (let [p (Path/GetDirectoryName (.FullName (io/as-file fi)))]
    (map #(combine-paths p %)
      (or (:source-paths (edn/read-string (slurp fi))) ["src" "test"]))))

;; ono phase "leiningen" is in code
(defn- leiningen-loadpaths []
  (mapcat leiningen-project-sourcepaths
    (leiningen-project-files)))

(defn configured-loadpath
  ([] (configured-loadpath @configuration))
  ([config]
   (clojure.string/join ":"
     (dedup-by identity
       (concat
         (when (:detect-leiningen-projects config)
           (leiningen-loadpaths))
         (load-path))))))

;; !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
;; update as soon as the file is required!
;; !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
(checked-update!)

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
