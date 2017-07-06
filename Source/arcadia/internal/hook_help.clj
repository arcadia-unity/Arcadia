(ns arcadia.internal.hook-help
  (:require arcadia.literals
            [clojure.spec :as s])
  (:import [UnityEngine Debug]
           ArcadiaBehaviour
           ArcadiaState
           ArcadiaBehaviour+StateContainer))

(defn get-state-atom [^ArcadiaBehaviour ab]
  (.state ab))

(s/fdef build-hook-state
  :args (s/cat
          :arcs #(instance? ArcadiaBehaviour %)
          :key-fns (s/? map?))
  :ret #(instance? ArcadiaBehaviour+StateContainer %))

(def bhs-log (atom ::initial))

(defn build-hook-state
  ([^ArcadiaBehaviour arcb]
   (build-hook-state arcb {}))
  ([^ArcadiaBehaviour arcb, key-fns]
   (reset! bhs-log {:arcb arcb, :key-fns key-fns})
   (when (nil? (.arcadiaState arcb))
     (set! (.arcadiaState arcb)
       (.GetComponent arcb ArcadiaState)))
   (ArcadiaBehaviour+StateContainer/BuildStateContainer
     key-fns 
     (into-array (keys key-fns))
     (into-array (vals key-fns))
     (.arcadiaState arcb))))

(s/fdef update-hook-state
  :args (s/cat
          :hs #(instance? ArcadiaBehaviour+StateContainer %)
          :arcs #(instance? ArcadiaBehaviour %)
          :f ifn?
          :args (s/* any?))
  :ret #(instance? ArcadiaBehaviour+StateContainer %))

(defn update-hook-state [^ArcadiaBehaviour+StateContainer hs, ^ArcadiaBehaviour arcs, f & args]
  (build-hook-state arcs (apply f (.indexes hs) args)))

(defn add-fn [state-component key f]
  (swap! (get-state-atom state-component)
    update-hook-state state-component assoc key f))

(defn add-fns [state-component fnmap]
  (swap! (get-state-atom state-component)
    update-hook-state state-component merge fnmap))

(defn remove-fn [state-component key]
  (swap! (get-state-atom state-component)
    update-hook-state state-component dissoc key))

(defn remove-all-fns [state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state state-component {})))

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
                    (let [msg (str "Failed to require namespace for " (pr-str v) "; threw:\n"
                                   e
                                   "\n\n")]
                      (if (instance? UnityEngine.Object state-component)
                        (UnityEngine.Debug/LogError msg state-component)
                        (UnityEngine.Debug/LogError msg))))))))]
    (reduce-kv f nil (.indexes state-component))))

(defn var-form->var [v]
  (let [[_ ^clojure.lang.Symbol sym] v
        ns-sym (symbol (.. sym Namespace))]
    (clojure.lang.RT/var (name ns-sym) (name sym))))

(defn deserialize-step [bldg k v]
  (let [k (if (deserialized-var-form? k)
            (var-form->var k)
            k)
        v (if (deserialized-var-form? v)
            (var-form->var v)
            v)]
    (assoc bldg k v)))

(defn hook-state-deserialize [^ArcadiaBehaviour state-component]
  (reset! (get-state-atom state-component)
    (build-hook-state state-component
      (reduce-kv
        deserialize-step
        {}
        (read-string (.edn state-component))))))
