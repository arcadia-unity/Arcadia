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

(defn- comp-impl []
  (let [max-args 20
        base-body (fn [outer-args]
                    (letfn [(max-arg-fn [[inner-args]]
                              `([~@(pop inner-args) ~'& ~'more]
                                (-> ~@(update outer-args 0
                                        (fn [f]
                                          `(apply ~f ~@(pop inner-args) ~'more))))))
                            (base-fn [inner-args]
                              (let [base (-> (vec (reverse outer-args))
                                             (update 0 #(cons % inner-args)))]
                                `(~inner-args (-> ~@base))))]
                      (list* `fn (symbol (str "composed-" (count outer-args)))
                        (am/arities-forms base-fn
                          {::am/max-args max-args
                           ::am/arg-fn (vec (am/classy-args max-args))
                           ::am/cases {max-args max-arg-fn}}))))
        base (vec
               (am/arities-forms #(list % (base-body %))
                 {::am/max-args max-args
                  ::am/arg-fn #(symbol (str "fn-" (inc %)))
                  ::am/cases {0 (fn [_] `([] identity))
                              1 (fn [[[f]]] `([~f] ~f))                           
                              max-args (fn [[outer-args]]
                                         `([~@(pop outer-args) ~'& ~'fn-more]
                                           (reduce comp
                                             (comp ~@(take (dec max-args) outer-args))
                                             ~'fn-more)))}}))]
    base))

(am/defn-meval comp
  "Faster version of comp than clojure.core/comp. Maybe should just swap out core comp for this."
  (comp-impl))


