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

(defn- mtag [x tag]
  (if tag
    (with-meta x (assoc (meta x) :tag tag))
    x))

(defn nestl [op args]
  (cond
    (next args) (reduce #(list op %1 %2) args)
    (first args) (list op (first args))
    :else (list op)))

;; remove this when we fix #298
(defn- casting
  ([sym type]
   (casting sym sym type))
  ([sym form type]
   (if (= type 'System.Single)
     `[~sym (float ~form)]
     `[~(mtag sym type) ~form])))

(defn inliner [op param-type args]
  (if (< 1 (count param-type))
    (let [[_ t] param-type
          type-lock-sym (gensym "type-lock_")
          args2 (assoc (vec args) 1 type-lock-sym)]
      `(let ~(casting type-lock-sym (nth args 1) t)
         ~(nestl op args2)))
    (nestl op args)))

(defn- inline-arity-pred [& arities]
  (let [big (apply max arities)
        as (set arities)]
    (fn [n]
      (or (< big n)
          (contains? as n)))))

(defmacro ^:private def-vop-lower [name opts]
  (mu/checked-keys [[op] opts]
    (let [{:keys [doc return-type param-type unary-op unary-expr nullary-expr doc]} opts
          param-type (cond
                       (sequential? param-type)
                       param-type

                       param-type
                       [param-type])
          args (let [[x & ys :as args] (take 21 (im/classy-args))]
                 (if param-type
                   (cons (mtag x (first param-type)) ys)
                   args))
          arity-bodies (for [n (range 2 20)]
                         (let [params (mtag (vec (take n args)) return-type)
                               bndgs (if (< 1 (count param-type))
                                       (let [[_ bt] param-type
                                             [_ y] params]
                                         (casting y bt))
                                       [])
                               body (nestl op (take n args))]
                           `(~params
                             (let ~bndgs
                               ~body))))
          optspec (cond-> {:inline-arities (cons `inline-arity-pred
                                             (cond-> [2]
                                                     nullary-expr (conj 0)
                                                     (or unary-op unary-expr) (conj 1)))
                           :arglists `(quote ~(list '[args*]))
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
                                        (inliner (quote ~op) (quote ~param-type) (cons x# xs#)))])}
                          doc (assoc :doc doc))
          body (remove nil?
                 (concat
                   [(when nullary-expr
                      (list [] nullary-expr))
                    (cond
                      unary-expr (list [(first args)] unary-expr)
                      unary-op `([~(first args)]
                                 (~unary-op ~(first args))))]
                   arity-bodies
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
  "Constructs a `Vector2`.

  If called with zero arguments, returns `Vector2/one`. If called with one argument
  `x` returns, a `Vector2` with all its coordinates set to `x`."
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
  "Constructs a `Vector3`.

  If called with zero arguments, returns `Vector3/one`. If called with one argument
  `x` returns, a `Vector3` with all its coordinates set to `x`."
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
  "Constructs a `Vector4`.

  If called with zero arguments, returns `Vector4/one`. If called with one argument
  `x` returns, a `Vector4` with all its coordinates set to `x`."
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
  "Constructs a `Quaternion`.

  If called with zero arguments, returns `Quaternion/identity`."
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
   :unary-expr a
   :doc
   "Adds `Vector2`s.

If called with zero arguments, returns `Vector2/zero`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v3+
  {:op UnityEngine.Vector3/op_Addition
   :return-type UnityEngine.Vector3
   :nullary-expr UnityEngine.Vector3/zero
   :unary-expr a
   :doc
   "Adds `Vector3`s.

If called with zero arguments, returns `Vector3/zero`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v4+
  {:op UnityEngine.Vector4/op_Addition
   :return-type UnityEngine.Vector4
   :nullary-expr UnityEngine.Vector4/zero
   :unary-expr a
   :doc
   "Adds `Vector4`s.

If called with zero arguments, returns `Vector4/zero`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

;; ------------------------------------------------------------
;; -

(def-vop-lower v2-
  {:op UnityEngine.Vector2/op_Subtraction
   :return-type UnityEngine.Vector2
   :unary-op UnityEngine.Vector2/op_UnaryNegation
   :doc
   "Subtracts `Vector2`s.

If called with one argument, the negation of that argument is returned; that is, the `Vector2` that results from multiplying all the components of the input `Vector2` by -1.

Calls to this function will be inlined if possible."})

(def-vop-lower v3-
  {:op UnityEngine.Vector3/op_Subtraction
   :return-type UnityEngine.Vector3
   :unary-op UnityEngine.Vector3/op_UnaryNegation
   :doc
   "Subtracts `Vector3`s.

If called with one argument, the negation of that argument is returned; that is, the `Vector3` that results from multiplying all the components of the input `Vector3` by -1.

Calls to this function will be inlined if possible."})

(def-vop-lower v4-
  {:op UnityEngine.Vector4/op_Subtraction
   :return-type UnityEngine.Vector4
   :unary-op UnityEngine.Vector4/op_UnaryNegation
   :doc "Subtracts `Vector4`s.

If called with one argument, the negation of that argument is returned; that is, the `Vector4` that results from multiplying all the components of the input `Vector4` by -1.

Calls to this function will be inlined if possible."})

;; ------------------------------------------------------------
;; *

(def-vop-lower v2*
  {:op UnityEngine.Vector2/op_Multiply
   :return-type UnityEngine.Vector2
   :param-type [UnityEngine.Vector2, System.Single]
   :nullary-expr UnityEngine.Vector2/one
   :unary-expr a
   :doc "Multiplies a `Vector2` by one or more floats. The `Vector2` must be the first argument (so the arguments of this function do not commute).

If called with zero arguments, returns `Vector2/one`. If called with one argument, returns that argument, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v3*
  {:op UnityEngine.Vector3/op_Multiply
   :return-type UnityEngine.Vector3
   :param-type [UnityEngine.Vector3, System.Single]
   :nullary-expr UnityEngine.Vector3/one
   :unary-expr a
   :doc "Multiplies a `Vector3` by one or more floats. The `Vector3` must be the first argument (so the arguments of this function do not commute).

If called with zero arguments, returns `Vector3/one`. If called with one argument, returns that argument, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v4*
  {:op UnityEngine.Vector4/op_Multiply
   :return-type UnityEngine.Vector4
   :param-type [UnityEngine.Vector4, System.Single]
   :nullary-expr UnityEngine.Vector4/one
   :unary-expr a
   :doc "Multiplies a `Vector4` by one or more floats. The `Vector4` must be the first argument (so the arguments of this function do not commute).

If called with zero arguments, returns `Vector4/one`. If called with one argument, returns that argument, as with `identity`.

Calls to this function will be inlined if possible."})

;; ------------------------------------------------------------
;; div

(def-vop-lower v2div
  {:op UnityEngine.Vector2/op_Division
   :return-type UnityEngine.Vector2
   :param-type [UnityEngine.Vector2 System.Single]
   :unary-expr (Arcadia.LinearHelper/invertV2 a)
   :doc "Divides a `Vector2` by one or more floats. The `Vector2` must be the first argument (so the arguments of this function do not commute).

If called with one `Vector2`, returns that Vector2 with its components inverted.

Calls to this function will be inlined if possible."})

(def-vop-lower v3div
  {:op UnityEngine.Vector3/op_Division
   :return-type UnityEngine.Vector3
   :param-type [UnityEngine.Vector3 System.Single]
   :unary-expr (Arcadia.LinearHelper/invertV3 a)
   :doc "Divides a `Vector3` by one or more floats. The `Vector3` must be the first argument (so the arguments of this function do not commute).

If called with one `Vector3`, returns that Vector3 with its components inverted.

Calls to this function will be inlined if possible."})

(def-vop-lower v4div
  {:op UnityEngine.Vector4/op_Division
   :return-type UnityEngine.Vector4
   :param-type [UnityEngine.Vector4 System.Single]
   :unary-expr (Arcadia.LinearHelper/invertV4 a)
   :doc "Divides a `Vector4` by one or more floats. The `Vector4` must be the first argument (so the arguments of this function do not commute).

If called with one `Vector4`, returns that Vector4 with its components inverted.

Calls to this function will be inlined if possible."})

;; ------------------------------------------------------------
;; dist

;; ------------------------------------------------------------
;; Quaternions
;; and then there's this
;; inline etc this stuff when time allows

(defn ^:private >1? [n] (clojure.lang.Numbers/gt n 1))

;; should always return a Quaternion
(def-vop-lower qq*
  {:op UnityEngine.Quaternion/op_Multiply
   :param-type UnityEngine.Quaternion
   :return-type UnityEngine.Quaternion
   :nullary-expr UnityEngine.Quaternion/identity
   :unary-expr a
   :doc "Multiplies one or more `Quaternions`.

If called with zero arguments, returns `Quaternion/identity`. If called with one argument, returns that argument.

Calls to this function will be inlined if possible."})

;; should always return a Vector3, which makes unary difficult,
;; and if unary doesn't work neither should nullary
(def-vop-lower qv*
  {:op UnityEngine.Quaternion/op_Multiply
   :return-type UnityEngine.Vector3
   :param-type [UnityEngine.Quaternion UnityEngine.Vector3]
   :nullary-expr (throw (clojure.lang.ArityException. "qv* requires at least two arguments, got zero."))
   :unary-expr (throw (clojure.lang.ArityException. "qv* requires at least two arguments, got one."))
   :doc "Multiplies a `Quaternion` by one or more `Vector3`s. The first argument must be a `Quaternion`, and the remaining arguments must be `Vector3`s.

Calls to this function will be inlined if possible."})

(def-vop-lower q*
  {:op UnityEngine.Quaternion/op_Multiply
   :nullary-expr (throw (clojure.lang.ArityException. "q* requires at least two arguments, got zero."))
   :unary-expr (throw (clojure.lang.ArityException. "q* requires at least two arguments, got one."))
   :doc "Multiplies a `Quaternion` by one or more `Vector3`s or `Quaternion`s.

Calls to this function will be inlined if possible."})

(definline euler
  "Wraps `Quaternion/Euler`.

  Calls to this function will be inlined if possible."
  ^UnityEngine.Quaternion [^UnityEngine.Vector3 v]
  `(UnityEngine.Quaternion/Euler ~v))

(definline euler-angles
  "Wraps the `Quaternion/eulerAngles`.

  Calls to this function will be inlined if possible."
  ^UnityEngine.Vector3 [^UnityEngine.Quaternion q]
  `(.eulerAngles ~q))

(definline to-angle-axis
  "Given a `Quaternion` `q`, returns a collection containing the angle (float) and axis (`Vector3`) that represents that `Quaternion`, as set by the `Quaternions/ToAngleAxis`.

  Calls to this function will be inlined if possible."
  [^UnityEngine.Quaternion q]
  `(Arcadia.LinearHelper/toAngleAxis ~q))

(definline angle-axis
  "Given an angle (float) and an axis (`Vector3`), constructs a `Quarternion`, as per `Quaternion/AngleAxis`.

  Calls to this function will be inlined if possible."
  ^UnityEngine.Quaternion [angle, axis]
  `(UnityEngine.Quaternion/AngleAxis ~angle, ~axis))

(definline qforward
  "Given a `Quaternion` `q`, returns the Vector3 derived by multiplying `q` by `Vector3/forward`.

  Calls to this function will be inlined if possible."
  ^UnityEngine.Vector3 [^UnityEngine.Quaternion q]
  `(q* ~q UnityEngine.Vector3/forward))

(definline aa
  "Shortcut for angle-axis. `(aa a x y z)` is the same as `(angle-axis a (v3 x y z))`.

  Calls to this function will be inlined if possible."
  ^UnityEngine.Quaternion [ang x y z]
  `(angle-axis ~ang (v3 ~x ~y ~z)))

(defn qlookat ^UnityEngine.Quaternion
  {:inline (fn
             ([here there]
              `(qlookat ~here ~there UnityEngine.Vector3/up))
             ([here there up]
              `(UnityEngine.Quaternion/LookRotation (v3- ~there ~here) ~up)))
   :inline-arities #{2 3}
   :doc "Returns a `Quaternion` that faces `there` from `here`, both `Vector3`s. An
   additional `up` `Vector3` can be provided to specify which direction is 'up'.
   `up` defaults to `Vector3/up`."}
  ([^UnityEngine.Vector3 here, ^UnityEngine.Vector3 there]
     (qlookat here there UnityEngine.Vector3/up))
  ([^UnityEngine.Vector3 here, ^UnityEngine.Vector3 there, ^UnityEngine.Vector3 up]
     (UnityEngine.Quaternion/LookRotation (v3- there here) up)))


;; ------------------------------------------------------------
;; scale

(def-vop-lower v2scale
  {:op UnityEngine.Vector2/Scale
   :return-type UnityEngine.Vector2
   :nullary-expr UnityEngine.Vector2/one
   :unary-expr a
   :doc "Scales one or more `Vector2`s, as per `Vector2/Scale`.

If called with zero arguments, returns `Vector2/one`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v3scale
  {:op UnityEngine.Vector3/Scale
   :return-type UnityEngine.Vector3
   :nullary-expr UnityEngine.Vector3/one
   :unary-expr a
   :doc "Scales one or more `Vector3`s, as per `Vector3/Scale`.

If called with zero arguments, returns `Vector3/one`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

(def-vop-lower v4scale
  {:op UnityEngine.Vector4/Scale
   :return-type UnityEngine.Vector4
   :nullary-expr UnityEngine.Vector4/one
   :unary-expr a
   :doc "Scales one or more `Vector4`s, as per `Vector4/Scale`.

If called with zero arguments, returns `Vector4/one`. If called with one argument, that argument is returned, as with `identity`.

Calls to this function will be inlined if possible."})

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
  "Constructs a `Matrix4x4`"
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
   (LinearHelper/matrixByRows r0 r1 r2 r3))
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
  "Multiplies one or more `Matrix4x4`s.

If called with zero arguments, returns `Matrix4x4/identity`. If called with one argument, returns that argument.

Calls to this function will be inlined if possible."
  {:inline (fn
             ([] Matrix4x4/identity)
             ([x] x)
             ([x & args]
              (nestl 'UnityEngine.Matrix4x4/op_Multiply (cons x args))))
   :inline-arities >1?}
  ([] Matrix4x4/identity)
  ([a] a)
  ([a b]
   (UnityEngine.Matrix4x4/op_Multiply a b))
  ([a b & args]
   (reduce m* (m* a b) args)))

(definline determinant
  "Wraps `Matrix4x4/determinant`"
  [^UnityEngine.Matrix4x4 m]
  `(. ~m determinant))

(definline transpose
  "Wraps `Matrix4x4/transpose`"
  [^UnityEngine.Matrix4x4 m]
  `(. ~m transpose))

(definline column
  "Wraps `Matrix4x4/GetColumn`"
  [^UnityEngine.Matrix4x4 m, col-inx]
  `(.GetColumn ~m ~col-inx))

(definline row
  "Wraps `Matrix4x4/GetRow`"
  [^UnityEngine.Matrix4x4 m, row-inx]
  `(.GetRow ~m ~row-inx))

(definline put-column 
  "Sets column number `col-inx` of `Matrix4x4` `m` to the `Vector4` `col`"
  [^UnityEngine.Matrix4x4 m, col-inx, col]
  `(Arcadia.LinearHelper/matrixPutColumn ~m ~col-inx ~col))

(definline put-row
  "Sets row number `row-inx` of `Matrix4x4` `m` to the `Vector4` `row`"
  [^UnityEngine.Matrix4x4 m, row-inx, row]
  `(Arcadia.LinearHelper/matrixPutRow ~m ~row-inx ~row))

(definline ortho
  "Wraps `Matrix4x4/Ortho`"
  ^UnityEngine.Matrix4x4 [left right bottom top znear zfar]
  `(UnityEngine.Matrix4x4/Ortho ~left ~right ~bottom ~top ~znear ~zfar))

(definline perspective
  "Wraps `Matrix4x4/Perspective`"
  ^UnityEngine.Matrix4x4 [fov aspect znear zfar]
  `(UnityEngine.Matrix4x4/Perspective ~fov ~aspect ~znear ~zfar))

(definline inverse
  "Wraps `Matrix4x4/inverse`"
  [^UnityEngine.Matrix4x4 m]
  `(. ~m inverse))

(definline trs
  "Wraps `Matrix4x4/TRS`"
  [^UnityEngine.Vector3 t, ^UnityEngine.Quaternion r, ^UnityEngine.Vector3 s]
  `(UnityEngine.Matrix4x4/TRS ~t ~r ~s))
