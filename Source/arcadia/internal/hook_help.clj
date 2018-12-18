(ns arcadia.internal.hook-help
  (:require arcadia.data
            [clojure.spec.alpha :as s]
            [arcadia.internal.namespace :as ans])
  (:import [UnityEngine Debug]
           clojure.lang.IFn
           ArcadiaBehaviour
           ArcadiaState
           ArcadiaBehaviour+IFnInfo
           [Arcadia JumpMap JumpMap+PartialArrayMapView JumpMap+KeyVal]))

(defn get-state-atom [^ArcadiaBehaviour ab]
  (.state ab))

(defn deserialized-var-form? [v]
  (and (seq? v)
       (= 2 (count v))
       (= (first v) 'var)
       (symbol? (second v))))

(s/def ::fn (s/or :ifn ifn?
                  :var-form deserialized-var-form?))

(s/def ::key any?)

;; ------------------------------------------------------------
;; pamv

;; (s/def ::pamv #(instance? JumpMap+PartialArrayMapView %))

;; (s/def ::pamv-data
;;   (s/coll-of ::key))

;; (s/fdef pamv-data
;;   :args (s/cat :pamv #(instance? JumpMap+PartialArrayMapView %))
;;   :ret ::pamv-data)

;; (defn pamv-data [^JumpMap+PartialArrayMapView pamv]
;;   (vec (.keys pamv)))

;; (s/fdef deserialize-pamv
;;   :args (s/cat :pamv-data ::pamv-data,
;;                :jm #(instance? JumpMap %))
;;   :ret #(instance? JumpMap+PartialArrayMapView %))

;; (defn deserialize-pamv [pamv-data, ^JumpMap jm]
;;   (.pamv jm (into-array System.Object pamv-data)))

;; ------------------------------------------------------------
;; ifn-info

;; (s/def ::ifn-info-data
;;   (s/keys
;;     :req [::key ::fn ::pamv-data]))

;; (s/fdef ifn-info-data
;;   :args (s/cat :inf #(instance? ArcadiaBehaviour+IFnInfo %))
;;   :ret ::ifn-info-data)

;; (defn ifn-info-data [^ArcadiaBehaviour+IFnInfo inf]
;;   {::key (.key inf)
;;    ::fn (.fn inf)
;;    ::pamv-data (if (.pamv inf)
;;                  (pamv-data (.pamv inf))
;;                  (vec (.fastKeys inf)))})

;; (s/fdef deserialize-ifn-info
;;   :args (s/cat :ifn-info-data ::ifn-info-data
;;                :jm (s/nilable #(instance? JumpMap %)))
;;   :ret #(instance? ArcadiaBehaviour+IFnInfo %))

;; (defn deserialize-ifn-info ^ArcadiaBehaviour+IFnInfo [{:keys [::key ::fn ::pamv-data]},
;;                                                       ^JumpMap jm]
;;   ;; (log "here we are in deserialize-ifn-info. pamv-data:\n"
;;   ;;   (pr-str pamv-data))
;;   (if jm
;;     (do
;;       ;; (log "branch 1")
;;       (ArcadiaBehaviour+IFnInfo. key fn
;;         (deserialize-pamv pamv-data jm)))
;;     (do
;;       ;; (log "branch 2")
;;       (ArcadiaBehaviour+IFnInfo/LarvalIFnInfo key fn
;;         (into-array System.Object pamv-data)))))

;; ------------------------------------------------------------
;; arcadia behaviour

;; (s/def ::behaviour-data
;;   (s/coll-of ::ifn-info-data))

;; (defn serialize-behaviour [^ArcadiaBehaviour state-component]
;;   (pr-str (mapv ifn-info-data (.ifnInfos state-component))))

;; (s/fdef require-var-namespace
;;   :args (s/cat :v var?))

;; (defn require-var-namespace [^clojure.lang.Var v]
;;   (let [^clojure.lang.Var v v
;;         ns-sym (.. v Namespace Name)]
;;     (try
;;       (ans/quickquire ns-sym)
;;       (catch Exception e
;;         (let [msg (str "Failed to require namespace for " (pr-str v) "; threw:\n"
;;                        e
;;                        "\n\n")]
;;           (UnityEngine.Debug/LogError msg))))))

;; (s/fdef require-var-namespaces
;;   :args (s/cat :behaviour #(instance? ArcadiaBehaviour %)))

;; (defn require-var-namespaces [^ArcadiaBehaviour behaviour]
;;   (let [infs (.ifnInfos behaviour)]
;;     (doseq [^ArcadiaBehaviour+IFnInfo inf infs]
;;       (let [k (.key inf)
;;             v (.fn inf)]
;;         (when (var? k) (require-var-namespace k))
;;         (when (var? v) (require-var-namespace v))))))

;; (defn var-form->var [v]
;;   (if (deserialized-var-form? v)
;;     (let [[_ ^clojure.lang.Symbol sym] v
;;           ns-sym (symbol (.. sym Namespace))]
;;       (clojure.lang.RT/var (name ns-sym) (name sym)))
;;     v))

;; (s/fdef realize-vars
;;   :args (s/cat :ifn-info-data ::ifn-info-data)
;;   :ret ::ifn-info-data)

;; (defn realize-vars [ifn-info-data]
;;   (-> ifn-info-data
;;       (update ::key var-form->var)
;;       (update ::fn var-form->var)))

;; (s/fdef hook-state-deserialize
;;   :args (s/cat :behaviour #(instance? ArcadiaBehaviour %)))

;; (defn hook-state-deserialize [^ArcadiaBehaviour behaviour]
;;   (let [raw (read-string (.edn behaviour))]
;;     (let []
;;       (set! (.ifnInfos behaviour)
;;         (->> raw
;;              (map realize-vars)
;;              (map #(deserialize-ifn-info % nil)) ;; note the nil
;;              (into-array ArcadiaBehaviour+IFnInfo)))
;;       behaviour)
;;     ;; (with-cmpt behaviour [state ArcadiaState]
;;     ;;   (let [jm (.state state)]
;;     ;;     (set! (.ifnInfos behaviour)
;;     ;;       (->> raw
;;     ;;            (map realize-vars)
;;     ;;            (map #(deserialize-ifn-info % nil)) ;; note the nil
;;     ;;            (into-array ArcadiaBehaviour+IFnInfo)))
;;     ;;     behaviour))
;;     ;; (with-cmpt behaviour [state ArcadiaState]
;;     ;;   (let [jm (.state state)]
;;     ;;     (set! (.ifnInfos behaviour)
;;     ;;       (->> raw
;;     ;;            (map realize-vars)
;;     ;;            (map #(deserialize-ifn-info % jm))
;;     ;;            (into-array ArcadiaBehaviour+IFnInfo)))
;;     ;;     behaviour))
;;     ))
