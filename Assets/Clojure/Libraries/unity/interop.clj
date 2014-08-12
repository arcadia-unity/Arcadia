(ns unity.interop
  (:require
   [unity.reflect-utils :as ru]
   [clojure.test :as test])
  (:import [UnityEngine GameObject]))

;; namespace for essential unity interop conveniences.

(defn some-2
  "Uses reduced, should be faster + less garbage + more general than clojure.core/some"
  [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))

(defn in? [x coll]
  (boolean (some-2 #(= x %) coll)))

(defn type-name? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (instance? System.MonoType y)))))

(defn type-of-local-reference [x env]
  (assert (contains? env x))
  (let [lclb ^clojure.lang.CljCompiler.Ast.LocalBinding (env x)]
    (when (.get_HasClrType lclb)
      (.get_ClrType lclb))))

(defn type? [x]
  (instance? System.MonoType x))

(defn tag-type [x]
  (when-let [t (:tag (meta x))]
    (cond ;; this does seem kind of stupid. Can we really tag things
          ;; with mere symbols as types?
      (type? t) t
      (type-name? t) (resolve t))))

(defn type-of-reference [x env]
  (if (contains? env x) ; local?
    (type-of-local-reference x env)
    (or
      (tag-type x) ; tagged symbol
      (tag-type (resolve x))))) ; reference to tagged var, or whatever

;; really ought to be testing for arity as well
(defn type-has-method? [t mth]
  (in? (symbol mth) (map :name (ru/methods t))))

;; maybe we should be passing full method sigs around rather than
;; method names. Also maybe this should accommodate types that
;; everything extends, if such types exist
(defn known-implementer-reference? [x method-name env] 
  (boolean
    (when-let [tor (type-of-reference x env)]
      (type-has-method? tor method-name))))

(defmacro get-component* [obj t]
  (cond
    (contains? &env t)
    `(.GetComponent ~obj ~t)

    (and
      (known-implementer-reference? obj 'GetComponent &env)
      (type-name? t))
    `(.GetComponent ~obj (~'type-args ~t))

    :else
    `(.GetComponent ~obj ~t)))

;; sadly I'm not sure this will actually warn us at runtime; I think
;; the reflection warning occurs when we compile get-component, not
;; when we run it. Perhaps we need another flag (that can be disabled)
;; for runtime reflection?
(defn get-component
  {:inline (fn [obj t]
             (list 'unity.interop/get-component* obj t))
   :inline-arities #{2}}
  [obj t]
  (.GetComponent obj t))

;; ==================================================
;; tests 
;; ==================================================

(defmacro with-new-object [obj-var & body]
  `(let [~obj-var (GameObject.)
         retval# (do ~@body)]
     (UnityEngine.Object/DestroyImmediate ~obj-var)
     retval#))

;; hm, how do you automate reflection tests?
;; binding *err* is possible but also gross

(test/deftest get-component
  (test/is
    (with-new-object ob
      (instance? UnityEngine.Transform
        (get-component ob UnityEngine.Transform)))))

