(ns arcadia.internal.leiningen
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [arcadia.internal.file-system :as fs]
            [arcadia.packages.data :as pd]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]
            [arcadia.compiler :as compiler]
            [arcadia.config :as config])
  (:import [System.Text.RegularExpressions Regex]
           [System.IO Path]))

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
;; defproject parsing for slackers and villains

(defn- ensure-readable-project-file [file-name, raw-file]
  (let [stringless (-> raw-file
                       (clojure.string/replace
                         #"(?m);;.*?$"
                         "")
                       (clojure.string/replace
                         #"(\".*?((\\\\+)|[^\\])\")|\"\"" ;; fancy string matcher
                         ""))
        problem (cond
                  (re-find #"~" stringless)
                  "'~' found"

                  (re-find #"#[^_]" stringless)
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

;; ============================================================
;; filesystem

(def ^:private assets-dir
  (.FullName (fs/info "Assets")))

;; if anyone can think of another way lemme know -tsg
(defn leiningen-project-file? [fi]
  (when-let [fi (fs/info fi)] ;; not sure this stupid function returns nil if input is already a filesysteminfo for a non existant filesystemthing
    (and (= "project.clj" (.Name fi))
         (= assets-dir (first (drop 2 (fs/path-supers (.FullName fi)))))
         (boolean
           (re-find #"(?m)^\s*\(defproject(?:$|\s.*?$)" ;; shift to something less expensive
             (slurp fi))))))

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
;; hook up listener

(aw/add-listener ::fw/create-modify-delete-file ::config-reload
  (str Path/DirectorySeparatorChar "project.clj")
  (fn [{:keys [::fw/path]}]
    (when (leiningen-project-file? path)
      (config/update!))))
