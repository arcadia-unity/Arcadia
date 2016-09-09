(ns arcadia.packages
  (:use clojure.pprint
        clojure.repl)
  (:require [arcadia.compiler :refer [dir-seperator-re]]
            [arcadia.config :as config]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.file-system :as fs]
            [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.leiningen :as lein]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.spec :as as]
            [arcadia.internal.state :as state]
            [arcadia.internal.thread :as thr]
            [arcadia.packages.data :as pd]
            [clojure.clr.io :as io]
            [clojure.spec :as s]
            [clojure.string :as string])
  (:import XmlReader
           [UnityEngine Debug]
           [System.Collections Queue]
           [System.Net WebClient WebException WebRequest HttpWebRequest]
           [System.IO Directory DirectoryInfo Path File FileInfo FileSystemInfo StringReader Path]
           [Ionic.Zip ZipEntry ZipFile ExtractExistingFileAction]))

;; xml reading
(defmulti xml-content (fn [^XmlReader xml] (.NodeType xml)))
(defmethod xml-content
  :default [^XmlReader xml]
  nil)

(defmethod xml-content
  XmlNodeType/Text [^XmlReader xml]
  (string/trim (.Value xml)))

(defmethod xml-content
  XmlNodeType/Element [^XmlReader xml]
  (keyword (.Name xml)))

(defn read-xml-from-reader [^XmlReader xml]
  (loop [accumulator []]
    (let [depth (.Depth xml)]
      (.Read xml)
      (cond (or (.EOF xml)
                (= (.NodeType xml)
                   XmlNodeType/EndElement)) (seq (filter identity accumulator)) ;; remove nils?
            (.IsEmptyElement xml) (recur accumulator)
            :else (if (> (.Depth xml)
                         depth)
                    (recur (conj accumulator
                                 (xml-content xml)
                                 (read-xml-from-reader xml)))
                    (recur (conj accumulator
                                 (xml-content xml))))))))

(defn read-xml [source]
  (read-xml-from-reader (XmlReader/Create (StringReader. source))))

