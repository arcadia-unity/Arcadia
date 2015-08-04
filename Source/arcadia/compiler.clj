(ns arcadia.compiler
  (:require [arcadia.config :refer [configuration]]
            clojure.string)
  (:import [System IO.Path IO.File IO.StringWriter Environment]
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
        (clojure.string/replace #"\/" ".")
        (clojure.string/replace "_" "-")))

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

(defn read-file [file]
  (binding [*read-eval* false]
    (read-string (slurp file :encoding "utf8"))))

(defn first-form-is-ns? [asset]
  (boolean
    (when-let [[frst & rst] (seq (read-file asset))]
      (= frst 'ns))))

(defn correct-ns? [asset]
  (let [[frst scnd & rst] (read-file asset)
        expected-ns (asset->ns asset)]
    (= scnd expected-ns)))


(defn should-compile? [file]
  (if (File/Exists file)
    (and (not (re-find #"data_readers.clj$" file))
         (first-form-is-ns? file)
         (correct-ns? file))))

(defn import-asset [asset]
  (let [config @configuration
        verbose (config :verbose)
        {:keys [assemblies
                load-path
                warn-on-reflection
                unchecked-math
                compiler-options
                enabled
                debug]}
        (config :compiler)
        assemblies (or assemblies
                       (assemblies-path))]
    (if (and enabled (should-compile? asset))
      (try
        (let [namespace (asset->ns asset)
              errors (StringWriter.)]
          (binding [*compile-path* assemblies
                    *debug* debug
                    *warn-on-reflection* warn-on-reflection
                    *unchecked-math* unchecked-math
                    *compiler-options* compiler-options
                    *err* errors]
            (if (config :verbose)
              (Debug/Log
                (str "Compiling " (name namespace) "...")))
            (compile namespace)
            (doseq [error (remove empty? (clojure.string/split (.ToString errors) #"\n"))]
                 (Debug/LogWarning error))
            (AssetDatabase/Refresh ImportAssetOptions/ForceUpdate)))
          (catch clojure.lang.Compiler+CompilerException e
            (Debug/LogError (str (.. e InnerException Message) " (at " (.FileSource e) ":" (.Line e) ")")))
          (catch Exception e
            (Debug/LogException e)))
      (if (config :verbose)
        (Debug/LogWarning (str "Skipping " asset ", "
                               (cond (not enabled)
                                     "compiler is disabled"
                                     (not (first-form-is-ns? asset))
                                     "first form is not ns"
                                     (not (correct-ns? asset))
                                     "namespace in ns form does not match file name"
                                     :else
                                     "not sure why")))))))

(defn import-assets [imported]
  (doseq [asset (clj-files imported)]
    (import-asset asset)))

(defn delete-assets [deleted]
  (doseq [asset (clj-files deleted)]
    ))
