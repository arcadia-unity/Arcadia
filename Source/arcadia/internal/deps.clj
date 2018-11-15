(ns arcadia.internal.deps
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [arcadia.internal.file-system :as fs]
            [arcadia.packages.data :as pd]
            [clojure.spec.alpha :as s]
            [clojure.edn :as edn]
            [arcadia.internal.spec :as as]
            [arcadia.compiler :as compiler]
            [arcadia.config :as config])
  (:import [System.Text.RegularExpressions Regex]
           [System.IO FileSystemInfo DirectoryInfo Path]))

;; ============================================================
;; filesystem

(defn deps-project-file? [fi]
  (when-let [fi (fs/info fi)]
    (= "deps.edn" (.Name fi))))

(defn- deps-structured-directory? [^DirectoryInfo di]
  (boolean
   (some deps-project-file?
         (.GetFiles di))))

(defn project-directories []
  (->> (.GetDirectories (DirectoryInfo. "Assets"))
       (filter deps-structured-directory?)))

(defn deps-files []
  (->> (project-directories)
       (map #(Path/Combine (.FullName %) "deps.edn"))))

(defn deps-maps []
  (->> (deps-files)
       (map #(edn/read-string (slurp % :encoding "utf8")))))

;; ============================================================
;; loadpath

(defn deps-loadpaths []
  (let [paths (->> (deps-maps)
                   (map :paths))]
    (mapcat
     (fn [dir paths]
       (map #(Path/Combine (str dir) %) paths))
     (project-directories)
     paths)))

(defn deps-loadpaths-string []
  (clojure.string/join Path/PathSeparator
                       (deps-loadpaths)))

(compiler/add-loadpath-extension-fn ::loadpath-fn #'deps-loadpaths-string)
