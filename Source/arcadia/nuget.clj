(ns arcadia.nuget
  (:require [clojure.string :as s]
            [arcadia.internal.leiningen :as lein]
            [arcadia.config :as config])
  (:import Newtonsoft.Json.JsonTextReader
           Newtonsoft.Json.Linq.JObject
           [Arcadia ProgressBar Shell]
           UnityEditor.EditorUtility
           UnityEngine.Debug
           [System.Diagnostics Process]
           [System.Xml XmlWriter XmlWriterSettings]
           [System.Text StringBuilder]
           [System.IO DirectoryInfo Directory File Path FileMode StreamReader]))

;;;; XML/CSPROJ Wrangling

(def ^:dynamic *xml-writer* nil)

(def settings
  (let [s (XmlWriterSettings.)]
    (set! (.Indent s) true)
    (set! (.OmitXmlDeclaration s) true)
    s))

(defmacro doc [& body]
  `(let [sb# (StringBuilder.)]
     (binding [*xml-writer* (XmlWriter/Create sb# settings)]
       (.WriteStartDocument *xml-writer*)
       ~@body
       (.WriteEndDocument *xml-writer*)
       (.Close *xml-writer*)
       (.ToString sb#))))

(defmacro elem [name & body]
  `(do
     (.WriteStartElement *xml-writer* ~name)
     ~@body
     (.WriteEndElement *xml-writer*)))

(defmacro attr [name value]
  `(.WriteAttributeString *xml-writer* ~name ~value))

(defmacro string [value]
  `(.WriteString *xml-writer* ~value))

(defn coords->csproj [data]
  (doc
   (elem "Project"
         (attr "Sdk" "Microsoft.NET.Sdk")
         (elem "PropertyGroup"
               (elem "OutputType" (string "Exe"))
               (elem "TargetFramework" (string "net461")))
         (elem "ItemGroup"
               (doseq [d data]
                 (elem "PackageReference"
                       (attr "Include" (str (first d)))
                       (attr "Version" (str (last d)))))))))


;;;; NuGet & JSON wrangling

(def nuget-exe-path (Path/Combine "Assets" "Arcadia" "Infrastructure" "NuGet.exe"))
(def external-packages-folder (Path/Combine "Arcadia" "Libraries"))
(def internal-packages-folder (Path/Combine "Assets" "Arcadia" "Libraries"))
(def external-package-files-folder (Path/Combine external-packages-folder "Files"))
(def package-lock-folder (Path/Combine external-packages-folder "obj"))
(def package-lock-file (Path/Combine package-lock-folder "project.assets.json"))

(defn restore
  ([coords] (restore coords (fn [])))
  ([coords donefn]
   (let [csproj-xml (coords->csproj coords)
         csproj-file (Path/Combine external-packages-folder  (str (gensym "arcadia-packages" ) ".csproj"))]
     (Directory/CreateDirectory external-packages-folder)
     (spit csproj-file csproj-xml)
     (ProgressBar/Start {:title "Restoring Packages" :info "" :progress 0})
     (Shell/MonoRun nuget-exe-path
                    (str "restore " csproj-file " -PackagesDirectory " external-package-files-folder)
                    {:output (fn [s] (swap! ProgressBar/State assoc :info (.Trim s)))
                     :error (fn [s]
                              (ProgressBar/Stop)
                              (Debug/LogError s))
                     :done (fn []
                             (File/Delete csproj-file)
                             (donefn))}))))


(defn get-json-keys
  [^System.Collections.Generic.IDictionary|`2[System.String,Newtonsoft.Json.Linq.JToken]| o]
  (when o
    (.get_Keys o)))

(defn cp-r-info [from to]
  (doseq [dir (.GetDirectories from)]
    (cp-r-info dir (.CreateSubdirectory to (.Name dir))))
  (doseq [file (.GetFiles from)]
    (.CopyTo file (Path/Combine (.FullName to) (.Name file)) true)))

(defn cp-r [from to]
  (cp-r-info (DirectoryInfo. from)
             (DirectoryInfo. to)))

(defn install [destination]
  (let [json-path package-lock-file
        json (JObject/Parse (slurp json-path :encoding "utf8"))
        obj (.SelectToken json "$.targets['.NETFramework,Version=v4.6.1']")
        to-copy (apply hash-set
                        (mapcat (fn [k]
                                  (let [name+version (s/lower-case k)
                                        compile-obj (.. obj (GetValue k) (GetValue "compile"))
                                        runtime-obj (.. obj (GetValue k) (GetValue "runtime"))
                                        compile-files (remove #(= "_._" (Path/GetFileName %)) (get-json-keys compile-obj))
                                        runtime-files (remove #(= "_._" (Path/GetFileName %)) (get-json-keys runtime-obj))]
                                    (concat
                                     [(Path/Combine name+version "content")]
                                     (when runtime-obj
                                       (map #(Path/Combine name+version %) runtime-files))
                                     (when compile-obj
                                       (map #(Path/Combine name+version %) compile-files)))))
                                (get-json-keys obj)))
        total (count to-copy)
        current (volatile! 0)]
    (Directory/CreateDirectory destination)
    (doseq [f to-copy]
      (let [full-source (Path/Combine external-package-files-folder f)]
        (swap! ProgressBar/State assoc
               :title "Installing Packages"
               :info f
               :progress (float (/ @current total)))
        (vswap! current inc)
        (cond
          (Directory/Exists full-source)
          (cp-r (Path/Combine external-package-files-folder f) destination)
          (File/Exists full-source)
          (File/Copy full-source
                     (Path/Combine destination (Path/GetFileName f))
                     true))))
    (ProgressBar/Stop)))

(defn clean [dir]
  (let [di (DirectoryInfo. dir)]
    (doseq [file (.EnumerateFiles di)]
      (.Delete file))
    (doseq [file (.EnumerateDirectories di)]
      (.Delete file true))))

(defn restore-from-config []
  (clean internal-packages-folder)
  (if (Directory/Exists package-lock-folder)
    (Directory/Delete package-lock-folder true))
  (let [user-deps (:dependencies (config/config))
        lein-deps (mapcat #(get-in % [::lein/defproject ::lein/dependencies])
                          (lein/all-project-data))
        dependencies (->> (concat user-deps lein-deps))]
    (restore dependencies (fn [] (install internal-packages-folder)))))

(defn clean-libraries []
  (clean internal-packages-folder))

(defn clean-cache []
  (clean external-packages-folder))

(comment
 (clean internal-packages-folder)
 (restore '[[Vector2D "1.0.0"]
            [Nasser.Flumbo "1.0.0"]])
 (install internal-packages-folder)
 )
