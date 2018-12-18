(ns arcadia.internal.config
  (:require [clojure.edn :as edn]
            [arcadia.internal.state :as state]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs])
  (:import
    [System DateTime]
    [System.IO FileSystemInfo File Path]
    [UnityEngine Debug]
    [System.Text.RegularExpressions Regex]))

(def default-config-file-path (Path/Combine "Assets" "Arcadia" "configuration.edn"))
(def user-config-file-path (Path/Combine "Assets" "configuration.edn"))

(defn config []
  (@state/state ::config))

(defn default-config
  "Built in Arcadia default configuration file. Never changes."
  [] (if (File/Exists default-config-file-path)
       (edn/read-string (slurp default-config-file-path
                               :encoding "utf8"))
       (throw (Exception. (str "Default Arcadia configuration file missing. "
                               default-config-file-path
                               " does not exist")))))

(defn user-config-file
  "Path to the user defined configuration file"
  [] user-config-file-path)

(defn user-config
  "User supplied configuration file"
  [] (if (File/Exists user-config-file-path)
       (edn/read-string (slurp user-config-file-path
                               :encoding "utf8"))
       {}))

(defn config-file? [p]
  (boolean
    (when-let [fsi (fs/info p)]
      (let [nm (.FullName (fs/info p))]
        (or
          (= nm (fs/path default-config-file-path))
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

(aw/add-listener ::fw/create-modify-delete-file ::config-listener
  (str Path/DirectorySeparatorChar "configuration.edn")
  #'config-listener)

;; ------------------------------------------------------------
;; Make the asset watcher itself reactive
;; I guess we have to do this here

(defn update-reactive [{:keys [reactive]}]
  (if reactive
    (aw/start-asset-watcher)
    (aw/stop-asset-watcher)))

(state/add-listener ::on-update ::update-reactive #'update-reactive)
