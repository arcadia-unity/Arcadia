(ns ^{:doc "C# Introspection functionality, useful for hacking the Unity API."}
  arcadia.introspection
  (:refer-clojure :exclude [methods])
  (:require [clojure.pprint :as pprint])
  (:import [System.Reflection
            BindingFlags MethodInfo PropertyInfo FieldInfo]))

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
  "Returns a sequence of `MethodInfo`s sorted by name of all the methods in `t`.
  If a string or regular expression is provided as `pattern` it is used to narrow
  the results to methods with names that contain or match `pattern`."
  ([^Type t]
   (letfn [(nf [^MethodInfo m] (.Name m))]
     (sort-by nf
       (.GetMethods t inclusive-binding-flag))))
  ([^Type t, pattern]
   (letfn [(nf [^MethodInfo m] (.Name m))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetMethods t inclusive-binding-flag))
        pattern)))))

(defn properties
  "Returns a sequence of `PropertyInfo`s sorted by name of all the properties in `t`.
  If a string or regular expression is provided as `pattern` it is used to narrow
  the results to methods with names that contain or match `pattern`."
  ([^Type t]
   (letfn [(nf [^PropertyInfo p] (.Name p))]
     (sort-by nf
       (.GetProperties t inclusive-binding-flag))))
  ([^Type t, pattern]
   (letfn [(nf [^PropertyInfo p] (.Name p))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetProperties t inclusive-binding-flag))
        pattern)))))

(defn fields
  "Returns a sequence of `FieldInfo`s sorted by name of all the fields in `t`.
  If a string or regular expression is provided as `pattern` it is used to narrow
  the results to methods with names that contain or match `pattern`."
  ([^Type t]
   (letfn [(nf [^FieldInfo f] (.Name f))]
     (sort-by nf
       (.GetFields t inclusive-binding-flag))))
  ([^Type t, pattern]
   (letfn [(nf [^FieldInfo f] (.Name f))]
     (sort-by nf
       ((fuzzy-finder-fn
          nf
          #(.GetFields t inclusive-binding-flag))
        pattern)))))

(defn constructors
  "Returns an array of all the constructors in `t`"
  [^Type t]
  (seq (.GetConstructors t)))

(defn members
  "Returns a sequence of all the methods, properties, and fields in `t`"
  ([^Type t]
   (sort-by
     #(.Name %)
     (concat
       (fields t)
       (properties t)
       (methods t))))
  ([^Type t, sr]
   (sort-by
     #(.Name %)
     (concat
       (fields t sr)
       (properties t sr)
       (methods t sr)))))

;; TODO: printing conveniences, aproprint, version of apropos returning richer data, etc

;; ============================================================
;;

(defn methods-report
  ([type] (methods-report type nil))
  ([type pat]
   (let [cmpr (comparator #(< (count (.GetParameters %1))
                              (count (.GetParameters %2))))]
     (->> (if pat
            (methods type pat)
            (methods type))
          (sort (fn [a b]
                  (let [cmp (compare (.Name a) (.Name b))]
                    (if-not (zero? cmp)
                      cmp
                      (cmpr a b)))))
          (map (fn [meth]
                 {:name (.Name meth)
                  :parameters (vec
                                (for [param (.GetParameters meth)]
                                  {:name (.Name param)
                                   :type (.ParameterType param)}))
                  :return-type (.. meth ReturnParameter ParameterType)}))))))
