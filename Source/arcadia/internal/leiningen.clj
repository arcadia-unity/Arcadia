(ns arcadia.internal.leiningen
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [arcadia.internal.file-system :as fs]
            [arcadia.packages.data :as pd]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]))

;; ------------------------------------------------------------
;; grammar

(s/def ::dependencies (as/collude [] ::pd/dependency))

(s/def ::name string?)

(s/def ::version string?)

(s/def ::defproject
  (s/keys :req [::fs/path
                ::name
                ::pd/version
                ::dependencies]))

(s/def ::project (s/keys :req [::fs/path ::defproject]))

(s/def ::projects (as/collude [] ::project))

;; ------------------------------------------------------------

;; should use conform instead
(defn- compute-raw-dependencies [data]
  (into [] (comp (map ::defproject) (mapcat ::dependencies)) data))

;; ============================================================

(defn- detect-leiningen-projects? []
  ;; for now:
  true
  ;; (boolean
  ;;   (:detect-leiningen-projects @configuration))
  )

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

(defn- read-lein-project-file [file]
  (let [raw (slurp file)]
    (ensure-readable-project-file file raw)
    (read-string raw)))

(defn- load-leiningen-configuration-map [file]
  (let [[_ name version & rst] (read-lein-project-file file)
        m (apply hash-map rst)]
    (-> m 
      (select-keys [:dependencies :source-paths])
      (assoc
        :type :leiningen
        :path (.FullName (fs/info file)),
        :name (str name),
        :version version,)
      (update :dependencies process-coordinates))))

(defn- leiningen-project-file? [fi]
  (= "project.clj" (.Name (fs/info fi))))

(s/fdef project-file-data
  :ret ::defproject)

(defn project-file-data [pf]
  (let [[_ name version & body] (read-lein-project-file pf)]
    {::fs/path (fs/path pf)
     ::name name
     ::version version
     ::dependencies (vec (:dependencies (apply hash-map body)))}))

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

(defn- leiningen-project-files []
  (for [^DirectoryInfo di (leiningen-project-directories)
        ^FileInfo fi (.GetFiles di)
        :when (leiningen-project-file? fi)]
    fi))

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