(def flatten-set #{:licenses :dependencies :exclusions :resources :testResources :repositories})

(defn make-map [stream]
  (if (seq? stream)
    (reduce (fn [acc [k v]]
              (assoc acc k (if (flatten-set k)
                             (mapv make-map (take-nth 2 (drop 1 v)))
                             (make-map v))))
            {}
            (partition 2 stream))
    stream))

(def temp-dir "Temp/Arcadia")

(defmacro in-dir [dir & body]
  `(let [original-cwd# (Directory/GetCurrentDirectory)]
     (try
       (do (Directory/CreateDirectory ~dir)
           (Directory/SetCurrentDirectory ~dir)
           ~@body)
       (finally
         (Directory/SetCurrentDirectory original-cwd#)))))

(def url-prefixes
  ["http://central.maven.org/maven2/"
   "http://search.maven.org/remotecontent?filepath="
   "https://oss.sonatype.org/content/repositories/snapshots/"
   "https://repo1.maven.org/maven2/"
   "https://clojars.org/repo/"])

(defn url-exists? [^String url]
  (let [req ^HttpWebRequest (WebRequest/Create url)]
    (try
      (set! (.Method req) "HEAD")
      (set! (.Timeout req) 1000)
      (.GetResponse req)
      true
      (catch WebException c
        false))))

(defn short-base-url [group artifact version]
  (let [group (string/replace group "." "/")]
   (str group "/"
        artifact "/"
        version "/")))

(defn download-file [url file]
  (with-open [wc (WebClient.)]
    (try (.. wc (DownloadFile url file))
      file
      (catch System.Net.WebException e
        nil))))

(defn download-string [url]
  (with-open [wc (WebClient.)]
    (try
      (.. wc (DownloadString url))
      (catch System.Net.WebException e
        nil))))

(defn base-url [group artifact version]
  (str (short-base-url group artifact version)
       artifact "-"
       version))

(defn base-metadata-url [group artifact version]
  (str (short-base-url group artifact version) "maven-metadata.xml"))

(defn metadata-urls [group artifact version]
  (->> (map #(str % (base-metadata-url group artifact version)) url-prefixes)
       (filter url-exists?)))

(defn metadata-url [group artifact version]
  (first (metadata-urls group artifact version)))

(defn snapshot-timestamp
  [group artifact version]
  (if-let [metadata (metadata-url group artifact version)]
    (->> (download-string metadata)
         read-xml
         make-map
         :metadata
         :versioning
         :snapshot
         ((juxt :timestamp :buildNumber))
         (string/join "-"))))

(defn base-jar-url [group artifact version]
  (string/replace (str (base-url group artifact version) ".jar")
                  #"-SNAPSHOT.jar"
                  (str "-" (snapshot-timestamp group artifact version) ".jar")))

(defn base-pom-url [group artifact version]
  (string/replace (str (base-url group artifact version) ".pom")
                  #"-SNAPSHOT.pom"
                  (str "-" (snapshot-timestamp group artifact version) ".pom")))

(defn jar-urls [group artifact version]
  (->> (map #(str % (base-jar-url group artifact version)) url-prefixes)
       (filter url-exists?)))

(defn pom-urls [group artifact version]
  (->> (map #(str % (base-pom-url group artifact version)) url-prefixes)
       (filter url-exists?)))

(defn jar-url [group artifact version]
  (first (jar-urls group artifact version)))

(defn pom-url [group artifact version]
  (first (pom-urls group artifact version)))

(defn dependencies
  [[group artifact version]]
  (let [pom (pom-url group artifact version)]
    (->> (download-string pom)
         read-xml 
         make-map
         :project
         :dependencies
         (remove #(= (% :scope) "test"))
         (remove #(= (% :artifactId) "clojure"))
         (map (juxt :groupId :artifactId :version)))))

(defn all-dependencies [[group artifact version]]
  (into #{}
        (apply concat
               (take-while seq (iterate #(mapcat dependencies %)
                                        (dependencies [group artifact version]))))))

(defn most-recent-versions [deps]
  (->> (group-by (juxt first second) deps)
     vals
     (map (fn [vs] (->> vs
                     (sort-by last)
                     last)))))

(defn all-unique-dependencies [group-artifact-version]
  (-> group-artifact-version
      all-dependencies
      most-recent-versions))

(defn download-jar
  [[group artifact version]]
  (let [temp (fs/directory-info (fs/path-combine temp-dir "Jars"))]
    (when-not (.Exists temp) (.Create temp))
    (Path/GetFullPath
      (download-file
        (jar-url group artifact version)
        (fs/path-combine (.FullName temp)
          (str (gensym (str group "-" artifact "-" version "-")) ".jar"))))))

(defn download-jars
  [group-artifact-version]
  (doall (->> (all-unique-dependencies group-artifact-version)
              (concat [group-artifact-version])
              (map download-jar))))

(defn should-extract? [^ZipEntry e]
  (not (or (re-find #"^META-INF" (.FileName e))
           (re-find #"^project\.clj" (.FileName e))
           (re-find #"/$" (.FileName e)))))

(defn make-directories [^ZipEntry e base]
  (let [dir (->> (string/split (.FileName e) dir-seperator-re)
                 (drop-last 1)
                 (string/join (str Path/DirectorySeparatorChar))
                 (conj [base])
                 (string/join (str Path/DirectorySeparatorChar)))]
    (Directory/CreateDirectory dir)
    dir))

(defn- as-directory ^DirectoryInfo [x]
  (if (instance? DirectoryInfo x)
    x
    (DirectoryInfo. x)))

(def library-directory "Assets/Arcadia/Libraries")

(defn ensure-library-directory ^DirectoryInfo []
  (let [d (as-directory library-directory)]
    (when-not (.Exists d)
      (.Create d))
    d))

(defn normalize-coordinates [group-artifact-version]
  (case (count group-artifact-version)
    3 (map str group-artifact-version)
    2 (let [[group-artifact version] group-artifact-version]
        [(or (namespace group-artifact)
             (name group-artifact))
         (name group-artifact)
         version])
    (throw (Exception. (str "Package coordinate must be a vector with 2 or 3 elements, got " group-artifact-version)))))

;; ============================================================
;; extraction

(s/def ::coord ::pd/normal-dependency)

(s/def ::root ::fs/path)

(s/def ::entries
  (s/coll-of #(instance? Ionic.Zip.ZipEntry %)))

(s/def ::error #(instance? Exception %))

(s/def ::succeeded
  #{true false})

(s/def ::extraction
  (s/or
    :succeeded  (s/and
                  (s/keys :req [::coord ::root ::entries ::succeeded])
                  #(::succeeded %))
    :failed     (s/and
                  (s/keys :req [::coord ::error ::succeeded])
                  #(not (::succeeded %)))
    :unresolved (s/keys :req [::coord ::root ::entries])))

(s/fdef extract
  :args (s/cat :data ::pd/vector-dependency)
  :ret ::extraction)

(defn- extract [coord]
  (Debug/Log (str "Installing " coord))
  (try
    (let [jar (download-jar coord)
          extractable-entries (filter should-extract?
                                (ZipFile/Read jar))]
      (doseq [^ZipEntry zip-entry extractable-entries]
        (make-directories zip-entry library-directory)
        (.Extract zip-entry library-directory
          ExtractExistingFileAction/OverwriteSilently))
      {::coord (pd/normalize-coordinates coord)
       ::root (first
                (fs/path-split
                  (.FileName
                    (first extractable-entries))))
       ::entries extractable-entries
       ::succeeded true})
    (catch Exception e
      {::coord (pd/normalize-coordinates coord)
       ::error e
       ::succeeded false})))

;; ============================================================
;; installation

;; stupid for now
(s/def ::manifest
  map?)

(s/def ::install-opts
  (s/keys :opt [::manifest]))

(s/fdef install
  :args (s/or
          :no-opts (s/cat :group-artifact-version ::pd/vector-dependency)
          :with-opts (s/cat
                       :group-artifact-version ::pd/vector-dependency
                       :opts ::install-opts)) ;; would like to broaden this
  :ret (s/coll-of ::extraction))

(defn- blocked-coordinate? [coord installed]
  (let [{:keys [::pd/artifact]} (pd/normalize-coordinates coord)]
    (or (= artifact "clojure")
        (contains? installed
          (pd/normalize-coordinates coord)))))

(defn install
  ([group-artifact-version]
   (install group-artifact-version nil))
  ([group-artifact-version, {{:keys [::installed]} ::manifest, :as opts}]
   (let [gav (vec (normalize-coordinates group-artifact-version))]
     (when-not (blocked-coordinate? gav installed)
       (->> (cons gav (all-dependencies gav))
            (map vec) ;; stricter
            (concat (map (juxt ::pd/group ::pd/artifact ::pd/version)
                      installed))
            (remove #(blocked-coordinate? % installed))
            most-recent-versions
            (mapv extract))))))

(def library-manifest-name "manifest.edn")

;; can't find how to do this with spit, don't want to waste more time
(defn- write-or-overwrite-file [file, contents]
  (File/WriteAllText
    (fs/path (fs/file-info file))
    (prn-str contents)))

(defn- write-library-manifest
  ([]
   (write-library-manifest {}))
  ([contents]
   (write-or-overwrite-file
     (Path/Combine
       (.FullName (ensure-library-directory))
       library-manifest-name)
     contents)))

(defn library-manifest []
  (let [inf (fs/file-info
              (fs/path-combine
                (ensure-library-directory)
                library-manifest-name))]
    (if (.Exists inf)
      (clojure.edn/read-string
        (slurp inf))
      (do (write-library-manifest)
          (library-manifest)))))

(defn flush-libraries []
  (let [d (ensure-library-directory)]
    (doseq [^FileSystemInfo fi (concat
                                 (.GetDirectories d)
                                 (.GetFiles d))
            :when (not
                    (#{library-manifest-name
                       (str library-manifest-name ".meta")}
                     (.Name fi)))]
      (fs/delete fi)))
  (write-library-manifest))

;; ============================================================
;; do everything

(defn- install-step [dep]
  (let [manifest (library-manifest)]
    (when-let [install-data (install dep {::manifest manifest})]
      (write-library-manifest
        (update manifest ::installed
          (fnil into #{}) (map ::coord) install-data))
      install-data)))

(defonce ^:private install-queue
  (Queue/Synchronized (Queue.)))

(defmacro ^:private with-log-out [& body]
  `(let [s# (System.IO.StringWriter.)
         res# (binding [*out* s#]
                ~@body)]
     (Debug/Log (str s#))
     res#))

(defn- drain-install-queue []
  (let [carrier (volatile! {::result []})]
    (locking install-queue
      (while (> (count install-queue) 0)
        (let [{:keys [::callback]} (.Dequeue install-queue)
              _ (vswap! carrier assoc ::callback callback)
              user-deps (:dependencies (config/config))
              lein-deps (mapcat #(get-in % [::lein/defproject ::lein/dependencies])
                          (lein/all-project-data))
              work (->> (concat user-deps lein-deps)
                        (map pd/normalize-coordinates)
                        (map (juxt ::pd/group ::pd/artifact ::pd/version))
                        most-recent-versions)]
          (with-log-out
            (println "Beginning installation:")
            (doseq [wk work]
              (println "------------------------------")
              (pprint wk)))
          (let [result (into [] (mapcat install-step) work)]
            (with-log-out
              (println "Installation complete. Installed:")
              (doseq [extr result]
                (println "------------------------------")
                (pprint
                  (select-keys extr [::succeeded ::coord]))))
            (vswap! carrier update ::result conj result)))))
    (when-let [cb (::callback @carrier)]
      (cb (::result @carrier)))))

(defonce install-errors (atom []))

(defn install-all-deps
  ([] (install-all-deps nil))
  ([callback]
   (thr/start-thread
     (fn []
       (try
         (.Enqueue install-queue {::callback callback})
         (drain-install-queue)
         (catch Exception e
           (swap! install-errors conj e)))))))

;; ============================================================
;; listeners

(state/add-listener ::config/on-update ::install-all-deps
  (fn [_] (install-all-deps)))
