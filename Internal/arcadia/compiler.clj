(ns arcadia.compiler
  (:require [arcadia.config :refer [config]]
            clojure.string)
  (:import [System IO.Path Environment]
           [UnityEngine Debug]
           [UnityEditor AssetDatabase ImportAssetOptions PlayerSettings ApiCompatibilityLevel]))

(defn initialize-unity []
  (set! PlayerSettings/apiCompatibilityLevel ApiCompatibilityLevel/NET_2_0)
  (set! PlayerSettings/runInBackground true))

(defn setup-load-paths []
  (initialize-unity)
  (arcadia.config/update-from-default-location!)
  (let [config @config]
    (->> (cons (get-in config [:compiler :assemblies])
               (get-in config [:compiler :load-path]))
         (map #(Path/Combine Environment/CurrentDirectory %))
         (clojure.string/join ":")
         (Environment/SetEnvironmentVariable "CLOJURE_LOAD_PATH"))))

(defn process-assets [imported]
  (let [config @config
        {:keys [load-path assemblies warn-on-reflection unchecked-math]}
          (config :compiler)]
    (doseq [asset imported
            :when (re-find #"\.clj$" asset)]
      (if-let [root (->> load-path (filter #(.Contains asset %)) first)]
        (let [namespace (-> asset 
                        (clojure.string/replace root "")
                        (clojure.string/replace #".clj$" "")
                        (clojure.string/replace #"^\/" "")
                        (clojure.string/replace "/" ".")
                        (clojure.string/replace "_" "-"))]
          (try
            (binding [*compile-path* assemblies
                      *warn-on-reflection* warn-on-reflection
                      *unchecked-math* unchecked-math
                      *compiler-options* nil]
              (compile (symbol namespace))
              (AssetDatabase/Refresh ImportAssetOptions/ForceUpdate))
            (catch Exception e
              (Debug/LogException e))))))))