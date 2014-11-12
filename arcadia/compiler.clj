(ns arcadia.compiler
  (:require [arcadia.config :refer [config]]
            clojure.string)
  (:import [System IO.Path Environment]
           [UnityEngine Debug]
           [UnityEditor AssetDatabase ImportAssetOptions PlayerSettings ApiCompatibilityLevel]))

(defn assemblies-path []
  (Path/GetDirectoryName
    (.Location (.Assembly clojure.lang.RT))))

;; should we just patch the compiler to make GetFindFilePaths public?
(defn load-path []
  (seq (.Invoke (.GetMethod clojure.lang.RT "GetFindFilePaths"
                            (enum-or BindingFlags/Static BindingFlags/NonPublic))
                clojure.lang.RT nil)))

(defn initialize-unity []
  (set! PlayerSettings/apiCompatibilityLevel ApiCompatibilityLevel/NET_2_0)
  (set! PlayerSettings/runInBackground true))

(defn setup-load-paths []
  (initialize-unity)
  (arcadia.config/update-from-default-location!)
  (let [config @config]
    (->> (cons "Assets"
               (get-in config [:compiler :load-path]))
         (map #(Path/Combine Environment/CurrentDirectory %))
         (clojure.string/join ":")
         (Environment/SetEnvironmentVariable "CLOJURE_LOAD_PATH"))))

(defn rests
  "Returns a sequence of all rests of the input sequence
  
  => (rests [1 2 3 4 5 6 7])
  ((1 2 3 4 5 6 7) (2 3 4 5 6 7) (3 4 5 6 7) (4 5 6 7) (5 6 7) (6 7) (7))"
  [s]
  (->> (seq s)
       (iterate rest)
       (take-while seq)))

(defmacro if-> [v & body]
  `(if ~v (-> ~v ~@body)))

(defn path->ns
  "Returns a namespace from a path
  
  => (path->ns \"foo/bar/baz.clj\")
  \"foo.bar.baz\""
  [p]
  (if-> p
        (clojure.string/replace #"\.clj$" "")
        (clojure.string/replace #"\/" ".")))

(defn load-path-relative
  "Sequence of subpaths relative to the load path, shortest first"
  [p]
  (->> (clojure.string/split p #"\/")
       rests
       reverse
       (map #(clojure.string/join "/" %))
       (filter #(clojure.lang.RT/FindFile %))))

(-> (load-path-relative "Assets/Arcadia/arcadia/core.clj")
    first
    path->ns)

(defn process-assets [imported]
  (let [config @config
        {:keys [assemblies warn-on-reflection unchecked-math]}
        (config :compiler)
        assemblies (or assemblies
                       (assemblies-path))]
    (doseq [asset imported
            :when (re-find #"\.clj$" asset)]
      
      (if-let [root (->> (load-path) (filter #(.Contains asset %)) first)]
        (let [namespace (-> asset 
                            (clojure.string/replace root "")
                            (clojure.string/replace #".clj$" "")
                            (clojure.string/replace #"^\/" "")
                            (clojure.string/replace "/" ".")
                            (clojure.string/replace "_" "-"))]
          (try
            (binding [*compile-path* assemblies
                      *warn-on-reflection* warn-on-reflection
                      *unchecked-math* unchecked-math
                      *compiler-options* nil]
              (compile (symbol namespace))
              (AssetDatabase/Refresh ImportAssetOptions/ForceUpdate))
            (catch Exception e
              (Debug/LogException e))))))))