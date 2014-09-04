(ns unity.core
  (:require [unity.reflect :as r])
  (:import [UnityEngine MonoBehaviour]))

;; ============================================================
;; defscript 
;; ============================================================

(defn- parse-opts [s]
  (loop [opts {} [k v & rs :as s] s]
    (if (keyword? k)
      (recur (assoc opts k v) rs)
      [opts s])))

(defn- parse-impls [specs]
  (loop [ret {} s specs]
    (if (seq s)
      (recur (assoc ret (first s) (take-while seq? (next s)))
             (drop-while seq? (next s)))
      ret)))
 
(defn ^{:private true}
  maybe-destructured
  [params body]
  (if (every? symbol? params)
    (cons params body)
    (loop [params params
           new-params []
           lets []]
      (if params
        (if (symbol? (first params))
          (recur (next params) (conj new-params (first params)) lets)
          (let [gparam (gensym "p__")]
            (recur (next params) (conj new-params gparam)
              (-> lets (conj (first params)) (conj gparam)))))
        `(~new-params
           (let ~lets
             ~@body))))))

(defn- parse-opts+specs [opts+specs]
  (let [[opts specs] (parse-opts opts+specs)
        impls (parse-impls specs)
        interfaces (-> (map #(if (var? (resolve %)) 
                               (:on (deref (resolve %)))
                               %)
                         (keys impls))
                     set
                     (disj 'Object 'java.lang.Object)
                     vec)
        methods (map (fn [[name params & body]]
                       (cons name (maybe-destructured params body)))
                  (apply concat (vals impls)))]
    (when-let [bad-opts (seq (remove #{:no-print} (keys opts)))]
      (throw (ArgumentException. (apply print-str "Unsupported option(s) -" bad-opts)))) ;;; IllegalArgumentException
    [interfaces methods opts]))

(defn- build-positional-factory
  "Used to build a positional factory for a given type/record.  Because of the
  limitation of 20 arguments to Clojure functions, this factory needs to be
  constructed to deal with more arguments.  It does this by building a straight
  forward type/record ctor call in the <=20 case, and a call to the same
  ctor pulling the extra args out of the & overage parameter.  Finally, the
  arity is constrained to the number of expected fields and an ArityException
  will be thrown at runtime if the actual arg count does not match."
  [nom classname fields]
  (let [fn-name (symbol (str '-> nom))
        [field-args over] (split-at 20 fields)
        field-count (count fields)
        arg-count (count field-args)
        over-count (count over)
        docstring (str "Positional factory function for class " classname ".")]
    `(defn ~fn-name
       ~docstring
       [~@field-args ~@(if (seq over) '[& overage] [])]
       ~(if (seq over)
          `(if (= (count ~'overage) ~over-count)
             (new ~classname
               ~@field-args
               ~@(for [i (range 0 (count over))]
                   (list `nth 'overage i)))
             (throw (clojure.lang.ArityException. (+ ~arg-count (count ~'overage)) (name '~fn-name))))
          `(new ~classname ~@field-args)))))

(defn- emit-defclass* 
  "Do not use this directly - use deftype"
  [tagname name extends assem fields interfaces methods]
  (assert (and (symbol? extends) (symbol? assem)))
  (let [classname (with-meta
                    (symbol
                      (str (namespace-munge *ns*) "." name))
                    (meta name))
        interfaces (conj interfaces 'clojure.lang.IType)]
    `(defclass*
       ~tagname ~classname
       ~extends ~assem
       ~fields 
       :implements ~interfaces 
       ~@methods))) 

;; (defmacro defclass
;;   {:arglists '([name extends assem [& fields] & opts+specs])}
;;   [name extends assem [& fields] & opts+specs]
;;   ;;(validate-fields fields)
;;   (let [gname name 
;;         [interfaces methods opts] (parse-opts+specs opts+specs)
;;         ns-part (namespace-munge *ns*)
;;         classname (symbol (str ns-part "." gname))
;;         hinted-fields fields
;;         fields (vec (map #(with-meta % nil) fields))
;;         [field-args over] (split-at 20 fields)]
;;     `(let []
;;        ~(emit-defclass*
;;           name gname
;;           extends
;;           assem
;;           (vec hinted-fields) (vec interfaces) methods)
;;        (import ~classname)
;;        ~(build-positional-factory gname classname fields)
;;        ~classname)))

(defn- validate-fields
  ""
  [fields]
  (when-not (vector? fields)
    (throw (Exception. "No fields vector given."))) ;;; AssertionError.
  (let [specials #{'__meta '__extmap}]
    (when (some specials fields)
      (throw (Exception. (str "The names in " specials " cannot be used as field names for types or records."))))))

(defmacro defscript
  ;;(validate-fields fields)
  [name fields & opts+specs]
  (validate-fields fields)
  (let [gname name 
        [interfaces methods opts] (parse-opts+specs opts+specs)
        ns-part (namespace-munge *ns*)
        classname (symbol (str ns-part "." gname))
        hinted-fields fields
        fields (vec (map #(with-meta % nil) fields))
        [field-args over] (split-at 20 fields)]
    `(let []
       ~(emit-defclass*
          name
          gname
          'UnityEngine.MonoBehaviour
          'UnityEngine
          (vec hinted-fields)
          (vec interfaces)
          methods)
       (import ~classname)
       ~(build-positional-factory gname classname fields)
       ~classname)))

;; ============================================================
;; interop
;; ============================================================

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
  (or (tag-type x) ; tagged symbol
      (when (contains? env x) (type-of-local-reference x env)) ; local
      (when (symbol? x) (tag-type (resolve x))))) ; reference to tagged var, or whatever 

;; really ought to be testing for arity as well
(defn type-has-method? [t mth]
  (in? (symbol mth) (map :name (r/methods t :ancestors true))))

;; maybe we should be passing full method sigs around rather than
;; method names. 
(defn known-implementer-reference? [x method-name env]
  (boolean
    (when-let [tor (type-of-reference x env)]
      (type-has-method? tor method-name))))

(defn raise-args [[head & rst]]
  (let [gsyms (repeatedly (count rst) gensym)]
    `(let [~@(interleave gsyms rst)]
       ~(cons head gsyms))))

(defn raise-non-symbol-args [[head & rst]]
  (let [bndgs (zipmap 
                (remove symbol? rst)
                (repeatedly gensym))]
    `(let [~@(mapcat reverse bndgs)]
       ~(cons head (replace bndgs rst)))))

(defmacro get-component* [obj t]
  (if (not-every? symbol? [obj t])
    (raise-non-symbol-args
      (list 'unity.interop/get-component* obj t))
    (cond
      (contains? &env t)
      `(.GetComponent ~obj ~t)


      (and
        (known-implementer-reference? obj 'GetComponent &env)
        (type-name? t))
      `(.GetComponent ~obj (~'type-args ~t))

      :else
      `(.GetComponent ~obj ~t))))

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
