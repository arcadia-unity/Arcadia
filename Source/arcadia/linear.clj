(ns arcadia.linear
  (:use arcadia.core)
  (:require [arcadia.internal.meval :as mvl]
            [clojure.zip :as zip]
            [arcadia.internal.zip-utils :as zu])
  (:import [UnityEngine Vector2 Vector3 Vector4]))

;; Much of the implementation logic here is due to current uncertainty
;; about the status of the polymorphic inline cache. We have a pretty
;; good one thanks to the DLR, but only for certain export
;; targets. Future versions of this library may switch on their export
;; target, as the presence or absence of a strong PIC would have
;; significant performance implications for this sort of thing.

;; ============================================================
;; utils

(defn- mtag [x tag]
  (if tag
    (with-meta x (assoc (meta x) :tag tag))
    x))

(defn nestl [op args]
  (if (next args) ;; check this
    (reduce #(list op %1 %2) args)
    (list op (first args))))


;; nestl needs to be public for inline thing to work, so should be in
;; a different namespace
(defmacro ^:private def-vop-lower [name opts]
  (mu/checked-keys [[op] opts]
    (let [{:keys [doc return-type param-type]
           :or {param-type return-type}} opts
           args (map #(mtag % param-type)
                  (mcu/classy-args))
           f (fn [op n]
               (list (mtag (vec (take n args)) return-type)
                 (nestl op (take n args))))
           optspec {:inline-arities (if unary-op
                                      #(< 0 %)
                                      #(< 1 %))
                    :inline `(fn [& xs#]
                               (nestl (quote ~op) xs#))}
           body (remove nil?
                  (concat
                    [(when unary-op
                       (f unary-op 1))]
                    (for [n (range 2 20)]
                      (f op n))
                    [(list (mtag (conj (vec (take n args)) '& 'more) return-type)
                       (list `reduce name
                         (nestl op (take 20 args))
                         'more))]))]
      `(defn ~name ~@(when doc [doc])
         ~@body))))

(defmacro ^:private def-vop-higher [name opts]
  (mu/checked-keys [[op] opts]
    (let [{:keys [doc]} opts
          asym (gensym "a")
          nfnform `(fn [v rargs]
                     (list* (symbol (str v op)) asym rargs))
          nfn (eval nfnform) ;; whee
          f (fn [op n]
              (let [[a & args2] (take n args)]
                (list (vec args2)
                  `(condcast-> ~a ~asym
                     UnityEngine.Vector3 ~(nfn `v3 args2)
                     UnityEngine.Vector2 ~(nfn `v2 args2)
                     UnityEngine.Vector4 ~(nfn `v4 args2)))))
          
          optspec {:inline-arities (if unary-op
                                     #(< 0 %)
                                     #(< 1 %))
                   :inline `(fn [[a# & args2#]]
                              (let [nfn# ~nfnform]
                                `(condcast-> ~a# ~~asym
                                   UnityEngine.Vector3 ~(nfn# `v3 args2#)
                                   UnityEngine.Vector2 ~(nfn# `v2 args2#)
                                   UnityEngine.Vector4 ~(nfn# `v4 args2#))))}
          body (remove nil?
                 (concat
                   [(when unary-op
                      (f unary-op 1))]
                   (for [n (range 2 20)]
                     (f op n))
                   [(list (mtag (conj (vec (take n args)) '& 'more) return-type)
                      (list `reduce name
                        (nestl op (take 20 args))
                        'more))]))]
      `(defn ~name ~@(when doc [doc])
         ~@body))))

;; ============================================================
;; div

(def-vop-lower v2div
  {:op UnityEngine.Vector2/op_Division
   :return-type UnityEngine.Vector2/op_Division})

(def-vop-lower v3div
  {:op UnityEngine.Vector3/op_Division
   :return-type UnityEngine.Vector3/op_Division})

(def-vop-lower v4div 
  {:op UnityEngine.Vector4/op_Division
   :return-type UnityEngine.Vector4/op_Division})

(def-vop-higher vdiv
  {:op -})

;; ============================================================
;; +

(definline v2+ [a b]
  `(Vector2/op_Addition ~a ~b))

(definline v3+ [a b]
  `(Vector3/op_Addition ~a ~b))

(definline v4+ [a b]
  `(Vector4/op_Addition ~a ~b))

(definline v+ [a b]
  `(condcast-> ~a a#
     Vector3 (v3+ a# ~b)
     Vector2 (v2+ a# ~b)
     Vector4 (v4+ a# ~b)))

(comment
  (definline v2blo [a b]
    `(Vector2/op_bla ~a ~b))

  (definline v3blo [a b]
    `(Vector3/op_bla ~a ~b))

  (definline v4blo [a b]
    `(Vector4/op_bla ~a ~b)))

;; ============================================================
;; -

(definline v2- [a b]
  `(Vector2/op_Subtraction ~a ~b))

(definline v3- [a b]
  `(Vector3/op_Subtraction ~a ~b))

(definline v4- [a b]
  `(Vector4/op_Subtraction ~a ~b))

(definline v- [a b]
  `(condcast-> ~a a#
     Vector3 (v3- a# ~b)
     Vector2 (v2- a# ~b)
     Vector4 (v4- a# ~b)))

;; ============================================================
;; *

(definline v2* [a b]
  `(Vector2/op_Multiply ~a ~b))

(definline v3* [a b]
  `(Vector3/op_Multiply ~a ~b))

(definline v4* [a b]
  `(Vector4/op_Multiply ~a ~b))

;; this one requires some more thought, of course
(definline v* [a b]
  `(condcast-> ~a a#
     Vector3 (v3* a# ~b)
     Vector2 (v2* a# ~b)
     Vector4 (v4* a# ~b)))

;; ============================================================
;; scale

(definline v2scale [a b]
  `(Vector2/op_Scale ~a ~b))

(definline v3scale [a b]
  `(Vector3/op_Scale ~a ~b))

(definline v4scale [a b]
  `(Vector4/op_Scale ~a ~b))

(definline vscale [a b]
  `(condcast-> ~a a#
     Vector3 (v3scale a# ~b)
     Vector2 (v2scale a# ~b)
     Vector4 (v4scale a# ~b)))
