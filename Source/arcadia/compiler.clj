(ns arcadia.compiler
  (:require [arcadia.config :refer [configuration]]
            clojure.string)
  (:import [System IO.Path IO.File Environment]
           [UnityEngine Debug]
           [UnityEditor AssetDatabase ImportAssetOptions PlayerSettings ApiCompatibilityLevel]))

(defn assemblies-path []
  (let [clj-dll-folder (Path/GetDirectoryName (.Location (.Assembly clojure.lang.RT)))
        arcadia-folder (Path/Combine clj-dll-folder "..")
        compiled-folder (Path/Combine arcadia-folder "Compiled")]
    (Path/GetFullPath compiled-folder)))

;; should we just patch the compiler to make GetFindFilePaths public?
(defn load-path []
  (seq (.Invoke (.GetMethod clojure.lang.RT "GetFindFilePaths"
                            (enum-or BindingFlags/Static BindingFlags/NonPublic))
                clojure.lang.RT nil)))

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

(defn relative-to-load-path
  "Sequence of subpaths relative to the load path, shortest first"
  [path]
  (->> (clojure.string/split path #"\/")
       rests
       reverse
       (map #(clojure.string/join "/" %))
       (filter #(clojure.lang.RT/FindFile %))))

(defn clj-file? [path]
  (boolean (re-find #"\.clj$" path)))

(defn clj-files [paths]
  (filter clj-file? paths))

(defn asset->ns [asset]
  (-> asset
      relative-to-load-path
      first
      path->ns
      (#(if % (symbol %) %))))

(defn first-form [file]
  (binding [*read-eval* false]
    (read-string (slurp file :encoding "utf8"))))

(defn should-compile? [file]
  (if (File/Exists file)
    (let [[frst scnd & rst] (first-form file)
          expected-ns (asset->ns file)]
      (and (= frst 'ns)
           (= scnd expected-ns)))))

(defn import-asset [asset]
  (let [verbose (@configuration :verbose)
        {:keys [assemblies
                load-path
                warn-on-reflection
                unchecked-math
                compiler-options]}
        (@configuration :compiler)
        assemblies (or assemblies
                       (assemblies-path))]
    (if (should-compile? asset)
      (let [namespace (asset->ns asset)]
        (try
          (binding [*compile-path* assemblies
                    *warn-on-reflection* warn-on-reflection
                    *unchecked-math* unchecked-math
                    *compiler-options* compiler-options]
            (Debug/Log (str "Compiling " (name namespace) "..."))
            (compile namespace)
            (AssetDatabase/Refresh ImportAssetOptions/ForceUpdate))
          (catch clojure.lang.Compiler+CompilerException e
            (Debug/Log (str (.Message e))))
          (catch Exception e
            (Debug/LogException e))))
      (Debug/Log (str "Skipping " asset "..."))
      )))

(defn import-assets [imported]
  (doseq [asset (clj-files imported)]
    (import-asset asset)))

(defn delete-assets [deleted]
  (doseq [asset (clj-files deleted)]
    ))