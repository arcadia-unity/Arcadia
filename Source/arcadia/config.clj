(ns arcadia.config
  (:require [clojure.edn :as edn]
            [arcadia.internal.state :as state]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs])
  (:import
    [Arcadia Configuration]
    [System DateTime]
    [System.IO File]
    [UnityEngine Debug]
    [System.Text.RegularExpressions Regex]))

(defn config []
  (@state/state ::config))

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

(defn config-file? [p]
  (boolean
    (when-let [fsi (fs/info p)]
      (let [nm (.FullName (fs/info p))]
        (or
          (= nm (fs/path Configuration/defaultConfigFilePath))
          (= nm (fs/path (user-config-file))))))))

;; TODO (merge-with into ... ) ?
(defn- merged-configs
  "Result of merger of all three configuration sources"
  [] (merge
       (default-config)
       (user-config)))

(defn update!
  "Update the configuration"
  []
  (let [mc (merged-configs)]
    (Debug/Log "Updating config")
    (swap! state/state assoc ::config mc)
    (state/run-listeners ::on-update mc)))

(defn config-listener [{:keys [::fw/path]}]
  (when (config-file? path)
    (update!)))

(update!)

(aw/add-listener ::fw/create-modify-delete-file ::config-listener
  (str Path/DirectorySeparatorChar "configuration.edn")
  #'config-listener)
