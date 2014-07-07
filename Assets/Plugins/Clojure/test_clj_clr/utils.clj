(ns test-clj-clr.utils
  (:require [clojure.reflect :as r]
            [clojure.zip :as zip])
  (:import [clojure.reflect
            Field
            Property
            Method
            Constructor]))

;; Ye olde midden of missing functions one wishes were in
;; clojure.core. It would be fantastic to have some more authoritative
;; tool for including these things than the dread utility project. I
;; suppose in the context of dependency-management and transitive deps,
;; projects are essentially reference types, so the only way to
;; confidently include a utility library perenially subject to breaking
;; changes is to have lots of little individually stable versions of the
;; library registered as distinct projects ie clojars repos, with their
;; distinctness reflected in distinct names. Other option is to automate
;; some inlining mechanism circumventing dependency management
;; altogether. Vexsome.

;; obvious missing functions

(defn take-until [pred xs]
  (lazy-seq
    (when-let [s (seq xs)]
      (if (pred (first s))
        [(first s)]
        (cons (first s) (take-until pred (rest s)))))))

;; zippers ----------------------------------------------

(defn map-entry? [x]
  (instance? clojure.lang.MapEntry x))

(defn standard-branch? [x] (coll? x))

(defn standard-children [x] (seq x))

(defn standard-make-node [x kids]
  (cond ;; future optimizations ahoy
    (map-entry? x) (vec kids)
    (seq? x)       kids
    :else          (into (empty x) kids)))

;; map entries still weird, have to be careful
(defn standard-zip [x]
  (zip/zipper
    standard-branch?
    standard-children
    standard-make-node
    x))

(defn zip-move-over-right [loc]
  (if-let [rnxt (zip/right loc)]
    rnxt
    (if-let [up (zip/up loc)]
      (recur up)
      nil)))

(defn loc-depth [loc]
  (count (zip/path loc)))

;; here's a great place for pattern matching. oh well.
;; and lots of other things, this is a hobbled version
;; of levelspec.
(defn level-pred [levelspec]
  (cond
    (number? levelspec)
    (fn [loc]
      (= levelspec (loc-depth loc)))
    
    (sequential? levelspec)
    (let [levelspec' (replace {:infinity Double/PositiveInfinity
                               :inf Double/PositiveInfinity}
                       levelspec)
          [min-lvl max-lvl]
          (case (count levelspec')
            1 (cons 0 levelspec')
            2 levelspec)]
      (fn [loc]
        (<= min-lvl (loc-depth loc) max-lvl)))))

;; this is stupid, we need pattern matching of some sort
;; at least. Is the clojure implementation really incompatible with
;; clojure-clr?
;; a yak shave we don't have time for now. Important, tho.


