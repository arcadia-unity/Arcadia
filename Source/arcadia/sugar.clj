(ns arcadia.sugar
  (:refer-clojure :rename {let clet,
                           when-let cwhen-let,
                           if-let cif-let} :exclude [pmap])
  (:require arcadia.core
            [clojure.spec.alpha :as s]))

(declare process-binding)

(defn- pvec [bvec b val]
  (clet [gvec (gensym "vec__")]
    (loop [ret (-> bvec (conj gvec) (conj val))
           n 0
           bs b
           seen-rest? false]
      (if (seq bs)
        (clet [firstb (first bs)]
          (cond
            (= firstb '&) (recur (process-binding ret (second bs) (list `nthnext gvec n))
                            n
                            (nnext bs)
                            true)
            (= firstb :as) (process-binding ret (second bs) gvec)
            :else (if seen-rest?
                    (throw (new Exception "Unsupported binding form, only :as can follow & parameter"))
                    (recur (process-binding ret firstb  (list `nth gvec n nil))
                      (inc n)
                      (next bs)
                      seen-rest?))))
        ret))))

(defn- pmap [bvec b v]
  (clet [gmap (gensym "map__") ; generated symbol of the map itself (gmap is not a map! it is a symbol)
        gmapseq (with-meta gmap {:tag 'clojure.lang.ISeq})
        defaults (:or b)]
    (loop [ret (-> bvec (conj gmap) (conj v)
                   (conj gmap) (conj `(if (seq? ~gmap) (clojure.lang.PersistentHashMap/create (seq ~gmapseq)) ~gmap))
                   ((fn [ret]
                      (if (:as b)
                        (conj ret (:as b) gmap)
                        ret))))
           bes (reduce
                 (fn [bes entry]
                   (reduce #(assoc %1 %2 ((val entry) %2)) ;; 
                     (dissoc bes (key entry))
                     ((key entry) bes))) 
                 (dissoc b :as :or)
                 {:keys #(if (keyword? %) % (keyword (str %))),
                  :strs str,
                  :syms #(list `quote %)})]
      (if (seq bes)
        (clet [bb (key (first bes))
              bk (val (first bes))
              bv (if (contains? defaults bb)
                   (list `get gmap bk (defaults bb))
                   (list `get gmap bk))]
          (recur (cond
                   (symbol? bb) (-> ret (conj (if (namespace bb) (symbol (name bb)) bb)) (conj bv))
                   (keyword? bb) (-> ret (conj (symbol (name bb)) bv))
                   :else (process-binding ret bb bv))
            (next bes)))
        ret))))

(defmacro ^:private kv-cat [k v]
  `(s/cat :k ~k :v ~v))

(s/def ::props (kv-cat  #{:props} vector?))

(s/def ::strs (kv-cat #{:strs} vector?))

(s/def ::syms (kv-cat #{:syms} vector?))

(s/def ::keys (kv-cat #{:keys} vector?))

;; I suppose this should only work with keys (get)
(s/def ::or (kv-cat #{:or} map?))

(s/def ::as (kv-cat #{:as} simple-symbol?))

(s/def ::with-cmpt (kv-cat #{:with-cmpt} vector?))

(s/def ::cmpt (kv-cat #{:cmpt} map?))

(s/def ::props (kv-cat #{:props} vector?))

(s/def ::obj-binding (kv-cat any? any?))

(s/def ::state (kv-cat #{:state} any?))

(s/def ::get (kv-cat #{:get} map?)) ;; should have some guard against special keys: :as, :syms, :keys, etc

(s/def ::obj-special-binding
  (s/*
    (s/alt
      :as ::as
      :or ::or
      :keys ::keys
      :syms ::syms
      :strs ::strs
      ;; NEW:
      :props ::props
      :with-cmpt ::with-cmpt
      :cmpt ::cmpt
      :state ::state
      :get ::get
      :obj-binding ::obj-binding)))

(s/def ::obj-binding-form
  (s/cat
    :obj any?
    :binding ::obj-special-binding))

(defn- kkv [[k1 {k2 :k, v :v}]] [k1 k2 v])

(defn- tagged [x t]
  (with-meta x (assoc (or (meta x) {}) :tag t)))

(defn- expand-alias [sym]
  (cif-let [ns (get (ns-aliases *ns*) (symbol (.Namespace sym)))]
    (symbol (name (.Name ns)) (name sym))
    sym))

(defn destructuring-form-dispatch [bvec [sym] v]
  (expand-alias sym))

(defmulti destructuring-form destructuring-form-dispatch)

(declare o)

(defmethod destructuring-form 'arcadia.sugar/o [bvec b v]
  (clet [parse (s/conform ::obj-binding-form b)
         _ (when (= ::s/invalid parse)
             (throw (Exception.
                     (str "Invalid arguments to arcadia.sugar/let for arcadia.sugar/o! Spec explaination:\n"
                          (with-out-str (s/explain ::obj-binding-form b))))))
         gsym (gensym "obj__")
         bvec (-> bvec (conj gsym) (conj v))
         kkvs (map kkv (:binding parse))
         rfn (fn rfn [bvec [t k v]]
               (case t
                 :obj-binding (conj bvec k (list '. gsym v))
                 :props (reduce (fn [bvec prop]
                                  (conj bvec prop (list '. gsym prop)))
                          bvec
                          v)
                 :as (conj bvec v gsym)
                 :with-cmpt (reduce (fn [bvec [lhs rhs]]
                                      (clet [form (-> `(arcadia.core/with-cmpt ~gsym [cmpt# ~rhs]
                                                         cmpt#)
                                                      (tagged rhs))]
                                        (process-binding bvec lhs form)))
                              bvec
                              (partition 2 v))
                 :get (process-binding bvec v gsym)
                 (cond
                   (#{:keys :syms :strs} t)
                   (conj bvec {k v} gsym)
                   
                   :else
                   (throw (Exception. (str "form not recognized. key: " k))))))]
    ;; not really, just for now:
    (reduce rfn
      bvec
      kkvs)))

(defmethod destructuring-form 'arcadia.sugar/with-cmpt [bvec b v]
  (clet [gsym (gensym "obj__")]
    (reduce (fn [bvec [lhs rhs]]
              (clet [form (-> `(arcadia.core/with-cmpt ~gsym [cmpt# ~rhs]
                                 cmpt#)
                              (tagged rhs))]
                (process-binding bvec lhs form)))
      (conj bvec gsym v)
      (partition 2 (rest b)))))

(defmethod destructuring-form 'arcadia.sugar/props [bvec b v]
  (clet [gsym (gensym "obj__")]
    (reduce (fn [bvec prop]
              (conj bvec prop (list '. gsym prop)))
      (conj bvec gsym v)
      (rest b))))

(defmethod destructuring-form 'arcadia.sugar/keys [bvec [_ & b] v]
  (conj bvec {:keys (vec b)} v))

(defmethod destructuring-form 'arcadia.sugar/syms [bvec [_ & b] v]
  (conj bvec {:syms (vec b)} v))

(defmethod destructuring-form 'arcadia.sugar/strs [bvec [_ & b] v]
  (conj bvec {:strs (vec b)} v))

(defn- process-binding [bvec b v]
  (cond
    (symbol? b) (-> bvec (conj b) (conj v))
    (vector? b) (pvec bvec b v)
    (map? b) (pmap bvec b v)
    (seq? b) (destructuring-form bvec b v)
    ;; (and (seq? b) (= 'obj (first b))) (pobj bvec b v)
    :else (throw (new Exception (str "Unsupported binding form: " b)))))

(defn- destructure2 [bindings]
  (clet [bentries (partition 2 bindings)
         process-entry (fn [bvec b] (process-binding bvec (first b) (second b)))]
    (if (every? symbol? (map first bentries))
      bindings
      (reduce process-entry [] bentries))))

;; notice that this could easily be an open, extensible system via multimethods

(defmacro let [bindings & body]
  `(clojure.core/let [~@(destructure2 bindings)]
     ~@body))

(defmacro when-let [bindings & body]
  (assert
    (vector? bindings)
    (== 2 (count bindings)))
  (let [form (bindings 0)
        test (bindings 1)]
    `(let [temp# ~test]
       (when temp#
         (let [~form temp#]
           ~@body)))))

(defmacro when-lets [bindings & body]
  (assert
    (vector? bindings)
    (even? (count bindings)))
  (let [[[x y] & rbindings] bindings]
    (if (seq rbindings)
      `(when-let [~x ~y]
         (when-lets [~@rbindings]
           ~@body))
      `(when-let [~x ~y]
         ~@body))))

;; ============================================================
;; imperative niceties

(defmacro sets! [o & assignments]
  (let [osym (gensym "obj__")
        asgns (->> (partition 2 assignments)
                   (map (fn [[lhs rhs]]
                          `(set! (. ~osym ~lhs) ~rhs))))]
    `(let [~osym ~o]
       ~@asgns)))
