(ns arcadia.introspection
  (:refer-clojure :exclude [methods])
  (:require [clojure.pprint :as pprint]
            [clojure.test :as test]
            [arcadia.core :as ac])
  (:import [System.Reflection
            MonoMethod MonoProperty MonoField]))

(def inclusive-binding-flag
  (enum-or
    BindingFlags/Static
    BindingFlags/Instance
    BindingFlags/Public
    BindingFlags/NonPublic))

;; private until I'm sure this is the right namespace - tsg
(defn- fuzzy-finder-fn [name-fn resource-fn]
  (fn [string-or-regex]
    (let [f (if (instance? System.Text.RegularExpressions.Regex string-or-regex)
              #(re-find string-or-regex (name-fn %))
              (let [^String s string-or-regex]
                #(let [^String n (name-fn %)]
                   (.Contains n s))))]
      (filter f (resource-fn)))))

;; for example:
;; (def fuzzy-materials
;;   (fuzzy-finder-fn
;;     (fn [^Material m]
;;       (.name m))
;;     #(Resources/FindObjectsOfTypeAll Material)))

(defn methods
  ([^Type t]
   (letfn [(nf [^MonoMethod m] (.Name m))]
     (sort-by nf
       (.GetMethods t inclusive-binding-flag))))
  ([^Type t, sr]
   (letfn [(nf [^MonoMethod m] (.Name m))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetMethods t inclusive-binding-flag))
        sr)))))

(defn properties
  ([^Type t]
   (letfn [(nf [^MonoProperty p] (.Name p))]
     (sort-by nf
       (.GetProperties t inclusive-binding-flag))))
  ([^Type t, sr]
   (letfn [(nf [^MonoProperty p] (.Name p))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetProperties t inclusive-binding-flag))
        sr)))))

(defn fields
  ([^Type t]
   (letfn [(nf [^MonoField f] (.Name f))]
     (sort-by nf
       (.GetFields t inclusive-binding-flag))))
  ([^Type t, sr]
   (letfn [(nf [^MonoField f] (.Name f))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetFields t inclusive-binding-flag))
        sr)))))

(defn constructors [^Type t]
  (.GetConstructors t))

(defn members
  ([^Type t]
   (sort-by
     #(ac/condcast-> % x
        MonoMethod (.Name x)
        MonoProperty (.Name x)
        MonoField (.Name x))
     (concat
       (fields t)
       (properties t)
       (methods t))))
  ([^Type t, sr]
   (sort-by
     #(ac/condcast-> % x
        MonoMethod (.Name x)
        MonoProperty (.Name x)
        MonoField (.Name x))
     (concat
       (fields t sr)
       (properties t sr)
       (methods t sr)))))

;; TODO: printing conveniences, aproprint, version of apropos returning richer data, etc
