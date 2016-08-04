(ns arcadia.internal.leiningen
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [arcadia.internal.file-system :as fs]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]))

;; ============================================================
;; implementation

;; ------------------------------------------------------------
;; state

(defn- state []
  (@state/state ::leiningen))

(def ^:private update-state
  (state/updater ::leiningen))

;; ------------------------------------------------------------
;; state grammar, since we love that now

(s/def ::dependency (as/collude [] string?))

(s/def ::dependencies (as/collude #{} ::dependency))

(s/def ::defproject (s/keys :req-un [::dependencies]))

(s/def ::path string?)

(s/def ::project (s/keys :req-un [::path ::defproject]))

(s/def ::projects (as/collude #{} ::project))

(s/def ::leiningen (s/keys :req-un [::projects]))

;; ------------------------------------------------------------

(defn- compute-raw-dependencies [data]
  (into [] (comp (map :defproject) (map :dependencies) cat) data))

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

;; (defn- leiningen-loadpaths []
;;   (let [config @configuration]
;;     (for [m (:config-maps config)
;;           :when (= :leiningen (:type m))
;;           :let [p (Path/GetDirectoryName (.FullName (io/as-file (:path m))))]
;;           sp (or (:source-paths m) ["src" "test"])]
;;       (combine-paths p sp))))


;; ============================================================
;; public facing

(declare gather-data compute-projects compute-raw-dependencies update-state)

(defn refresh!
  "Hits disk to update Arcadia state with Leiningen-relevent information, keyed to ::leiningen. Returns total Arcadia state immediately after update completes."
  []
  (let [data (gather-data)
        projects (compute-projects data)
        dependencies (compute-raw-dependencies data)]
    (update-state
      (fn [x]
        (assoc x :projects projects)))))

(defn projects
  "Returns vector of information for directories under Assets that Arcadia currently considers Leiningen projects. Does not hit disk.

Elements of the returned vector have the following structure:

{:path  <string representation of absolute path to directory of leiningen project>
 :loadpath <vector of strings representing absolute path(s) to leiningen project root(s)>
 :name <string; name of the leiningen project>}
"
  [])

(defn dependencies
  "Return vector of what Arcadia currently considers to be dependency coordinates specified in Leiningen project files. Does not hit disk."
  [])


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
