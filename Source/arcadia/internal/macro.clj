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

(defmacro meval [form]
  "Expands to the result of (eval form). Useful for manipulating the order of compile-time evaluation, thereby potentially improving ergonomics of code generation. Also handy for writing bizarre, illegible, or weirdly buggy code; please use with caution. See comments aracadia/internal/macro.clj for examples of expansion, and potential resulting bugs."
  (eval form))

(defmacro defn-meval [name & tail-forms-and-code-form]
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
