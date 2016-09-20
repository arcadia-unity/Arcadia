(ns ^{:doc "Integration with the Unity compiler pipeline. Typical
           Arcadia users will not need to use this namespace,
           but the machinery is exposed for those who do."
      :author "Tims Gardner and Ramsey Nasser"}
    arcadia.compiler
  (:require [arcadia.config :as config]
            [clojure.spec :as s]
            [clojure.string :as str]
            [arcadia.internal.state :as state]
            [arcadia.internal.spec :as as]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.filewatcher :as fw])
  (:import [Arcadia StringHelper]
           [System.IO Path File StringWriter Directory]
           [System.Text.RegularExpressions Regex]
           [System.Collections Queue]
           [System.Threading Thread ThreadStart]
           [UnityEngine Debug]))

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

(def dir-seperator-re
  (re-pattern (Regex/Escape (str Path/DirectorySeparatorChar))))

(defn path->ns
  "Returns a namespace from a path
  
  => (path->ns \"foo/bar/baz.clj\")
  \"foo.bar.baz\""
  [p]
  (if-> p
        (clojure.string/replace #"\.clj$" "")
        (clojure.string/replace dir-seperator-re ".")
        (clojure.string/replace "_" "-")))

(defn relative-to-load-path
  "Sequence of subpaths relative to the load path, shortest first"
  [path]
  (->> (clojure.string/split path dir-seperator-re)
       rests
       reverse
       (map #(clojure.string/join Path/DirectorySeparatorChar %))
       (filter #(clojure.lang.RT/FindFile %))))

(defn clj-file?
  "Is `path` a Clojure file?"
  [path]
  (boolean (re-find #"\.clj$" path)))

(defn config-file?
  "Is `path` the configuration file?"
  [path]
  (= path
     (config/user-config-file)))

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

(defn dependencies [ns]
  (let [nss-mappings (->> ns
                          .getMappings  
                          vals
                          (filter var?)
                          (map #(.. % Namespace)))
        nss-aliases (->> ns
                         .getAliases  
                         vals)]
    (-> (into #{} (concat nss-aliases
                          nss-mappings))
        (disj ns
              (find-ns 'clojure.core)))))

(defn all-dependencies
  ([] (all-dependencies Namespace/all))
  ([nss]
   (reduce
     (fn [m ns]
       (reduce
         (fn [m* ns*]
           (update m* (.Name ns*)
                   (fnil conj #{}) (.Name ns)))
         m
         (dependencies ns)))
     {}
     (remove #{(find-ns 'user)} nss))))

(defn reload-ns [n]
  (Debug/Log (str "Loading " n))
  (require n :reload))

(defn reload-ns-and-deps [n]
  (let [dep-map (all-dependencies)]
    (loop [nss [n]
           loaded #{}]
      (when-not (empty? nss)
        (let [loaded (into loaded nss)]
          (doseq [ns nss]
            (reload-ns ns))
          (recur (remove loaded (mapcat dep-map nss))
                 loaded))))))

(defn import-asset [asset]
  (if (should-compile? asset)
    (reload-ns (asset->ns asset))
    (Debug/Log (str "Not Loading " asset))))

(def ^:dynamic *exporting?* false)

(defn aot-namespace
  "Compile a namespace `ns` and all namespaces it depends on
  to disk, placing resulting assemblies in `path`"
  [path ns]
  (binding [*compile-path* path
            *compile-files* true
            *exporting?* true]
    (require ns :reload-all)))

(defn aot-namespaces [path nss]
  (doseq [ns nss]
    (Debug/Log (str "Compiling " ns " to " path))
    (aot-namespace path ns)))

(defn aot-asset [path asset]
  (when (should-compile? asset)
    (Debug/Log (str "Compiling " asset " to " path))
    (aot-namespace path (asset->ns asset))))

(defn aot-assets [path assets]
  (doseq [asset assets]
    (aot-asset path asset)))

(defn import-assets [imported]
  (when (some config-file? imported)
    (Debug/Log (str "Updating config"))
    (config/update!))
  (doseq [asset (clj-files imported)]
    (import-asset asset)
    #_ (import-asset asset)))

(defn delete-assets [deleted]
  (doseq [asset (clj-files deleted)]))

;; ============================================================
;; old liveload

;; (def import-queue (Queue/Synchronized (Queue.)))

;; (defn import-changed-files []
;;   (try
;;     (while (pos? (.Count import-queue))
;;       (let [file (.Dequeue import-queue)]
;;         (if (config-file? file)
;;           (config/update!)
;;           (import-asset file))))
;;     (catch Exception e
;;       (Debug/LogException e))))

;; (defn after? [^DateTime a ^DateTime b]
;;   (pos? (.CompareTo a b)))

;; (defn new? [file times]
;;   (after? (File/GetLastWriteTime file)
;;           (or (times file) (DateTime.))))

;; (defn each-new-file [root pattern f times-atom]
;;   (let [files
;;         ^|System.String[]|
;;         (Directory/GetFiles root
;;                             pattern
;;                             SearchOption/AllDirectories)]
;;     (loop [i 0]
;;       (let [file (aget files i)]
;;         (when (new? file @times-atom)
;;           (f file)
;;           (swap! times-atom assoc file DateTime/Now)))
;;       (if (< i (dec (count files))) (recur (inc i))))))

;; (defonce last-read-times (atom {}))
;; (defonce watching-files (atom true))

;; (defn enqueue-asset [asset]
;;   (.Enqueue import-queue asset))

;; (defn start-watching-files []
;;   (reset! watching-files true)
;;   (-> (gen-delegate
;;         ThreadStart []
;;         (while @watching-files
;;           (each-new-file "Assets" "*.clj" enqueue-asset last-read-times)
;;           (Thread/Sleep 100)))
;;       Thread.
;;       .Start))

;; (defn stop-watching-files []
;;   (reset! watching-files false))

;; ============================================================
;; stand-in while building new liveload

(defn start-watching-files []
  (throw (Exception. "still building liveload infrastructure")))

(defn stop-watching-files []
  (throw (Exception. "still building liveload infrastructure")))

;; ============================================================
;; loadpath handling

(s/def ::loadpath-extension-fns
  (s/map-of keyword? ifn?))

(defn add-loadpath-extension-fn [k f]
  (swap! state/state update ::loadpath-extension-fns
    (fn [loadpath-extensions]
      (let [res (assoc loadpath-extensions k f)]
        (if (as/loud-valid? ::loadpath-extension-fns res)
          res
          (throw
            (Exception.
              (str
                "Result of attempt to add loadpath extension function keyed to " k
                " fails:\n"
                (with-out-str
                  (s/explain ::loadpath-extension-fns res))))))))))

(defn remove-loadpath-extension-fn [k]
  (swap! state/state update ::loadpath-extensions dissoc k))

(defn loadpath-extensions
  ([] (loadpath-extensions nil))
  ([{:keys [::strict?],
     :as opts}]
   (let [ap (if strict?
              (fn ap [bldg _ f]
                (conj bldg (f)))
              (fn ap [bldg k f]
                (try
                  (conj bldg (f))
                  (catch Exception e
                    (Debug/Log
                      (str
                        "Encountered " (class e)
                        " exception for loadpath extension function keyed to "
                        k " during computation of loadpath."
                        e))
                    bldg))))]
     (reduce-kv ap [] (::loadpath-extension-fns @state/state)))))

(defn loadpath-extension-string
  ([] (loadpath-extension-string nil))
  ([opts]
   (str/join Path/PathSeparator
     (loadpath-extensions opts))))

(defn refresh-loadpath []
  (Arcadia.Initialization/SetClojureLoadPath))


;; ============================================================
;; listeners

(defn live-reload-listener [{:keys [::fw/path]}]
  (import-asset path))

(defn start-watching-files []
  (UnityEngine.Debug/Log "Starting to watch for changes in Clojure files.")
  (aw/add-listener
    ::fw/create-modify-delete-file
    ::live-reload-listener
    [".clj" ".cljc"]
    #'live-reload-listener))

(defn stop-watching-files []
  (aw/remove-listener ::live-reload-listener))

(defn manage-reload-listener [{:keys [:compiler/on-file-change]}]
  (if (= :reload on-file-change)
    (start-watching-files)
    (stop-watching-files)))

(manage-reload-listener (config/config))

(state/add-listener ::config/on-update ::live-reload
  #'manage-reload-listener)
