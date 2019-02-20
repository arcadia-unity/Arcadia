(ns arcadia.internal.state-help
  (:require arcadia.data
            ;; just for deserialized-var-form?, var-form->var
            [arcadia.internal.hook-help :as hh]
            [clojure.edn :as edn])
  (:import [UnityEngine Debug]
           [Arcadia JumpMap JumpMap+KeyVal JumpMap+PartialArrayMapView]
           Arcadia.Util
           ArcadiaState
           ArcadiaBehaviour+IFnInfo
           AroundSerializeDataHook
           AroundDeserializeDataHook))

(defn jumpmap-to-map [^JumpMap jm]
  (persistent!
    (reduce (fn [m, ^JumpMap+KeyVal kv]
              (assoc! m (.key kv) (.val kv)))
      (transient {})
      (.. jm KeyVals))))

(defn inner-deserialize [data-string]
  (edn/read-string {:readers *data-readers*} data-string))

(defn around-wrapper [^GameObject obj, ifn-infos, init-f]
  (reduce
    (fn make-wrappers [wrapper-acc, ^ArcadiaBehaviour+IFnInfo finf]
      (fn wrapper [data]
        ((.fn finf) obj (.key finf) wrapper-acc data)))
    init-f
    ifn-infos))

(defn deserialize-data-string
  "Given an ArcadiaState `as`, returns its deserialized data. If an
  AroundDeserializedDataHook component is attached to the GameObject
  of `as`, will deserialize the data by threading the string through
  all registered :around-deserialize-data hook functions. Each of
  these hook functions is expected to take the arguments [obj k f s],
  where `obj` is the GameObject of `as`; `k` is the key of the current
  hook function; `f` is the _next_ function to call; and `s` is the
  data string being deserialized. Each hook function is expected to
  return the result of `(f s)`."
  [^ArcadiaState as]
  (if-let [^AroundDeserializeDataHook addh (Arcadia.Util/TrueNil
                                             (.GetComponent as AroundDeserializeDataHook))]
    (let [f (around-wrapper (.gameObject as) (.ifnInfos addh) inner-deserialize)]
      (f (.serializedData as)))
    (inner-deserialize (.serializedData as))))

(defn deserialize [^ArcadiaState as]
  (.BuildDatabaseAtom as true)
  (let [objdb (.objectDatabase as)]
    (binding [arcadia.data/*object-db* objdb]
      (try
        (let [^JumpMap jm (.state as)]
          (.Clear jm)
          (.AddAll jm
            ;; in the future, switch on (.serializedFormat as)
            ;; assuming edn for now
            (deserialize-data-string as)
            ;; (edn/read-string {:readers *data-readers*}
            ;;   (.serializedData as))
            ))
        (catch Exception e
          (Debug/Log "Exception encountered in arcadia.internal.state-help/deserialize:")
          (Debug/Log e)
          (Debug/Log "arcadia.data/*object-db*:")
          (Debug/Log arcadia.data/*object-db*)
          (throw (Exception. "wrapper" e)))))))

;; TODO: change this up for defmutable
(defn default-conversion [k v source target]
  v)

(defn initialize [^ArcadiaState as]
  (deserialize as)
  ;;(.RefreshAll as)
  )

;; used from ArcadiaState
(defn serialize [^ArcadiaState as, state-map]
  (if-let [^AroundSerializeDataHook asdh (Arcadia.Util/TrueNil
                                           (.GetComponent as AroundSerializeDataHook))]
    (let [f (around-wrapper (.gameObject as) (.ifnInfos asdh) pr-str)]
      (f state-map))
    (pr-str state-map)))
