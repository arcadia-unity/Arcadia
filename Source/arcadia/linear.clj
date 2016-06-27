(ns arcadia.linear
  (:refer-clojure :exclude [methods])
  (:use arcadia.core)
  (:require [arcadia.internal.map-utils :as mu]
            [arcadia.internal.macro :as im]
            [clojure.test :as test])
  (:import [UnityEngine Vector2 Vector3 Vector4 Quaternion Matrix4x4]
           [Arcadia LinearHelper]))

;; Much of the implementation logic here is due to current uncertainty
;; about the status of the polymorphic inline cache. We have a pretty
;; good one thanks to the DLR, but only for certain export
;; targets. Future versions of this library may switch on their export
;; target, as the presence or absence of a strong PIC would have
;; significant performance implications for this sort of thing.

;; ------------------------------------------------------------
;; utils

;; Should abstract these crazy macros into some other namespace for optimized variadic inlining DSL stuff

(defn- mtag [x tag]
  (if tag
    (with-meta x (assoc (meta x) :tag tag))
    x))

(defn nestl [op args]
  (cond
    (next args) (reduce #(list op %1 %2) args)
    (first args) (list op (first args))
    :else (list op)))

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

;; ------------------------------------------------------------
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

;; ------------------------------------------------------------
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

;; ------------------------------------------------------------
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

;; undecided whether to support variadic versions of these
;; non-associative multiply and divide ops (eg force associativity, at
;; the expense of commutativity in the case of multiply). Probably should, later
;; ------------------------------------------------------------
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

;; ------------------------------------------------------------
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

;; ------------------------------------------------------------
;; dist

;; ------------------------------------------------------------
;; Quaternions
;; and then there's this
;; inline etc this stuff when time allows

(defn ^:private >1? [n] (clojure.lang.Numbers/gt n 1))

(defn qq*
  {:inline (fn [& args]
             (nestl 'Quaternion/op_Multiply args))
  :inline-arities >1?}
  (^Quaternion [^Quaternion a ^Quaternion b]
    (Quaternion/op_Multiply a b))
  (^Quaternion [^Quaternion a ^Quaternion b & cs]
    (reduce qq* (qq* a b) cs)))

(definline qv* ^Vector3 [^Quaternion a ^Vector3 b]
  `(Quaternion/op_Multiply ~a ~b))

(definline q* [a b]
  `(Quaternion/op_Multiply ~a ~b))

(definline euler ^Quaternion [^Vector3 v]
  `(Quaternion/Euler ~v))

(definline euler-angles ^Vector3 [^Quaternion q]
  `(.eulerAngles ~q))

(definline to-angle-axis [^Quaternion q]
  `(let [ang# (float 0)
         axis# Vector3/zero]
     (.ToAngleAxis ~q (by-ref ang#) (by-ref axis#))
     [ang# axis#]))

;; this gives some weird SIGILL problem
;; (defn angle-axis [^Double angle, ^Vector3 axis]
;;   (Quaternion/AngleAxis angle, axis))

(definline angle-axis ^Quaternion [angle, axis]
  `(Quaternion/AngleAxis ~angle, ~axis))

(definline qforward ^Vector3 [^Quaternion q]
  `(q* ~q Vector3/forward))

(definline aa ^Quaternion [ang x y z]
  `(angle-axis ~ang (v3 ~x ~y ~z)))

(defn qlookat ^Quaternion
  {:inline (fn
             ([here there]
              `(qlookat ~here ~there Vector3/up))
             ([here there up]
              `(Quaternion/LookRotation (v3- ~there ~here) ~up)))
   :inline-arities #{2 3}}
  ([^Vector3 here, ^Vector3 there]
     (qlookat here there Vector3/up))
  ([^Vector3 here, ^Vector3 there, ^Vector3 up]
     (Quaternion/LookRotation (v3- there here) up)))


;; ------------------------------------------------------------
;; scale

(definline v2scale [a b]
  `(UnityEngine.Vector2/Scale ~a ~b))

(definline v3scale [a b]
  `(UnityEngine.Vector3/Scale ~a ~b))

(definline v4scale [a b]
  `(UnityEngine.Vector4/Scale ~a ~b))

;; ------------------------------------------------------------
;; more rotation

(definline point-pivot ^Vector3 [^Vector3 pt, ^Vector3 piv, ^Quaternion rot]
  `(let [piv# ~piv]
     (v3+ (qv* ~rot (v3- ~pt piv#))
       piv#)))

;; ============================================================
;; Matrix4x4

(defn- binding-gen [rhs-fn args]
  (mapcat (fn [argsym expr]
            `[~argsym ~(rhs-fn expr)])
    (repeatedly #(gensym "val_"))
    args))

(defn- matrix4x4-inline
  ([]
   'UnityEngine.Matrix4x4/identity)
  ([a]
   (let [asym (gensym "arg_")]
     `(let [~asym (float ~a)]
        (Arcadia.LinearHelper/matrix
          ~@(repeat 16 asym)))))
  ([a b c d]
   (let [bndgs (binding-gen identity [a b c d])]
     `(let [~@bndgs]
        (Arcadia.LinearHelper/matrixByRows
          ~@(take-nth 2 bndgs)))))
  ([a b c d & args]
   (let [bndgs (binding-gen #(list `float %)
                 (list* a b c d args))]
     `(let [~@bndgs]
        (Arcadia.LinearHelper/matrix
          ~@(take-nth 2 bndgs))))))

(defn matrix4x4
  {:inline (fn [& args]
             (apply matrix4x4-inline args))
   :inline-arities #{0 1 4 16}}
  (^Matrix4x4 []
   Matrix4x4/identity)
  (^Matrix4x4 [a]
   (LinearHelper/matrix
     a a a a
     a a a a
     a a a a
     a a a a))
  (^Matrix4x4 [r0 r1 r2 r3]
   (Matrix4x4/matrixByRows r0 r1 r2 r3))
  (^Matrix4x4 [a b c d
               e f g h
               i j k l
               m n o p]
   (LinearHelper/matrix
     a b c d
     e f g h
     i j k l
     m n o p)))

(defn m*
  {:inline (fn
             ([] Matrix4x4/identity)
             ([x] x)
             ([x & args]
              (nestl 'Matrix4x4/op_Multiply (cons x args))))
   :inline-arities >1?}
  ([] Matrix4x4/identity)
  ([a] a)
  ([a b]
   (Matrix4x4/op_Multiply a b))
  ([a b & args]
   (reduce m* (m* a b) args)))

(definline determinant [^Matrix4x4 m]
  `(Matrix4x4/Determinant ~m))

(definline transpose [^Matrix4x4 m]
  `(. ~m transpose))

(definline column [^Matrix4x4 m, col-inx]
  `(.GetColumn ~m ~col-inx))

(definline row [^Matrix4x4 m, row-inx]
  `(.GetRow ~m ~row-inx))

(definline put-column [^Matrix4x4 m, col-inx, col]
  `(Arcadia.LinearHelper/matrixPutColumn ~m ~col-inx ~col))

(definline put-row [^Matrix4x4 m, row-inx, row]
  `(Arcadia.LinearHelper/matrixPutRow ~m ~row-inx ~row))

(definline ortho ^Matrix4x4 [left right bottom top znear zfar]
  `(Matrix4x4/Ortho ~left ~right ~bottom ~top ~znear ~zfar))

(definline perspective ^Matrix4x4 [fov aspect znear zfar]
  `(Matrix4x4/Perspective ~fov ~aspect ~znear ~zfar))

(definline inverse [^Matrix4x4 m]
  `(Matrix4x4/Inverse ~m))

(defn trs [^Vector3 t, ^Quaternion r, ^Vector3 s]
  `(Matrix4x4/TRS ~t ~r ~s))

;; ============================================================
;; tests, until I find a better place to put them

(defn- run-tests []
  (binding [test/*test-out* *out*]
    (test/run-tests)))

;; we work with what we're given
(def ^:private default-epsilon 8E-8)

(defn- close-enough-v2
  ([a b]
   (close-enough-v2 default-epsilon))
  ([a b epsilon]
   (< (Vector2/Distance a b) epsilon)))

(defn- close-enough-v3
  ([a b]
   (close-enough-v3 default-epsilon))
  ([a b epsilon]
   (< (Vector3/Distance a b) epsilon)))

(defn- close-enough-v4
  ([a b]
   (close-enough-v4 default-epsilon))
  ([a b epsilon]
   (< (Vector4/Distance a b) epsilon)))

(test/deftest- test-addition
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

(test/deftest- test-rotation
  (test/is
    (close-enough-v3
      (point-pivot
        (v3 1 0 0)
        (v3 0)
        (aa 90 1 0 0))
      (v3 0 0 1))
    (close-enough-v3
      (point-pivot
        (v3 11 0 0)
        (v3 10 0 0)
        (aa 90 1 0 0))
      (v3 10 0 1))))
