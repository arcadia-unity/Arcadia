(ns arcadia.linear
  (:use arcadia.core)
  (:require [arcadia.internal.meval :as mvl]
            [clojure.zip :as zip]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.zip-utils :as zu]
            [arcadia.internal.macro :as im]
            [clojure.test :as test])
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

;; Should abstract these crazy macros into some other namespace for optimized variadic inlining DSL stuff

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
                                           (list (quote ~unary-op)
                                             ~(first args))))
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
          nfnform `(fn ~'nfn [v# rargs#]
                     (list* (symbol (str v# '~op)) '~asym rargs#))
          nfn (eval nfnform)
          f (fn f [op n]
              (let [[a :as args2] (take n args)]
                (list (vec args2)
                  `(condcast-> ~a ~asym
                     UnityEngine.Vector3 ~(nfn `v3 args2)
                     UnityEngine.Vector2 ~(nfn `v2 args2)
                     UnityEngine.Vector4 ~(nfn `v4 args2)))))
          optspec {:inline-arities (if unary-op
                                     #(< 0 %)
                                     #(< 1 %))
                   :inline `(fn ~'checked-keys-inliner [a# & args2#]
                              (let [nfn# ~nfnform]
                                `(condcast-> ~a# ~'~asym
                                   UnityEngine.Vector3 ~(nfn# `v3 args2#)
                                   UnityEngine.Vector2 ~(nfn# `v2 args2#)
                                   UnityEngine.Vector4 ~(nfn# `v4 args2#))))}
          ;{}
          body (remove nil?
                 (concat
                   [(when unary-op
                      (f unary-op 1))]
                   (for [n (range 2 20)]
                     (f op n))
                   [(list (conj (vec (take 19 args)) '& 'more)
                      (list `reduce name
                        (list* name (take 19 args))
                        'more))]))]
      `(defn ~name ~@(when doc [doc])
         ~optspec
         ~@body))))

;; ============================================================
;; constructors

(defn v2
  {:inline-arities #{0 1 2}
   :inline (fn
             ([] `UnityEngine.Vector2/one)
             ([x]
              `(let [x# ~x]
                 (UnityEngine.Vector2. x# x#)))
             ([x y]
              `(UnityEngine.Vector2. ~x ~y)))}
  (^UnityEngine.Vector2 []
    UnityEngine.Vector2/one)
  (^UnityEngine.Vector2 [x]
    (UnityEngine.Vector2. x x))
  (^UnityEngine.Vector2 [x y]
   (UnityEngine.Vector2. x y)))

(defn v3
  {:inline-arities #{0 1 3}
   :inline (fn
             ([] `UnityEngine.Vector3/one)
             ([x]
              `(let [x# ~x]
                 (UnityEngine.Vector3. x# x# x#)))
             ([x y z]
              `(UnityEngine.Vector3. ~x ~y ~z)))}
  (^UnityEngine.Vector3 []
    UnityEngine.Vector3/one)
  (^UnityEngine.Vector3 [x]
    (UnityEngine.Vector3. x x x))
  (^UnityEngine.Vector3 [x y z]
   (UnityEngine.Vector3. x y z)))

(defn v4
  {:inline-arities #{0 1 4}
   :inline (fn
             ([] `UnityEngine.Vector4/one)
             ([x]
              `(let [x# ~x]
                 (UnityEngine.Vector4. x# x# x# x#)))
             ([x y z w]
              `(UnityEngine.Vector4. ~x ~y ~z ~w)))}
  (^UnityEngine.Vector4 []
    UnityEngine.Vector4/one)
  (^UnityEngine.Vector4 [x]
    (UnityEngine.Vector4. x x x x))
  (^UnityEngine.Vector4 [x y z w]
   (UnityEngine.Vector4. x y z w)))

(defn qt
  {:inline-arities #{0 4}
   :inline (fn
             ([] `UnityEngine.Quaternion/identity)
             ([a b c d]
              `(UnityEngine.Quaternion. ~a ~b ~c ~d)))}
  (^UnityEngine.Quaternion []
    UnityEngine.Quaternion/identity)
  (^UnityEngine.Quaternion [a b c d]
   (UnityEngine.Quaternion. a b c d)))

;; ============================================================
;; +

(def-vop-lower v2+
  {:op UnityEngine.Vector2/op_Addition
   :return-type UnityEngine.Vector2
   :nullary-expr UnityEngine.Vector2/zero
   :unary-expr a})

(def-vop-lower v3+
  {:op UnityEngine.Vector3/op_Addition
   :return-type UnityEngine.Vector3
   :nullary-expr UnityEngine.Vector3/zero
   :unary-expr a})

(def-vop-lower v4+
  {:op UnityEngine.Vector4/op_Addition
   :return-type UnityEngine.Vector4
   :nullary-expr UnityEngine.Vector4/zero
   :unary-expr a})

(def-vop-higher v+
  {:op +
   :unary-op Identity ; bit klunky for now
   })

;; ============================================================
;; -

(def-vop-lower v2-
  {:op UnityEngine.Vector2/op_Subtraction
   :return-type UnityEngine.Vector2/op_Subtraction
   :unary-op UnityEngine.Vector3/op_UnaryNegation})

(def-vop-lower v3-
  {:op UnityEngine.Vector3/op_Subtraction
   :return-type UnityEngine.Vector3/op_Subtraction
   :unary-op UnityEngine.Vector3/op_UnaryNegation})

(def-vop-lower v4- 
  {:op UnityEngine.Vector4/op_Subtraction
   :return-type UnityEngine.Vector4/op_Subtraction
   :unary-op UnityEngine.Vector3/op_UnaryNegation})

(def-vop-higher v-
  {:op -})

;; undecided whether to support variadic versions of these
;; non-associative multiply and divide ops (eg force associativity, at
;; the expense of commutativity in the case of multiply). Probably should, later
;; ============================================================
;; *

;; this nonsense should be changed as soon as we get better support for primitive arguments

(definline v2* [^UnityEngine.Vector2 a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector2/op_Multiply ~a b#)))

(definline v3* [^UnityEngine.Vector3 a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector3/op_Multiply ~a b#)))

(definline v4* [^UnityEngine.Vector4 a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector4/op_Multiply ~a b#)))

;; this one requires some more thought, of course
(definline v* [a b]
  `(condcast-> ~a a#
     UnityEngine.Vector3 (v3* a# ~b)
     UnityEngine.Vector2 (v2* a# ~b)
     UnityEngine.Vector4 (v4* a# ~b)))

;; ============================================================
;; div

(definline v2div [a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector2/op_Division ~a b#)))

(definline v3div [a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector3/op_Division ~a b#)))

(definline v4div [a b]
  `(let [b# (float ~b)]
     (UnityEngine.Vector4/op_Division ~a b#)))

(definline vdiv [a b]
  `(condcast-> ~a a#
     UnityEngine.Vector3 (v3div a# ~b)
     UnityEngine.Vector2 (v2div a# ~b)
     UnityEngine.Vector4 (v4div a# ~b)))

;; ============================================================
;; Quaternions
;; and then there's this
;; inline etc this stuff when time allows

(defn qq* 
  (^Quaternion [^Quaternion a ^Quaternion b]
    (Quaternion/op_Multiply a b))
  (^Quaternion [^Quaternion a ^Quaternion b & cs]
    (reduce qq* (qq* a b) cs)))

(defn qv* ^Vector3 [^Quaternion a ^Vector3 b]
  (Quaternion/op_Multiply a b))

(defn q* [^Quaternion a b]
  (condcast-> b b
    UnityEngine.Vector3 (Quaternion/op_Multiply a b)
    UnityEngine.Quaternion (Quaternion/op_Multiply a b)))

(defn euler ^Quaternion [^Vector3 v]
  (Quaternion/Euler v))

(defn euler-angles ^Vector3 [^Quaternion q]
  (.eulerAngles q))

(defn to-angle-axis [^Quaternion q]
  (let [ang (float 0)
        axis Vector3/zero]
    (.ToAngleAxis q (by-ref ang) (by-ref axis))
    [ang axis]))

(defn qlookat ^Quaternion
  ([^Vector3 here, ^Vector3 there]
     (qlookat here there Vector3/up))
  ([^Vector3 here, ^Vector3 there, ^Vector3 up]
     (Quaternion/LookRotation (v- there here) up)))

  ;; this gives some weird SIGILL problem
;; (defn angle-axis [^Double angle, ^Vector3 axis]
;;   (Quaternion/AngleAxis angle, axis))

(defn angle-axis ^Quaternion [angle, axis]
  (Quaternion/AngleAxis angle, axis))

(defn qforward ^Vector3 [^Quaternion q]
  (q* q Vector3/forward))

(defn aa ^Quaternion [ang x y z]
  (angle-axis ang (v3 x y z)))

;; ============================================================
;; scale

(definline v2scale [a b]
  `(UnityEngine.Vector2/Scale ~a ~b))

(definline v3scale [a b]
  `(UnityEngine.Vector3/Scale ~a ~b))

(definline v4scale [a b]
  `(UnityEngine.Vector4/Scale ~a ~b))

(definline vscale [a b]
  `(condcast-> ~a a#
     Vector3 (v3scale a# ~b)
     Vector2 (v2scale a# ~b)
     Vector4 (v4scale a# ~b)))

;; ============================================================
;; more rotation

(defn point-pivot ^Vector3 [^Vector3 pt, ^Vector3 piv, ^Quaternion rot]
  (v3+ (qv* rot (v3- pt piv))
    piv))

;; TODO: all the other stuff (normalize, orthonormalize (nice!), magnitude, sqrmagnitude, lerp, etc)
;; ============================================================
;; tests

(defn- run-tests []
  (binding [test/*test-out* *out*]
    (test/run-tests)))

(test/deftest test-addition
  (test/is
    (= (v2+
         (v2 1 2)
         (v2 1 2)
         (v2 1 2))
      (v2 3.0, 6.0)))
  (test/is
    (= (v3+
         (v3 1 2 3)
         (v3 1 2 3)
         (v3 1 2 3))
      (v3 3.0, 6.0, 9.0)))
  (test/is
    (= (v4+
         (v4 1 2 3 4)
         (v4 1 2 3 4)
         (v4 1 2 3 4))
      (v4 3.0, 6.0, 9.0, 12.0))))

(test/deftest test-rotation
  (test/is
    (= (point-pivot
         (v3 0 0 1)
         (v3 0)
         (aa 90 1 0 0))
      (v3 0 0 1))
    (= (point-pivot
         (v3 0 0 1)
         (v3 10)
         (aa 90 1 0 0))
      (v3 0 0 1))))
