(ns ^{:doc "Integration with the Unity compiler pipeline. Typical
           Arcadia users will not need to use this namespace,
           but the machinery is exposed for those who do."
      :author "Tims Gardner and Ramsey Nasser"}
    arcadia.internal.compiler
  (:require [arcadia.internal.config :as config]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [arcadia.internal.state :as state]
            [arcadia.internal.spec :as as]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.editor-callbacks :as callbacks])
  (:import [Arcadia StringHelper]
           [clojure.lang Namespace]
           [System.IO Path File StringWriter Directory]
           [System.Text.RegularExpressions Regex]
           [System.Collections Queue]
           [System.Threading Thread ThreadStart]
           [UnityEngine Debug]))

(def main-thread System.Threading.Thread/CurrentThread)

(defn on-main-thread? []
  (.Equals main-thread System.Threading.Thread/CurrentThread))

(defn load-path []
  (seq (clojure.lang.RT/GetFindFilePaths)))

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
        (clojure.string/replace #"\.cljc?$" "")
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
  (boolean (re-find #"\.cljc?$" path)))

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

(defn aot-namespaces
  ([path nss]
   (aot-namespaces path nss nil))
  ([path nss {:keys [file-callback] :as opts}]
   ;; We want to ensure that namespaces are neither double-aot'd, nor
   ;; _not_ aot'd if already in memory.
   ;; In other words, we want to walk the forest of all the provided
   ;; namespaces and their dependency namespaces, aot-ing each
   ;; namespace we encounter in this walk exactly once. `:reload-all`
   ;; will re-aot encountered namespaces redundantly, potentially
   ;; invalidating old type references (I think). Normal `require`
   ;; will not do a deep walk over already-loaded namespaces. So
   ;; instead we rebind the *loaded-libs* var to a ref with an empty
   ;; set and call normal `require`, which gives the desired behavior.
   (let [loaded-libs' (binding [*compiler-options* (get (config/config) :compiler-options {})
                                *compile-path* path
                                *compile-files* true
                                arcadia.internal.compiler/*exporting?* true
                                clojure.core/*loaded-libs* (ref #{})]
                        (doseq [ns nss]
                          (require ns)
                          (when file-callback
                            (file-callback ns)))
                        @#'clojure.core/*loaded-libs*)]
     (dosync
       (alter @#'clojure.core/*loaded-libs* into @loaded-libs'))
     nil)))

(defn aot-asset [path asset]
  (when (should-compile? asset)
    (Debug/Log (str "Compiling " asset " to " path))
    (aot-namespaces path (asset->ns asset))))

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
        (if (or *compile-files* ;; annoying fix for issue #212
                (as/loud-valid? ::loadpath-extension-fns res))
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

(defn configuration-extensions []
  (map #(System.IO.Path/GetFullPath %)
    (get-in @state/state [::config/config :source-paths])))

(defn loadpath-extension-string
  ([] (loadpath-extension-string nil))
  ([opts]
   (str/join Path/PathSeparator
     (concat
       (loadpath-extensions opts)
       (configuration-extensions)))))

(defn refresh-loadpath
  ([]
   (callbacks/add-callback
     #(Arcadia.Initialization/SetClojureLoadPath)))
  ([config]
   (refresh-loadpath)))

(state/add-listener ::config/on-update ::refresh-loadpath #'refresh-loadpath)

;; ============================================================
;; listeners

(defn reload-if-loaded [path]
  (when (some-> path asset->ns find-ns)
    (import-asset path)))

(defn live-reload-listener [{:keys [::fw/path]}]
  (callbacks/add-callback #(reload-if-loaded path)))

(defn start-watching-files []
  ;; (UnityEngine.Debug/Log "Starting to watch for changes in Clojure files.")
  (aw/add-listener
    ::fw/create-modify-delete-file
    ::live-reload-listener
    [".clj" ".cljc"]
    #'live-reload-listener))

(defn stop-watching-files []
  (aw/remove-listener ::live-reload-listener))

(defn manage-reload-listener [{:keys [reload-on-change]}]
  (if reload-on-change
    (start-watching-files)
    (stop-watching-files)))

(state/add-listener ::config/on-update ::live-reload
  #'manage-reload-listener)

(manage-reload-listener (config/config))
