(ns arcadia.reflect
  (:refer-clojure :exclude [methods])
  (:require
   [clojure.reflect :as reflect]
   [clojure.walk :as walk]
   [clojure.pprint])
  (:import [clojure.reflect Constructor Method Field Property]))

;;; This library is motivated by a preference for persistent maps over
;;; records. It rewrites the output of clojure.core/reflect such that
;;; instances of the Constructor, Method, Field, and Property record
;;; types become maps. It also exposes some conveniences for common
;;; reflective queries. For the original record types, see
;;; clojure.core/reflect.

(defn reflection-transform [x]
  (if-let [t ({Constructor :constructor
               Method :method ; hi this does methods
               Field :field   ; but this does fiedls!
               Property :property} (type x))]
    (into {:member-type t} (seq x))
    x))

(defn reflect [x & opts]
  (walk/prewalk
    reflection-transform
    (apply reflect/reflect x opts)))

(defn member-getter-fn [member-type]
  (fn [x & opts]
    (->> (apply reflect/reflect x opts)
      :members
      (filter #(instance? member-type %))
      (sort-by :name)
      (map reflection-transform))))

(defmacro def-member-getter-fn [name member-type]
  `(defn ~name [x# & opts#]
     (->> (apply reflect/reflect x# opts#)
       :members
       (filter #(instance? ~member-type %))
       (sort-by :name)
       (map reflection-transform))))
 
(def-member-getter-fn constructors clojure.reflect.Constructor)
(def-member-getter-fn methods clojure.reflect.Method)
(def-member-getter-fn fields clojure.reflect.Field)
(def-member-getter-fn properties clojure.reflect.Property)
