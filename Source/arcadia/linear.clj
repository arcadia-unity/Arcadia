(ns arcadia.linear
  (:use arcadia.core)
  (:require [arcadia.internal.meval :as mvl]
            [clojure.zip :as zip]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.zip-utils :as zu]
            [arcadia.internal.macro :as im])
  (:import [UnityEngine Vector2 Vector3 Vector4 Quaternion]))

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
  (cond
    (next args) (reduce #(list op %1 %2) args)
    (first args) (list op (first args))
    :else (list op)))

;; nestl needs to be public for inline thing to work, so should be in
;; a different namespace
(defmacro ^:private def-vop-lower [name opts]
  (mu/checked-keys [[op] opts]
    (let [{:keys [doc return-type param-type unary-op unary-expr nullary-expr]} opts
          return-type (:or return-type param-type)
          args (map #(mtag % param-type)
                 (im/classy-args))
          f (fn [op n]
              (list (mtag (vec (take n args)) return-type)
                (nestl op (take n args))))
          optspec {:inline-arities (if unary-op
                                     #(< 0 %)
                                     #(< 1 %))
                   :inline (remove nil?
                             ['fn
                              (when nullary-expr
                                (list [] nullary-expr))
                              (cond
                                unary-expr (list [(first args)] unary-expr)
                                unary-op `([~(first args)]
                                           (~unary-op ~(first args))))
                              `([x# & xs#]
                                (nestl (quote ~op)
                                  (cons x# xs#)))])}
          body (remove nil?
                 (concat
                   [(when nullary-expr
                      (list [] nullary-expr))
                    (cond
                      unary-expr (list [(first args)] unary-expr)
                      unary-op `([~(first args)]
                                 (~unary-op ~(first args))))]
                   (for [n (range 2 20)]
                     (f op n))
                   [(list (mtag (conj (vec (take 20 args)) '& 'more) return-type)
                      (list `reduce name
                        (nestl op (take 20 args))
                        'more))]))]
      `(defn ~name ~@(when doc [doc])
         ~optspec
         ~@body))))

(defmacro ^:private def-vop-higher [name opts]
  (mu/checked-keys [[op] opts]
    (let [{:keys [doc unary-op]} opts
          asym (gensym "a")
          args (im/classy-args)
          nfnform `(fn [v# rargs#]
                     (list* (symbol (str v# '~op)) '~asym rargs#))
          nfn (eval nfnform)
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
                   (for [n (range 2 21)]
                     (f op n))
                   [(list (conj (vec (take 20 args)) '& 'more)
                      (list `reduce name
                        (list* (symbol (str name op)) (take 20 args))
                        'more))]))]
      `(defn ~name ~@(when doc [doc])
         ~optspec
         ~@body))))

;; ============================================================
;; constructors

(defmacro ^:private def-v-ctr
  ([name vtype arity]
   `(def-v-ctr ~name ~vtype ~arity ~(symbol (str vtype "/zero"))))
  ([name vtype arity zero-expr]
   (let [args (mtag (vec (take arity (im/classy-args))) vtype)
         zargs (mtag [] vtype)
         frm0 zero-expr]
     `(defn ~name 
        {:inline-arities #{0 ~arity}
         :inline (fn
                   (~zargs '~frm0)
                   (~args `(new ~~vtype ~~@args)))}
        (~zargs ~frm0)
        (~args (new ~vtype ~@args))))))

(def-v-ctr v2 UnityEngine.Vector2 2)

(def-v-ctr v3 UnityEngine.Vector3 3)

(def-v-ctr v4 UnityEngine.Vector4 4)

(def-v-ctr qt UnityEngine.Quaternion 4 UnityEngine.Quaternion/identity)

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

(def-vop-lower v2+
  {:op UnityEngine.Vector2/op_Addition
   :return-type UnityEngine.Vector2/op_Addition
   :nullary-expr UnityEngine.Vector2/zero
   :unary-expr a})

(def-vop-lower v3+
  {:op UnityEngine.Vector3/op_Addition
   :return-type UnityEngine.Vector3/op_Addition})

(def-vop-lower v4+ 
  {:op UnityEngine.Vector4/op_Addition
   :return-type UnityEngine.Vector4/op_Addition})

(def-vop-higher v+
  {:op +})

;; ============================================================
;; -

(def-vop-lower v2-
  {:op UnityEngine.Vector2/op_Subtraction
   :return-type UnityEngine.Vector2/op_Subtraction})

(def-vop-lower v3-
  {:op UnityEngine.Vector3/op_Subtraction
   :return-type UnityEngine.Vector3/op_Subtraction})

(def-vop-lower v4- 
  {:op UnityEngine.Vector4/op_Subtraction
   :return-type UnityEngine.Vector4/op_Subtraction})

(def-vop-higher v-
  {:op -})

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
;; q*
;; and then there's this
;; inline etc this stuff when time allows

(defn qq* ^Quaternion [^Quaternion a ^Quaternion b]
  (Quaternion/op_Multiply a b))

(defn qv* ^Vector3 [^Quaternion a ^Vector3 b]
  (Quaternion/op_Multiply a b))

(defn q* [^Quaternion a b]
  (condcast-> b b
    UnityEngine.Vector3 (Quaternion/op_Multiply a b)
    UnityEngine.Quaternion (Quaternion/op_Multiply a b)))

;; (mvl/defn-meval q*
;;   {:inline-arities #{}
;;    :inline}
;;   (let [args (map #(mtag % 'UnityEngine.Quaternion) (im/classy-args))
;;         argf (fn [n] (mtag (vec (take n args)) 'UnityEngine.Quaternion))]
;;     (concat
;;       [(list [(first args)] (first args))]
;;      #_ (for [arity (range 2 20)
;;             :let [args2 (argf arity)]]
;;         (list args2 (nestl 'q* args2)))
;;      #_ [(let [args2 (argf 20)]
;;          (list (conj args2) `reduce 'q* (nestl 'q* args2)))])
;;     ))

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
