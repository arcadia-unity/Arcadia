(ns arcadia.internal.hook-help
  (:use arcadia.core)
  (:require arcadia.literals
            [clojure.spec :as s]
            [arcadia.internal.namespace :as ans])
  (:import [UnityEngine Debug]
           ArcadiaBehaviour
           ArcadiaState
           ;; ArcadiaBehaviour+StateContainer
           [Arcadia JumpMap JumpMap+PartialArrayMapView]))

(defn get-state-atom [^ArcadiaBehaviour ab]
  (.state ab))

(s/def ::behaviour #(instance? ArcadiaBehaviour %))

(s/def ::fn ifn?)

(s/def ::key any?)

(s/def ::pamv #(instance? JumpMap+PartialArrayMapView %))

(s/def ::key-fn
  (s/keys
    :req [::fn ::key]
    :opt [::pamv ::fast-keys]))

(s/def ::key-fns (s/coll-of ::key-fn))

(defn update-hook-state [& args])

(defn add-fn
  ([state-component key f]
   (swap! (get-state-atom state-component)
     update-hook-state state-component assoc key f))
  ([state-component key f fast-keys]
   ;; TODO: HERE IS WHERE WE NEED TO PICK UP TOMORROW
   ;; (swap! (get-state-atom state-component)
   ;;   update-hook-state )
   ))

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
                  (ans/quickquire ns-sym)
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
