(ns clj-unity.reflect-utils
  (require
    ;;[clj-unity.seq-utils :as su]
    [clojure.reflect :as reflect]
    [clojure.walk :as walk])
  (import [clojure.reflect Constructor Method Field Property
           ClrReflector]))

(defn reflection-transform [x]
  (if-let [t (#{Constructor Method Field Property} (type x))]
    (into {:type t} (seq x))
    x))

(def unity-reflector
  (let [rflr (clojure.reflect/ClrReflector. nil)]
    (reify clojure.reflect/Reflector
      (clojure.reflect/do-reflect [_ typeref]
        (when-not (#{UnityEngine.GameObject
                     UnityEngine.Object} typeref)
          (clojure.reflect/do-reflect rflr typeref))))))

(defn reflect [x & opts]
  (walk/prewalk
    reflection-transform
    (apply reflect/reflect x
      :reflector unity-reflector
      opts)))
