(ns arcadia.internal.hook-help
  (:import ArcadiaBehaviour
           ArcadiaBehaviour+StateContainer))

(defn get-state-atom [^ArcadiaBehaviour ab]
  (.state ab))

(defn build-hook-state
  ([]
   (build-hook-state {}))
  ([key-fns]
   (ArcadiaBehaviour+StateContainer. key-fns (into-array (vals key-fns)))))

(defn update-hook-state [^ArcadiaBehaviour+StateContainer hs, f & args]
  (build-hook-state (apply f (.indexes hs) args)))

(defn add-fn [state-component key f]
  (swap! (get-state-atom state-component)
    update-hook-state assoc key f))

(defn remove-fn [state-component key]
  (swap! (get-state-atom state-component)
    update-hook-state dissoc key))

(defn remove-all-fns [state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state {} (into-array nil))))

(defn hook-state-serialized-edn [^ArcadiaBehaviour state-component]
  (pr-str (.indexes state-component)))

(defn deserialized-var-form? [v]
  (and (seq? v)
       (= 2 (count v))
       (= (first v) 'var)
       (symbol? (second v))))

(defn deserialize-step [bldg k v]
  (if (deserialized-var-form? v)
    (let [[_ ^clojure.lang.Symbol sym] v
          ns-sym (symbol (.. sym Namespace))]
      (require ns-sym)
      (try
        (assoc bldg k
          (.FindInternedVar ^clojure.lang.Namespace (find-ns ns-sym) (symbol (name sym))))
        (catch Exception e
          (UnityEngine.Debug/LogError
            (Exception.
              (str "Failed to require namespace while attaching #'"
                   (name ns-sym) "/" (name sym))
              e))
          (assoc bldg k (clojure.lang.RT/var (name ns-sym) (name sym))))))
    bldg))

(defn hook-state-deserialize [^ArcadiaBehaviour state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state
      (reduce-kv
        deserialize-step
        {}
        (read-string (.edn state-component))))))
