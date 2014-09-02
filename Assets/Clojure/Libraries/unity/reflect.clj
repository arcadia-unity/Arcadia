(ns unity.reflect
  (:refer-clojure :exclude [methods])
  (:require
   ;;[unity.seq-utils :as su]
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

(def constructors (member-getter-fn Constructor))
(def methods      (member-getter-fn Method))
(def fields       (member-getter-fn Field))
(def properties   (member-getter-fn Property))
;; hi

(comment 
  (defn member-printer-fn [member-type rows]
    (fn [x & opts]
      (->> (apply reflect/reflect x opts)
        :members
        (filter #(instance? member-type %))
        (sort-by :name)
        (map reflection-transform)
        (clojure.pprint/print-table rows))))

  (def print-constructors
    (member-printer-fn Constructor
      [:name :return-type :parameter-types]))
  (def print-methods
    (member-printer-fn Method))
  (def print-fields (member-printer-fn Field))
  (def print-properties (member-printer-fn Property))

  (defn setters [x]
    (->> x
      methods
      (filter
        (fn [mth]
          (and
            (clojure.set/subset?
              #{:public :special-name}
              (:flags mth))
            (re-matches
              #"^set_.*"
              (name (:name mth)))))))))
