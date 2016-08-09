(ns arcadia.internal.macro)

;; ============================================================
;; evaluation manipulation

;; ------------------------------------------------------------
;; notes on meval:

;; (am/meval (+ 1 1))

;; expands to:

;; 2

;; ---

;; (am/meval `(+ 1 1))

;; expands to:

;; (clojure.core/+ 1 1)

;; ---

;; (let [a 1]
;;   (am/meval (+ a a)))

;; throws an exception, because 'a isn't defined at expansion time.

;; ---

;; (let [a 1]
;;   (am/meval (+ 'a 'a)))

;; throws an exception, because (eval '(+ 'a 'a)) throws an exception.

;; ---

;; (let [a 1]
;;   (am/meval ('+ 'a 'a)))

;; expands to

;; (let* [a 1]
;;   a)

;; because ('+ 'a 'a) gives the same result as (get '+ 'a 'a). Since
;; the symbol '+ is not associative, 'a isn't (and can't be) in it, so
;; get will return its second, "not-found" argument, which in this
;; case is also 'a.

;; ---

;; (let [a 1]
;;   (am/meval `('+ 'a 'a)))

;; expands to

;; (let* [a 1]
;;   ('clojure.core/+ 'user/a 'user/a))

;; which at runtime will evaluate to

;; 'user/a

;; for the same reason as the last example.

;; ---

;; (let [a 1]
;;   (am/meval `(+ ~'a ~'a)))

;; expands to

;; (let* [a 1]
;;   (clojure.core/+ a a))

;; which is probably what we wanted here, and at runtime evalutes to 2.

(defmacro meval
  "Expands to the result of (eval form). Useful for manipulating the order of compile-time evaluation, thereby potentially improving ergonomics of code generation. Also handy for writing bizarre, illegible, or weirdly buggy code; please use with caution. See comments aracadia/internal/macro.clj for examples of expansion, and potential resulting bugs."
  [form]
  (eval form))

(defmacro defn-meval
  "Macro for generating function definitions. Uses include generating many arities for a function. The final form in tail-forms-and-code-form will be evaluated at expansion time and spliced into the body of the function.

  Example:

  (am/defn-meval backwards-subtract
  \"an amazing function\"
  (for [i (range 3)
        :let [args (vec (repeatedly i gensym))]]
    `(~args ~(cons '- (reverse args)))))

  expands to:

  (clojure.core/defn backwards-subtract
  \"an amazing function\"
  ([] (-))
  ([G__26409] (- G__26409))
  ([G__26410 G__26411] (- G__26411 G__26410)))

  I've found vectors useful data structures for generating arities, since it is easy to generate all the default cases, then use update to modify them for special cases, such as 0, 1, and 20/more-than-19 arguments."
  [name & tail-forms-and-code-form]
  `(defn ~name
     ~@(butlast tail-forms-and-code-form)
     ~@(eval (last tail-forms-and-code-form)))  )

;; ============================================================
;; args

(def alphabet
  (mapv str
    (sort "qwertyuiopasdfghjklzxcvbnm")))

;; ungry-bettos!

(defn classy-arg-strs
  ([]
   (for [n (cons nil (drop 2 (range)))
         c alphabet]
     (str c n)))
  ([n]
   (take n (classy-arg-strs))))

(defn classy-args
  ([] (map symbol (classy-arg-strs)))
  ([n] (map symbol (classy-arg-strs n))))

;; ============================================================
;; arity generation helpers

(defn arities-forms
  ([base-fn] (arities-forms base-fn nil))
  ([base-fn, {:keys [::max-args, ::cases, ::arg-fn]
              :or {::max-args 20,
                   ::cases {},
                   ::arg-fn #(gensym (str "arg-" (inc %) "_"))}}]
   (let [args (map arg-fn (range max-args))
         arity-args (vec (reductions conj [] args))
         cases-fn (fn [i val]
                    (if (contains? cases i)
                      ((cases i) val)
                      val))]
     (into []
       (comp
         (map base-fn)
         (map-indexed cases-fn))
       arity-args))))

;; ============================================================
;; type utils
;; ============================================================

(defn- same-or-subclass? [^Type a ^Type b]
  (or (= a b)
      (.IsSubclassOf a b)))

;; put elsewhere
(defn- some-2
  "Uses reduced, should be faster + less garbage + more general than clojure.core/some"
  [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))

(defn- in? [x coll]
  (boolean (some-2 #(= x %) coll)))
 ; reference to tagged var, or whatever 

(defn- type-name? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (instance? System.MonoType y)))))

(defn- type-of-local-reference [x env]
  (assert (contains? env x))
  (let [lclb ^clojure.lang.CljCompiler.Ast.LocalBinding (env x)]
    (when (.get_HasClrType lclb)
      (.get_ClrType lclb))))

(defn- type? [x]
  (instance? System.MonoType x))

(defn- ensure-type [x]
  (cond
    (type? x) x
    (symbol? x) (let [xt (resolve x)]
                  (if (type? xt)
                    xt
                    (throw
                      (Exception.
                        (str "symbol does not resolve to a type")))))
    :else (throw
            (Exception.
              (str "expects type or type symbol")))))

(defn- tag-type [x]
  (when-let [t (:tag (meta x))]
    (ensure-type t)))

(defn- type-of-reference [x env]
  (when (symbol? x)
    (or (tag-type x)
      (if (contains? env x)
        (type-of-local-reference x env) ; local
        (let [v (resolve x)] ;; dubious
          (when (not (and (var? v) (fn? (var-get v))))
            (tag-type v))))))) 

;; ============================================================
;; condcast->

(defn- maximize
  ([xs]
   (maximize (comparator >) xs))
  ([compr xs]
   (when (seq xs)
     (reduce
       (fn [mx x]
         (if (= 1 (compr mx x))
           x
           mx))
       xs))))

(defn- most-specific-type ^Type [& types]
  (maximize (comparator same-or-subclass?)
    (remove nil? types)))

(defn- contract-condcast-clauses [expr xsym clauses env]
  (let [etype (most-specific-type
                (type-of-reference expr env)
                (tag-type xsym))]
    (if etype
      (if-let [[_ then] (first
                          (filter #(= etype (ensure-type (first %)))
                            (partition 2 clauses)))]
        [then]
        (->> clauses
          (partition 2)
          (filter
            (fn [[t _]]
              (same-or-subclass? etype (ensure-type t))))
          (apply concat)))
      clauses)))

;; note this takes an optional default value. This macro is potentially
;; annoying in the case that you want to branch on a supertype, for
;; instance, but the cast would remove interface information. Use with
;; this in mind.
(defmacro condcast-> [expr xsym & clauses]
  (let [[clauses default] (if (even? (count clauses))
                            [clauses nil] 
                            [(butlast clauses)
                             [:else
                              `(let [~xsym ~expr]
                                 ~(last clauses))]])
        clauses (contract-condcast-clauses
                  expr xsym clauses &env)]
    (cond
      (= 0 (count clauses))
      `(let [~xsym ~expr]
         ~default) ;; might be nil obvi

      (= 1 (count clauses)) ;; corresponds to exact type match. janky but fine
      `(let [~xsym ~expr]
         ~@clauses)

      :else
      `(let [~xsym ~expr]
         (cond
           ~@(->> clauses
               (partition 2)
               (mapcat
                 (fn [[t then]]
                   `[(instance? ~t ~xsym)
                     (let [~(with-meta xsym {:tag t}) ~xsym]
                       ~then)]))))))))
