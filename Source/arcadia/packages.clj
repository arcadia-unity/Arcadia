(ns arcadia.packages
  (:require [clojure.string :as string])
  (:import XmlReader
           System.Net.WebClient
           [System.IO File StringReader]))

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
  ["http://search.maven.org/remotecontent?filepath="
   "https://clojars.org/repo/"])

(defn base-url [group artifact version]
  (let [group (string/replace group "." "/")]
   (str group "/"
        artifact "/"
        version "/"
        artifact "-"
        version)))

(defn jar-url [group artifact version] (str (base-url group artifact version) ".jar"))
(defn jar-urls [group artifact version] (map #(str % (jar-url group artifact version)) url-prefixes))
(defn pom-url [group artifact version] (str (base-url group artifact version) ".pom"))
(defn pom-urls [group artifact version] (map #(str % (pom-url group artifact version)) url-prefixes))

(defn download-file-or-nil [url file]
  (with-open [wc (WebClient.)]
    (try (.. wc (DownloadFile url file))
      file
      (catch System.Net.WebException e
        nil))))

(defn download-file [urls file]
  (->> urls
       (map #(download-file-or-nil % file))
       (remove nil?)
       first))

(defn download-string-or-nil [url]
  (with-open [wc (WebClient.)]
    (try (.. wc (DownloadString url))
      (catch System.Net.WebException e
        nil))))

(defn download-string [urls]
  (->> urls
       (map download-string-or-nil)
       (remove nil?)
       first))

(defn dependencies
  [[group artifact version]]
  (let [poms (pom-urls group artifact version)]
    (->> (download-string poms)
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

(defn version-number [s]
  (->> (string/split s #"\.")
       (map #(Int32/Parse %))
       reverse
       (interleave (map #(Math/Pow 100 %) (range)))
       (partition 2)
       (map (partial apply *))
       (apply +)
       int))

(defn most-recent-versions [deps]
  (->> (group-by (juxt first second) deps)
     vals
     (map (fn [vs] (->> vs
                        (sort-by #(version-number (last %)))
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
        (jar-urls group artifact version)
        (str (gensym (str group "-" artifact "-" version "-")) ".jar")))))

(defn download-jars
  [group-artifact-version]
  (->> (all-unique-dependencies group-artifact-version)
       (concat [group-artifact-version])
       (map download-jar)))

(defn should-extract? [^ZipEntry e]
  (not (or (re-find #"^META-INF" (.Name e))
           (re-find #"^project\.clj" (.Name e)))))

(defn install [group-artifact-version]
  (doseq [jar (download-jars group-artifact-version)]
    (Packages/ExtractZipFile jar "Assets" should-extract?)))

;; (install ["thi.ng" "geom-voxel" "0.0.770"])