(defn rewrite
  ;; eager version for now. rewrite* or something for lazy.
  ;; on the other hand for kind of annoying reasons rewrite*
  ;; would be difficult.
  ;; Restricting it to certain levels would be easier, tho
  ([x pred f levelspec]
     ;; this needs a lot more optimization. should be 
     ;; able to deal with infinitely deep trees etc
     (let [lp (level-pred levelspec)]
       (loop [loc (standard-zip x)]
         (if (zip/end? loc)
           (zip/root loc)
           (let [n (zip/node loc)]
             (if (and (lp loc) (pred n))
               (let [loc' (zip/edit loc f)
                     loc'' (zip-move-over-right loc')]
                 (if loc''
                   (recur loc'')
                   (zip/root loc')))
               (recur (zip/next loc))))))))
  ([x pred f] 
     (loop [loc (standard-zip x)]
       (if (zip/end? loc)
         (zip/root loc)
         (let [n (zip/node loc)]
           (if (pred n)
             (let [loc' (zip/edit loc f)
                   loc'' (zip-move-over-right loc')]
               (if loc''
                 (recur loc'')
                 (zip/root loc')))
             (recur (zip/next loc))))))))

(declare fixed-point)

(defn rewrite-repeated
  ([x pred f]
     (fixed-point #(rewrite % pred f) x))
  ([x pred f levelspec]
     (fixed-point #(rewrite % pred f levelspec) x)))

;; cases, or whatever ---------------------------------



;; fixed point ----------------------------------------

(defn fixed-point-seq [f x]
  ((fn step [x]
     (cons x
       (lazy-seq
         (let [x' (f x)]
           (when (not= x x')
             (step x'))))))
   x))

(defn fixed-point [f x]
  (loop [x x, x' (f x)]
    (if (= x x')
      x
      (recur x' (f x)))))

;; scan -------------------------------------------

(defn scan [x]
  (tree-seq standard-branch? standard-children x))

;; fix reflection ----------------------------------

(declare apply-kw)

(defn reflection-type? [x]
  (or
    (instance? clojure.reflect.Method x)
    (instance? clojure.reflect.Field x)
    (instance? clojure.reflect.Property x)
    (instance? clojure.reflect.Constructor x)))

(defn qwik-reflect [x & {:as opts0}]
  (let [opts (merge {:ancestors true} opts0)]
    (rewrite-repeated
      (apply-kw clojure.reflect/reflect x opts)
      reflection-type?
      #(into {:reflection-type (type %)} (seq %)))))

(declare map-keys)

(defn qwik-members [x & {:as opts0}]
  (let [opts (merge {:ancestors true} opts0)]
    (map-keys
      {clojure.reflect.Method :methods
       clojure.reflect.Field :fields
       clojure.reflect.Property :properties
       clojure.reflect.Constructor :constructors}
      (group-by
        :reflection-type
        (:members (apply-kw qwik-reflect x opts))))))

;; basic map ops ------------------------------------------

(defn submap?
  [sub-map m]
  (every?
    (fn [^clojure.lang.MapEntry e]
      (= e (find m (key e))))
    sub-map))

;; from https://github.com/runa-dev/kits/blob/master/src/kits/homeless.clj
(defn apply-kw
  "Like apply, but f take kw-args.  The last arg to apply-kw is
   a map of the kw-args to pass to f.
  EXPECTS: {:pre [(map? (last args))]}"
  [f & args]
  (apply f (apply concat
             (butlast args) (last args))))



;;; BEGIN ripped directly from https://github.com/runa-dev/kits/blob/master/src/kits/map.clj
;;; Mapping and Filtering Over Maps

(defn map-values
  "Apply a function on all values of a map and return the corresponding map (all
   keys untouched)"
  [f m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (assoc! out-m k (f v)))
        (transient (empty m))
        m))))

(defn map-keys
  "Apply a function on all keys of a map and return the corresponding map (all
   values untouched)"
  [f m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (assoc! out-m (f k) v))
        (transient (empty m))
        m))))

(defn map-values
  "Apply a function on all values of a map and return the corresponding map (all
   keys untouched)"
  [f m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (assoc! out-m k (f v)))
        (transient (empty m))
        m))))

(defn filter-map
  "Given a predicate like (fn [k v] ...) returns a map with only entries that
   match it."
  [pred m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (if (pred k v)
                     (assoc! out-m k v)
                     out-m))
        (transient (empty m))
        m))))

(defn filter-by-key
  "Given a predicate like (fn [k] ...) returns a map with only entries with keys
   that match it."
  [pred m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (if (pred k)
                     (assoc! out-m k v)
                     out-m))
        (transient (empty m))
        m))))

(defn filter-by-val
  "Given a predicate like (fn [v] ...) returns a map with only entries with vals
   that match it."
  [pred m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (if (pred v)
                     (assoc! out-m k v)
                     out-m))
        (transient (empty m))
        m))))

(defn map-over-map
  "Given a function like (fn [k v] ...) returns a new map with each entry mapped
   by it."
  [f m]
  (when m
    (persistent!
      (reduce-kv (fn [out-m k v]
                   (apply assoc! out-m (f k v)))
        (transient (empty m))
        m))))

;;; END ripped directly from https://github.com/runa-dev/kits/blob/master/src/kits/map.clj

;; protocol introspection, adapted from http://maurits.wordpress.com/2011/01/13/find-which-protocols-are-implemented-by-a-clojure-datatype/

(defn protocol? [maybe-p] ;; awfuly stupid
  (boolean (:on-interface maybe-p)))

(defn all-protocols 
  ([] (all-protocols *ns*))
  ([ns]
     (filter #(protocol? @(val %))
       (ns-publics ns))))

(defn implemented-protocols
  ([x] (implemented-protocols x *ns*))
  ([x ns]
     (filter #(satisfies? @(val %) x) (all-protocols ns))))

;; macros straight from 1.5

(defmacro as->
  "Binds name to expr, evaluates the first form in the lexical context
  of that binding, then binds name to that result, repeating for each
  successive form, returning the result of the last form."
  {:added "1.5"}
  [expr name & forms]
  `(let [~name ~expr
         ~@(interleave (repeat name) forms)]
     ~name))


(defmacro cond->
  "Takes an expression and a set of test/form pairs. Threads expr (via ->)
  through each form for which the corresponding test
  expression is true. Note that, unlike cond branching, cond-> threading does
  not short circuit after the first true test expression."
  {:added "1.5"}
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if ~test (-> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro cond->>
  "Takes an expression and a set of test/form pairs. Threads expr (via ->>)
  through each form for which the corresponding test expression
  is true.  Note that, unlike cond branching, cond->> threading does not short circuit
  after the first true test expression."
  {:added "1.5"}
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if ~test (->> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro as->
  "Binds name to expr, evaluates the first form in the lexical context
  of that binding, then binds name to that result, repeating for each
  successive form, returning the result of the last form."
  {:added "1.5"}
  [expr name & forms]
  `(let [~name ~expr
         ~@(interleave (repeat name) forms)]
     ~name))

(defmacro some->
  "When expr is not nil, threads it into the first form (via ->),
  and when that result is not nil, through the next etc"
  {:added "1.5"}
  [expr & forms]
  (let [g (gensym)
        pstep (fn [step] `(if (nil? ~g) nil (-> ~g ~step)))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep forms))]
       ~g)))

(defmacro some->>
  "When expr is not nil, threads it into the first form (via ->>),
  and when that result is not nil, through the next etc"
  {:added "1.5"}
  [expr & forms]
  (let [g (gensym)
        pstep (fn [step] `(if (nil? ~g) nil (->> ~g ~step)))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep forms))]
       ~g)))
