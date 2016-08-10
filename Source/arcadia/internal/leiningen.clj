(ns arcadia.internal.leiningen
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [arcadia.internal.file-system :as fs]
            [arcadia.packages.data :as pd]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]
            [arcadia.compiler :as compiler]))

;; ------------------------------------------------------------
;; grammar

(s/def ::dependencies (as/collude [] ::pd/dependency))

(s/def ::name string?)

(s/def ::version string?)

(s/def ::body map?)

(s/def ::source-paths
  (as/collude [] ::fs/path))

(s/def ::defproject
  (s/keys :req [::fs/path
                ::name
                ::pd/version
                ::dependencies
                ::body]))

(s/def ::project (s/keys :req [::fs/path ::defproject]))

(s/def ::projects (as/collude [] ::project))

;; ============================================================

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
            (.FullName (fs/info file-name))
            ": " problem))))))

(defn- read-lein-project-file [file]
  (let [raw (slurp file)]
    (ensure-readable-project-file file raw)
    (read-string raw)))

(defn- leiningen-project-file? [fi]
  (= "project.clj" (.Name (fs/info fi))))

(s/fdef project-file-data
  :ret ::defproject)

(defn project-file-data [pf]
  (let [[_ name version & body] (read-lein-project-file pf)
        body (apply hash-map body)]
    {::fs/path (fs/path pf)
     ::name name
     ::version version
     ::dependencies (vec (:dependencies body))
     ::body body}))

(s/fdef project-data
  :ret ::project)

(defn project-data [dir]
  (let [dir (fs/directory-info dir)]
    (if (.Exists dir)
      (let [project-file (fs/file-info
                           (fs/path-combine dir "project.clj"))]
        (if (.Exists project-file)
          {::fs/path (fs/path dir)
           ::defproject (project-file-data project-file)}
          (throw
            (Exception.
              (str "No leiningen project file found at " (.FullName project-file))))))
      (throw
        (ArgumentException.
          (str "Directory " (.FullName dir) " does not exist"))))))

(defn- leiningen-structured-directory? [^DirectoryInfo di]
  (boolean
    (some leiningen-project-file?
      (.GetFiles di))))

(defn leiningen-project-directories []
  (->> (.GetDirectories (DirectoryInfo. "Assets"))
    (filter leiningen-structured-directory?)))

;; (defn- leiningen-loadpaths []
;;   (let [config @configuration]
;;     (for [m (:config-maps config)
;;           :when (= :leiningen (:type m))
;;           :let [p (Path/GetDirectoryName (.FullName (io/as-file (:path m))))]
;;           sp (or (:source-paths m) ["src" "test"])]
;;       (combine-paths p sp))))


;; ============================================================
(s/fdef all-project-data
  :ret ::projects)

(defn all-project-data []
  (into []
    (map project-data)
    (leiningen-project-directories)))

;; ============================================================
;; loadpath

(s/fdef project-data-loadpath
  :args (s/cat :project ::project)
  :ret ::fs/path)

(defn project-data-loadpath [{{{:keys [source-paths]} ::body} ::defproject,
                              p1 ::fs/path}]
  (if source-paths
    (map (fn [p2]
           (Path/Combine p1 p2))
      source-paths)
    [(Path/Combine p1 "src")]))

(defn leiningen-loadpaths []
  (->> (all-project-data)
       (mapcat project-data-loadpath)))

(defn leiningen-loadpaths-string []
  (clojure.string/join Path/PathSeparator
    (leiningen-loadpaths)))

(compiler/add-loadpath-extension-fn ::loadpath-fn #'leiningen-loadpaths-string)

;; ============================================================
;; hook up listeners. should be idempotent.


;; ((::fw/add-listener (aw/asset-watcher))
;;  ::fw/create-modify-delete-file
;;  ::config-reload
;;  (fn [{:keys [::fw/time ::fw/path]}]
;;    ;; stuff happens here
;;    ))




(comment
  ((::fw/add-listener (aw/asset-watcher))
   ::fw/create-modify-delete-file
   ::config-reload
   (fn [{:keys [::fw/path]}]
     (when (and
             (leiningen-project-file? path)
             (let [[_ parent grandparent] (fs/path-supers (fs/path path))]
               (leiningen-structured-directory? (info parent)) ;; this is stupid right now
               (= )))))))
