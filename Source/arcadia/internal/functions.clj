(ns ^{:doc
      "Useful general-purpose higher order functions, with emphasis on perf.
 Includes optimized implementation of comp."}
    arcadia.internal.functions
  (:refer-clojure :exclude [comp, partial])
  (:require [arcadia.internal.macro :as am]
            [arcadia.internal.benchmarking :as b]))

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
                           ::am/cases {max-args max-arg-fn}}))))]
    (vec
      (am/arities-forms #(list % (base-body %))
        {::am/max-args max-args
         ::am/arg-fn #(symbol (str "fn-" (inc %)))
         ::am/cases {0 (fn [_] `([] identity))
                     1 (fn [[[f]]] `([~f] ~f))                           
                     max-args (fn [[outer-args]]
                                `([~@(pop outer-args) ~'& ~'fn-more]
                                  (reduce comp
                                    (comp ~@(pop outer-args))
                                    ~'fn-more)))}}))))

(am/defn-meval comp
  "Faster version of comp than clojure.core/comp. Maybe should just swap out core comp for this."
  (comp-impl))

;; perf:

;; (b/n-timing 1e3
;;   (comp
;;     identity identity identity identity
;;     identity identity identity identity
;;     identity identity identity identity
;;     identity identity identity identity))

;; ~ 0.0008

;; (b/n-timing 1e3
;;   (clojure.core/comp
;;     identity identity identity identity
;;     identity identity identity identity
;;     identity identity identity identity
;;     identity identity identity identity))

;; ~ 0.018332

;; (am/defn-meval arg-eater
;;   (am/arities-forms list))

;; (let [f (comp
;;           identity identity identity identity
;;           identity identity identity identity
;;           identity identity identity identity
;;           identity identity identity identity
;;           arg-eater)]
;;   (b/n-timing 1e3
;;     (f
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla)))

;; ~ 0.000234

;; (let [f (clojure.core/comp
;;           identity identity identity identity
;;           identity identity identity identity
;;           identity identity identity identity
;;           identity identity identity identity
;;           arg-eater)]
;;   (b/n-timing 1e3
;;     (f
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla
;;       :bla :bla :bla :bla)))

;; ~ 0.005382

;; ============================================================
;; partial

;; partial in clojure is too stupid, I'm not writing this.

;; ============================================================
;; reducers, transducers

;; aka transreductorooni
(defn transreducer
  "Takes a transducer and a reducible, returns a reducible incorporating the transducer."
  [xfrm reducible]
  (reify
    clojure.core.protocols/CollReduce
    (coll-reduce [this f]
      (clojure.core.protocols/coll-reduce this f (f))) ;; right?
    (coll-reduce [_ f init]
      (transduce xfrm f init reducible))
    clojure.lang.Counted
    (count [_]
      (count reducible))))

;; ============================================================
;; other suboptimal core stuff

(defn some [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))
