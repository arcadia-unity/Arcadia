(ns ^{:doc
      "Useful general-purpose higher order functions.
 Includes optimized implementation of comp."}
    arcadia.internal.functions
  (:refer-clojure :exclude [comp])
  (:require [arcadia.internal.macro :as am]))

;; ============================================================
;; comp

;; 400 total composed arities: explicit cases for up to 19 input functions,
;; plus a case for over 20, then for each of those, explicit cases for
;; up to 19 arguments to the composed function, plus a case for over
;; 20 such arguments. Code explodey, but as-fast-as-possible comp is especially
;; important for us, because of its relevance to transducers.

;; To see the innards, (pprint (comp-impl)) with *print-length* set to
;; something reasonable.

(defn- arities-forms
  ([base-fn] (arities-forms base-fn nil))
  ([base-fn, {:keys [::max-args, ::cases, ::arg-fn]
              :or {::max-args 21,
                   ::cases {},
                   ::arg-fn #(gensym (str "arg-" (inc %) "_"))}}]
   (let [args (map arg-fn (range max-args))
         arity-args (vec (reductions conj [] args))
         cases-fn (fn [i val]
                    (if (contains? cases i)
                      ((cases i) val)
                      val))]
     (into []
       (clojure.core/comp
         (map base-fn)
         (map-indexed cases-fn))
       arity-args))))

(defn- comp-impl []
  (let [max-args 5
        base-body (fn [outer-args]
                    (letfn [(max-arg-fn [[inner-args]]
                              `([~@(pop inner-args) ~'& ~'more]
                                (->
                                  ~@(update outer-args 0
                                      (fn [f]
                                        `(apply ~f ~@(pop inner-args) ~'more))))))]
                      (list* `fn (symbol (str "composed-" (count outer-args)))
                        (arities-forms (fn [inner-args]
                                         (let [base (-> (vec (reverse outer-args))
                                                        (update 0 #(cons % inner-args)))]
                                           `(~inner-args (-> ~@base))))
                          {::max-args max-args
                           ::arg-fn (vec (am/classy-args max-args))
                           ::cases {max-args max-arg-fn}}))))
        base (vec
               (arities-forms #(list % (base-body %))
                 {::max-args max-args
                  ::arg-fn #(symbol (str "fn-" (inc %)))
                  ::cases {0 (fn [_] `([] identity))
                           1 (fn [[[f]]] `([~f] ~f))                           
                           max-args (fn [[outer-args]]
                                      (list
                                        (-> outer-args pop (conj '& 'more))
                                        `(reduce comp
                                           (comp ~@(take (dec max-args) outer-args))
                                           ~'fn-more)))}}))]
    base))

;; (am/defn-meval comp
;;   "Faster version of comp than clojure.core/comp. Maybe should just swap out core comp for this."
;;   (comp-impl))


