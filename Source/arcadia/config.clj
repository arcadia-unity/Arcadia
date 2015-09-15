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

;; ============================================================
;; global state
;; ============================================================

(def configuration (atom {}))

(def ^:private last-read-time (atom nil))

;; ============================================================
;; utils
;; ============================================================

;; also in arcadia.compiler (which requires this namespace, so there
;; you go). should probably consolidate
(defn- load-path []
  (seq (.Invoke (.GetMethod clojure.lang.RT "GetFindFilePaths"
                            (enum-or BindingFlags/Static BindingFlags/NonPublic))
         clojure.lang.RT nil)))

(defn- file-exists? [file]
  (.Exists (io/as-file file)))

(defn- combine-paths [& ps]
  (reduce #(Path/Combine %1 %2) ps))

(defn- dedup-by [f coll]
  (letfn [(step [prv coll2]
            (lazy-seq
              (when-let [[x & rst] (seq coll2)]
                (let [v (f x)]
                  (if (prv v)
                    (step prv rst)
                    (cons x (step (conj prv v) rst)))))))]
    (step #{} coll)))

;; ============================================================
;; basic configuration
;; ============================================================

;; TODO account for multiple/different files
(defn- should-update-from? [f]
  (boolean
    (let [f (io/as-file f)]
      (and f
        (file-exists? f)
        (< (or @last-read-time 0)
          (.Ticks (.LastWriteTime f)))))))

(defn- standard-project-files []
  (->> [ClojureConfiguration/configFilePath
        ClojureConfiguration/userConfigFilePath]
    (map io/as-file)
    (filter file-exists?)
    vec))

;; stupid for now, expand to deal with exclusions etc
(defn- normalize-coordinate [coord]
  (vec
    (take 3
      (map str
        (if (< (count coord) 3)
          (cons (first coord) coord)
          coord)))))

(defn- process-coordinates [coords]
  (->> coords
    (map normalize-coordinate)
    (remove #(= ["org.clojure/clojure" "org.clojure/clojure"]
               (take 2 %)))
    vec))

(defn- load-basic-configuration-map [file]
  (let [f2 (edn/read-string (slurp file))]
    (reset! last-read-time (.Ticks DateTime/Now))
    (assert (map? f2))
    (-> f2
      (assoc
        :type :basic,
        :path (.FullName (io/as-file file)))
      (update :dependencies process-coordinates))))

;; ============================================================
;; leiningen processing
;; ============================================================

(defn- detect-leiningen-projects? []
  (boolean
    (:detect-leiningen-projects @configuration)))

(defn- ensure-readable-project-file [file-name, raw-file]
  (let [stringless (clojure.string/replace
                     raw-file
                     #"(\".*?((\\\\+)|[^\\])\")|\"\"" ;; fancy string matcher
                     "")
        problem (cond
                  (re-find #"~" stringless)
                  "'~' found"

                  (re-find #"#" stringless)
                  "'#' found")]
    (when problem
      (throw
        (Exception.
          (str "Unsupported file "
            (.FullName (io/as-file file-name))
            ": " problem))))))

(defn- read-lein-project-file [file]
  (let [raw (slurp file)]
    (ensure-readable-project-file file raw)
    (let [f (read-string raw)]
      (reset! last-read-time (.Ticks DateTime/Now))
      f)))

(defn- load-leiningen-configuration-map [file]
  (let [[_ name version & rst] (read-lein-project-file file)
        m (apply hash-map rst)]
    (-> m 
      (select-keys [:dependencies :source-paths])
      (assoc
        :type :leiningen
        :path (.FullName (io/as-file file)),
        :name (str name),
        :version version,)
      (update :dependencies process-coordinates))))

(defn- leiningen-project-file? [fi]
  (= "project.clj" (.Name (io/as-file fi))))

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

(defn- leiningen-loadpaths []
  (let [config @configuration]
    (for [m (:config-maps config)
          :when (= :leiningen (:type m))
          :let [p (Path/GetDirectoryName (.FullName (io/as-file (:path m))))]
          sp (or (:source-paths m) ["src" "test"])]
      (combine-paths p sp))))

;; ============================================================
;; public api
;; ============================================================

(defn configuration-files
  "Returns a vector of all configuration files on disk."
  []
  (vec
    (concat
      (when (detect-leiningen-projects?)
        (leiningen-project-files))
      (standard-project-files))))

(defn configuration-maps
  "Returns a vector of all maps corresponding to configuration files on disk."
  []
  (vec
    (concat
      (when (detect-leiningen-projects?)
        (map load-leiningen-configuration-map (leiningen-project-files)))
      (map load-basic-configuration-map (standard-project-files)))))

(defn configured-loadpath
  "Computes loadpath, taking state of arcadia.config/configuration into
  account as of last update!."
  ([] (configured-loadpath @configuration))
  ([config]
   (clojure.string/join ":"
     (dedup-by identity ;; preserves order, unlike set
       (concat
         (when (:detect-leiningen-projects config)
           (leiningen-loadpaths))
         (load-path))))))

(defn compute-configuration
  "Returns a map suitable for the state of
  arcadia.configuration/configuration. With no argument, computes using
  configuration files on disk. If supplied a collection of
  configuration maps, computes from those. Use to test configuration
  options."
  ([]
   (compute-configuration (configuration-maps)))
  ([maps]
   (let [deps (->> maps
                (mapcat :dependencies)
                packages/most-recent-versions)]
     (-> (reduce merge maps)
       (dissoc :type :path :source-paths :name :version)
       (assoc
         :dependencies deps
         :config-maps (vec maps))))))

(defn update!
  "Update the configuration atom (arcadia.config/configuration). If
  maps are not provided, reads them from configuration files on
  disk. See also: 
  configuration-files
  configuration-maps
  checked-update!"
  ([] (update! (configuration-maps)))
  ([maps]
   (reset! configuration (compute-configuration maps))))

(defn checked-update!
  "Update the configuration atom (arcadia.config/configuration) if
  configuration files have changed since last read. Reads from disk."
  []
  (if (some should-update-from? (configuration-files))
    (update!)
    @configuration))

(defn deps
  "Recomputes and installs dependencies, as specified by configuration
  files on disk."
  []
  (update!)
  (packages/flush-libraries) ;; until we have better caching story
  (doseq [c (:dependencies @configuration)]
    (packages/install c)))

;; ============================================================
;; static initializer
;; ============================================================

;; load basic configuration to see whether we're detecting leiningen files.
(update! (map load-basic-configuration-map (standard-project-files)))
;; update again (note that checked-update! wouldn't work here).
(update!)

;; ============================================================
;; UI
;; ============================================================

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
