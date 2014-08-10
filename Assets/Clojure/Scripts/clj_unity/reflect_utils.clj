(ns clj-unity.reflect-utils
  (:refer-clojure :exclude [methods])
  (:require
   ;;[clj-unity.seq-utils :as su]
   [clojure.reflect :as reflect]
   [clojure.walk :as walk])
  (:import [clojure.reflect Constructor Method Field Property]))

(defn reflection-transform [x]
  (if-let [t ({Constructor :constructor
               Method :method
               Field :field
               Property :property} (type x))]
    (into {:type t} (seq x))
    x))

(defn reflect [x & opts]
  (walk/prewalk
    reflection-transform
    (apply reflect/reflect x
      opts)))

(defn constructors [x & opts]
  (->> (apply reflect x opts)
    :members
    (filter #(= :constructor (:type %)))
    (sort-by :name)))

(defn methods [x & opts]
  (->> (apply reflect x opts)
    :members
    (filter #(= :method (:type %)))
    (sort-by :name)))

(defn fields [x & opts]
  (->> (apply reflect x opts)
    :members
    (filter #(= :field (:type %)))
    (sort-by :name)))

(defn properties [x & opts]
  (->> (apply reflect x opts)
    :members
    (filter #(= :property (:type %)))
    (sort-by :name)))


