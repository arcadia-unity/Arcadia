(ns arcadia.packages
  (:require [clojure.string :as string]
            [clojure.clr.io :as io])
  (:import XmlReader
           [UnityEngine Debug]
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

(def temp-dir "Temp/Arcadia") ;; TODO where should this live?

(defmacro in-dir [dir & body]
  `(let [original-cwd# (Directory/GetCurrentDirectory)
         res# (do 
                (Directory/CreateDirectory ~dir)
                (Directory/SetCurrentDirectory ~dir)
                ~@body)]
     (Directory/SetCurrentDirectory original-cwd#)
     res#))

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
  (in-dir
    (str temp-dir "/Jars")
    (Path/GetFullPath
      (download-file
        (jar-url group artifact version)
        (str (gensym (str group "-" artifact "-" version "-")) ".jar")))))

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
  (Directory/CreateDirectory
    (->> (string/split (.FileName e) #"/")
         (drop-last 1)
         (string/join (str Path/DirectorySeparatorChar))
         (conj [base])
         (string/join (str Path/DirectorySeparatorChar))))
  e)

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

(defn install [group-artifact-version]
  (Debug/Log (str "Installing " (prn-str group-artifact-version)))
  (dorun
    (->> (download-jars group-artifact-version)
      (mapcat #(seq (ZipFile/Read %)))
      (filter should-extract?)
      (map #(make-directories % library-directory))
      ;; TODO do better than silently overwriting 
      (map #(.Extract % library-directory ExtractExistingFileAction/OverwriteSilently)))))

(def library-manifest-name "manifest.edn")

;; can't find how to do this with spit, don't want to waste more time
(defn- write-or-overwrite-file [file, contents]
  (File/WriteAllText (.FullName (io/as-file file)) contents))

(defn- write-library-manifest
  ([]
   (write-library-manifest "{}"))
  ([contents]
   (write-or-overwrite-file
     (Path/Combine
       (.FullName (ensure-library-directory))
       library-manifest-name)
     contents)))

;; oh for condcast->. Put this somewhere.
(defn- delete-fsi [^FileSystemInfo fsi]
  (condp instance? fsi
    DirectoryInfo (let [^DirectoryInfo fsi fsi]
                    (.Delete fsi true))
    FileInfo (let [^FileInfo fsi fsi]
               (.Delete fsi))
    (throw (Exception. "Expects instance of FileSystemInfo"))))

(defn flush-libraries []
  (let [d (ensure-library-directory)]
    (doseq [^FileSystemInfo fi (concat
                                 (.GetDirectories d)
                                 (.GetFiles d))
            :when (not
                    (#{library-manifest-name
                       (str library-manifest-name ".meta")}
                     (.Name fi)))]
      (delete-fsi fi)))
  (write-library-manifest))
