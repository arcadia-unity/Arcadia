(ns arcadia.config
  (:require [clojure.edn :as edn])
  (:import
    [System DateTime]
    [System.IO File]
    [UnityEngine Debug]))

(defonce configuration (atom {}))

(defn default-config 
  "Built in Arcadia default configuration file. Never changes."
  [] (if (File/Exists ClojureConfiguration/defaultConfigFilePath)
       (edn/read-string (slurp ClojureConfiguration/defaultConfigFilePath
                               :encoding "utf8"))
       (throw (Exception. (str "Default Arcadia configuration file missing. "
                               ClojureConfiguration/defaultConfigFilePath
                               " does not exist")))))

(defn user-config-file 
  "Path to the user defined configuration file"
  [] ClojureConfiguration/userConfigFilePath)

(defn user-config
  "User supplied configuration file"
  [] (if (File/Exists ClojureConfiguration/userConfigFilePath)
       (edn/read-string (slurp ClojureConfiguration/userConfigFilePath
                               :encoding "utf8"))
       {}))

;; TODO (merge-with into ... ) ?
(defn merged-configs
  "Result of merger of all three configuration sources"
  [] (merge (default-config)
            (user-config)))

(defn update!
  "Update the configuration atom"
  [] (reset! configuration (merged-configs)))