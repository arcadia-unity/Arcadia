(ns unity.reflect-utils
  (:refer-clojure :exclude [methods])
  (:require
   ;;[unity.seq-utils :as su]
   [clojure.reflect :as reflect]
   [clojure.walk :as walk])
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
    (into {:type t} (seq x))
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

(def constructors (member-getter-fn Constructor))
(def methods      (member-getter-fn Method))
(def fields       (member-getter-fn Field))
(def properties   (member-getter-fn Property))
