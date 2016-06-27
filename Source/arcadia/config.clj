(ns arcadia.config
  (:require [clojure.edn :as edn])
  (:import
    [Arcadia Configuration]
    [System DateTime]
    [System.IO File]
    [UnityEngine Debug]))

(defonce configuration (atom {}))

(defn default-config 
  "Built in Arcadia default configuration file. Never changes."
  [] (if (File/Exists Configuration/defaultConfigFilePath)
       (edn/read-string (slurp Configuration/defaultConfigFilePath
                               :encoding "utf8"))
       (throw (Exception. (str "Default Arcadia configuration file missing. "
                               Configuration/defaultConfigFilePath
                               " does not exist")))))

(defn user-config-file 
  "Path to the user defined configuration file"
  [] Configuration/userConfigFilePath)

(defn user-config
  "User supplied configuration file"
  [] (if (File/Exists Configuration/userConfigFilePath)
       (edn/read-string (slurp Configuration/userConfigFilePath
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