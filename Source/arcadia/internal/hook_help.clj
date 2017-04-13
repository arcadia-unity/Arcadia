(ns arcadia.internal.hook-help
  (:require arcadia.literals)
  (:import [UnityEngine Debug]
           ArcadiaBehaviour
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

(defn add-fns [state-component fnmap]
  (swap! (get-state-atom state-component)
    update-hook-state merge fnmap))

(defn remove-fn [state-component key]
  (swap! (get-state-atom state-component)
    update-hook-state dissoc key))

(defn remove-all-fns [state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state {})))

(defn hook-state-serialized-edn [^ArcadiaBehaviour state-component]
  (pr-str (.indexes state-component)))

(defn deserialized-var-form? [v]
  (and (seq? v)
       (= 2 (count v))
       (= (first v) 'var)
       (symbol? (second v))))

(defn require-var-namespaces [^ArcadiaBehaviour state-component]
  (letfn [(f [_ _ v]
            (when (var? v)
              (let [^clojure.lang.Var v v
                    ns-sym (.. v Namespace Name)]
                (try
                  (require ns-sym)
                  (catch Exception e
                    (UnityEngine.Debug/LogError
                      (str "Failed to require namespace for " (pr-str v))
                      e)
                    (throw e))))))]
    (reduce-kv f nil (.indexes state-component))))

(defn deserialize-step [bldg k v]
  (if (deserialized-var-form? v)
    (let [[_ ^clojure.lang.Symbol sym] v
          ns-sym (symbol (.. sym Namespace))]
      (assoc bldg k (clojure.lang.RT/var (name ns-sym) (name sym))))
    bldg))

(defn hook-state-deserialize [^ArcadiaBehaviour state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state
      (reduce-kv
        deserialize-step
        {}
        (read-string (.edn state-component))))))
