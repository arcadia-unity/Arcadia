(ns ^{:doc
      "Useful general-purpose higher order functions.
 Includes optimized implementation of comp."}
    arcadia.internal.functions
  (:refer-clojure :exclude [comp])
  (:require [arcadia.internal.macro :as am]))

;; ============================================================
;; comp

;; 400 total thingums: explicit cases for up to 19 input functions,
;; plus a case for over 20, then for each of those, explicit cases for
;; up to 19 arguments to the composed function, plus a case for over
;; 20 such arguments. Code explodey, but as-fast-as-possible comp is especially
;; important for us, because of its relevance to transducers.

;; To see the innards, (pprint (comp-impl)) with *print-length* set to
;; something reasonable.

(defn- comp-impl []
  (let [max-args 5
        outer-args (for [i (range (inc max-args))]
                     (symbol (str "fn_" i)))
        inner-args (am/classy-args)
        base-body-base (fn [outer-inx inner-inx]
                         (let [outer-args* (take outer-inx outer-args)
                               inner-args* (vec (take inner-inx inner-args))
                               base (vec (reverse outer-args*))]
                           (list (if (= max-args inner-inx)
                                   (-> inner-args* pop (conj '& 'more))
                                   inner-args*)
                             (cons `->
                               (if (= max-args inner-inx)
                                 (update base 0
                                   #(list* `apply %
                                      (-> inner-args* pop (conj 'more))))
                                 (update base 0 #(list* % inner-args*)))))))
        base-body (fn [outer-inx]
                    (list* `fn (gensym (str "composed_" outer-inx))
                      (for [inner-inx (range (inc max-args))]
                        (base-body-base outer-inx inner-inx))))
        base (vec
               (for [outer-inx (range (inc max-args))]
                 (let [outer-args* (vec (take outer-inx outer-args))
                       bb (base-body outer-inx)]
                   (list
                     (if (= max-args outer-inx)
                       (-> outer-args* pop (conj '& 'fn-more))
                       outer-args*)
                     (cond
                       (zero? outer-inx)
                       'identity

                       (= 1 outer-inx)
                       (first outer-args)
                       
                       (= max-args outer-inx)
                       `(reduce comp
                          (comp ~@(take (dec max-args) outer-args*))
                          ~'fn-more)

                       :else bb)))))]
    base))

(am/defn-meval comp
  "Faster version of comp than clojure.core/comp. Maybe should just swap out core comp for this."
  (comp-impl))


