(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su]
            [unity.reflect-utils :as ru]
            [clojure.string :as string]
            [clojure.set :as sets])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

(declare hydration-database hydrate)

(defmacro cast-as [x type]
  (let [xsym (with-meta (gensym "caster_") {:tag type})]
    `(let [~xsym ~x] ~xsym)))

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn type? [x]
  (instance? System.MonoType x))

(defn nice-keyword [s]
  (keyword
    (clojure.string/lower-case
      (camels-to-hyphens (name s)))))

(defn ensure-type [t]
  (cond
    (symbol? t) (resolve t)
    (type? t) t
    :else (throw
            (ArgumentException.
              (str "Expects symbol or type, instead (type t) = "
                (type t))))))

(defn type-symbol [^System.MonoType t]
  (symbol (.FullName t)))

(defn type-symbol? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (type? y)))))
 
(defn ensure-type-symbol [x]
  (cond
    (symbol? x) x
    (type? x) (type-symbol x)
    :else (throw
            (ArgumentException.
              (str "Expects symbol or type, instead (type t) = "
                (type x))))))

(defn component-type-symbol? [x]
  (boolean
    (and (type-symbol? x)
      (isa? (resolve x)
        UnityEngine.Component))))

;; boy schema would be nice
(defn valid-hdb? [hdb]
  (and
    (map? (:type-flags-to-type-symbols hdb))
    (when-let [hs (:hydraters hdb)]
      (every? type-symbol? (keys hs)))
    (when-let [hffs (:hydration-form-fns hdb)]
      (every? (some-fn symbol? keyword?) (keys hffs)))
    (when-let [sts (:setters hdb)]
      (every? (some-fn symbol? keyword?) (keys sts)))))
 
(defn keyword-for-type [t]
  (nice-keyword
    (.Name ^System.MonoType
      (ensure-type t))))

(defn keyword-for-type-symbol [ts]
  (assert (type-symbol? ts))
  (keyword-for-type ts))

;; generate more if you feel it worth testing for
(defn keys-for-setable [{n :name}]
  [(nice-keyword n)])

(defn type-for-setable [{typ :type}]
  typ) ;; good enough for now

;; TYPE HINT this
(defn extract-property [{:keys [declaring-class
                                name]}]
  (first
    (filter
      (fn [^System.Reflection.PropertyInfo p]
        (= (.Name p) (clojure.core/name name)))
      (.GetProperties ^System.MonoType (resolve declaring-class)))))

(defn setable-properties [typ]
  (filter
    #(and
       (extract-property %) ;; this is a cop-out, isolate circumstances in which this would return nil later
       (.CanWrite (extract-property %)))
    (ru/properties (ensure-type typ))))

(defn setable-fields [typ]
  (->> typ
    ensure-type
    ru/fields
    (filter
      (fn [{fs :flags}]
        (and
          (:public fs)
          (not (:static fs)))))))

(defn dedup-by [f coll]
  (map peek (vals (group-by f coll))))

(defn setables [typ]
  (dedup-by :name
    (concat
      (setable-fields typ)
      (setable-properties typ))))

(defn hydration-form [hdb type vsym]
  (assert (symbol? type))
  (assert (valid-hdb? hdb))
  (if-let [hff (get-in hdb [:hydration-form-fns type])]
    (hff vsym)
    `(cast-as ~vsym ~type)))

;; insert converters etc here if you feel like it
(defn setter-key-clauses [hdb targsym typ vsym]
  (assert (valid-hdb? hdb))
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables typ)
            :let [styp (type-for-setable setable)
                  vhyd (hydration-form hdb styp vsym)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n) (cast-as ~vhyd typ))]))))

(defn setter-reducing-fn-form [hdb ^System.MonoType typ]
  (assert (valid-hdb? hdb))
  (let [ksym (gensym "spec-key_")
        vsym (gensym "spec-val_")
        targsym (with-meta (gensym "targ")
                  {:tag typ})
        skcs (setter-key-clauses hdb targsym typ vsym)
        fn-inner-name (symbol (str "setter-fn-for-" typ))]
    `(fn ~fn-inner-name ~[targsym ksym vsym] 
       (case ~ksym
         ~@skcs
         ~targsym))))

(defn setter-form [hdb typ]
  (assert (valid-hdb? hdb))
  (let [typ      (ensure-type-symbol typ)
        targsym  (with-meta (gensym "setter-target") {:tag typ})
        specsym  (gensym "spec")
        sr       (setter-reducing-fn-form hdb typ)]
    `(fn [~targsym spec#]
       (reduce-kv
         ~sr
         ~targsym
         spec#))))

(defn generate-setter [hdb type]
  (assert (valid-hdb? hdb))
  (eval (setter-form hdb type)))

(defn all-component-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat #(.GetTypes %))
    (filter #(isa? % UnityEngine.Component))))

(defn all-component-type-symbols []
  (map type-symbol (all-component-types)))

(defn all-value-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat
      (fn [^System.AppDomain ad]
        (.GetTypes ad)))
    (filter
      (fn [^System.MonoType t]
        (.IsValueType t)))))

(defn all-value-type-symbols []
  (map type-symbol (all-value-types)))

(defn expand-map-to-type-kws [m]
  (merge m
    (-> m
      (mu/filter-keys type-symbol?)
      (mu/map-keys keyword-for-type-symbol))))

(defn build-setters [hdb types]
  (assert (valid-hdb? hdb))
  (expand-map-to-type-kws
    (zipmap types
      (map #(generate-setter hdb %)
        types))))

(defn build-hydration-database [hdb, types]
  (assert (valid-hdb? hdb))
  (assert (every? symbol? types))
  (mu/merge-in hdb [:setters]
    (build-setters hdb types)))

(defn refresh-hydration-database []
  (swap! hydration-database
    (fn [db]
      (build-hydration-database db
        (all-component-type-symbols)))))

(comment ;; this works well, but each type takes about 500
         ;; milliseconds, and there are 80 built-in component types
         ;; alone. hrm. would it be that slow if we did it as a macro?
  (refresh-hydration-database))
;;; fdf

(defn register-component [type]
  (let [s (generate-setter type)]
    (swap! hydration-database ;; maybe it should be an agent
      (fn [db]
        (assoc db type s)))))

;; ============================================================
;; core hydraters
;; ============================================================

(defn hydraters
  ([] (hydraters @hydration-database))
  ([hdb] (:hydraters hdb)))

(defn init-game-obj ^UnityEngine.GameObject [spec]
  (let [{:keys [name]} spec]
    (if name
      (UnityEngine.GameObject. ^String name)
      (UnityEngine.GameObject.))))

(defn hydration-type-symbol [hdb x]
  ((:type-flags-to-type-symbols hdb) x))

(defn init-component [^UnityEngine.GameObject obj, type-symbol]
  (.AddComponent obj ^String (name type-symbol)))

(defn component-hydration-type-symbol [k]
  (let [ht (hydration-type-symbol k)]
    (if (component-type-symbol? ht)
      ht
      nil)))

;; need a map from keywords to type-symbols etc
(defn game-object-hydrater [spec]
  (let [obj (init-game-obj spec)
        hs (hydraters)]
    (reduce-kv
      (fn [_, k, cspec]
        (when-let [t (component-hydration-type-symbol k)]
          ((hs t)
           (init-component obj t)
           cspec)))
      nil
      spec)
    obj))

;; ============================================================
;; some other setter defs
;; ============================================================


;; vector things
(defmacro def-vectorish-hydrater [name type field-args]
  (let [argsym (with-meta (gensym "arg_") {:tag type})]
    `(defn ~name ~(with-meta [argsym]
                    {:tag type})
       (-> (cond
             (instance? ~type ~argsym)
             ~argsym

             (vector? ~argsym)
             (let [[~@field-args] ~argsym]
               (new ~type ~@field-args))
             
             :else
             (let [{:keys [~@field-args]
                    :or ~(zipmap field-args (repeat 0))} ~argsym]
               (new ~type ~@field-args)))
         (cast-as ~type)))))

(def-vectorish-hydrater vec2-hyd, UnityEngine.Vector2, [x y])

(def-vectorish-hydrater vec3-hyd, UnityEngine.Vector3, [x y z])

(def-vectorish-hydrater vec4-hyd, UnityEngine.Vector4, [x y z w])

(def-vectorish-hydrater quat-hyd, UnityEngine.Quaternion, [x y z w])

;; ============================================================
;; hydrate
;; ============================================================

(defn get-type-flag [x]
  (cond
    (map? x)    (:type x)
    (vector? x) (case (count x) ;; this is stupid
                  2 'UnityEngine.Vector2
                  3 'UnityEngine.Vector3
                  4 'UnityEngine.Vector4
                  nil)))

;; (defn hydrate ;; this should have a hdb argument :-(
;;   ([spec] (hydrate spec (get-type-flag spec)))
;;   ([spec type-flag]
;;      (when-let [hdr ((hydraters)
;;                      (hydration-type-symbol
;;                        @hydration-database
;;                        type-flag))]
;;        (hdr spec))))

;; ============================================================
;; the hydration database itself
;; ============================================================

;; (def hydration-database
;;   (atom 
;;     {:hydration-form-fns
;;      (->
;;        `{UnityEngine.GameObject    game-object-hydrater
;;          ;; UnityEngine.Vector2    vec2-hyd
;;          ;; UnityEngine.Vector3    vec3-hyd
;;          ;; UnityEngine.Vector4    vec4-hyd
;;          ;; UnityEngine.Quaternion quat-hyd
;;          }
;;        (mu/map-vals
;;          (fn [fsym]
;;            (fn [vsym]
;;              `(~fsym ~vsym)))))
;;      ;; :setters {}
;;      :hydraters {}
;;      :type-flags-to-type-symbols {}}
;;     :validator valid-hdb?))
 
;; (defmacro refresh-hydration-database-as-a-macro
;;   ([] `(refresh-hydration-database-as-a-macro
;;          ~(vec (take 3 (all-component-type-symbols)))))
;;   ([type-symbols]
;;      (assert (coll? type-symbols))
;;      (assert (every? type-symbol? type-symbols))
;;      (let [hdb       @hydration-database
;;            sfs       (zipmap type-symbols
;;                        (map #(setter-form hdb %)
;;                          type-symbols))
;;            vsym   (gensym "vsym_")
;;            hforms (zipmap
;;                     (map (fn [ts] `(quote ~ts)) type-symbols)
;;                     (for [t type-symbols]
;;                       (if-let [hffn (get-in hdb [:hydration-form-fns t])]
;;                         `(fn ~(with-meta [vsym] {:tag t})
;;                            ~(hffn vsym))
;;                         (if-let []
;;                           ))))]
;;        `(swap! hydration-database
;;           (fn [hdb#]
;;             (merge-in hdb# [:hydraters]
;;               (expand-map-to-type-kws
;;                 ~hforms)))))))

(defn component-hydrater-form-fn [hdb type-symbol]
  (let [targsym  (with-meta (gensym "setter-target") {:tag type-symbol})
        specsym  (gensym "spec")
        sr       (setter-reducing-fn-form hdb type-symbol)]
    `(fn [^UnityEngine.GameObject obj#, spec#]
       (let [~targsym (.AddComponent obj# ~type-symbol)]
         (reduce-kv
           ~sr
           ~targsym
           spec#)))))

(defn hydrater-form [hdb type-symbol]
  (assert type-symbol? type-symbol)
  (assert valid-hdb? hdb)
  (cond ;; lot of redundancy here, don't like it
    (get-in hdb [:hydration-form-fns type-symbol])
    (let [vsym (gensym)]
      `(fn [~vsym]
         ~(hydration-form hdb type-symbol vsym)))
    
    (component-type-symbol? type-symbol)
    (component-hydrater-form-fn hdb type-symbol)

    :else
    'identity))

(defmacro refresh-hydration-database-as-a-macro
  ([] `(refresh-hydration-database-as-a-macro
         ~(vec (take 3 (all-component-type-symbols)))))
  ([type-symbols]
     (assert (coll? type-symbols))
     (assert (every? type-symbol? type-symbols))
     (let [hdb @hydration-database
           hfm (zipmap
                 (map (fn [ts] `(quote ~ts))
                   type-symbols)
                 (map #(hydrater-form hdb %)
                   type-symbols))]
       `(swap! hydration-database
          (fn [hdb#]
            (mu/merge-in hdb# [:hydraters]
              ~hfm))))))

(comment
  (refresh-hydration-database-as-a-macro))
;; ============================================================
;; ============================================================
;; second attempt
;; ============================================================
;; ============================================================


;; ============================================================
;; populater forms
;; ============================================================

(defn populater-key-clauses [targsym typ vsym]
  (assert (type-symbol? typ))
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables (ensure-type typ))
            :let [styp (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n)
               (-> (populate! ~vsym ~styp)
                 (cast-as ~styp)))]))))

(defn populater-reducing-fn-form [typ]
  (assert (type-symbol? typ))
  (let [ksym (gensym "spec-key_")
        vsym (gensym "spec-val_")
        targsym (with-meta (gensym "targ_") {:tag typ})
        skcs (populater-key-clauses targsym typ vsym)
        fn-inner-name (symbol (str "populater-fn-for_" typ))]
    `(fn ~fn-inner-name ~[targsym ksym vsym] 
       (case ~ksym
         ~@skcs
         ~targsym))))

(defn populater-form [typ]
  (assert (type-symbol? typ))
  (let [targsym  (with-meta (gensym "populater-target_") {:tag typ})
        specsym  (gensym "spec_")
        sr       (populater-reducing-fn-form typ)]
    `(fn [~targsym spec#]
       (reduce-kv
         ~sr
         ~targsym
         spec#))))

;; ============================================================
;; hydrater-forms
;; ============================================================

(defn hydrater-key-clauses [targsym typ vsym]
  (assert (type-symbol? typ))
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables (ensure-type typ))
            :let [styp (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n)
               (cast-as (hydrate ~vsym ~styp)
                 ~styp))]))))

(defn hydrater-reducing-fn-form [typ]
  (assert (type-symbol? typ))
  (let [ksym (gensym "spec-key_")
        vsym (gensym "spec-val_")
        targsym (with-meta (gensym "targ_") {:tag typ})
        skcs (hydrater-key-clauses targsym typ vsym)
        fn-inner-name (symbol (str "hydrater-fn-for-" typ))]
    `(fn ~fn-inner-name ~[targsym ksym vsym] 
       (case ~ksym
         ~@skcs
         ~targsym))))

(defn constructors-spec [type]
  (assert (type? type))
  (set (map :parameter-types (ru/constructors type))))

;; janky + reflective 4 now. Need something to disambiguate dispatch
;; by argument type rather than arity
(defn constructor-application-count-clauses [typ cvsym cspec]
  ;; cspec should be:
  ;; #{[type...]...}
  (assert (type-symbol? typ))
  (let [arities (set (map count cspec))
        dstrsyms (take (max arities)
                   (repeatedly gensym))]
    (apply concat
      (for [cnt arities
            :let [args (vec (take cnt dstrsyms))]]
        [cnt
         `(let [~args ~cvsym]
            (new ~typ ~@args))]))))

;; can make the following more optimal if it becomes an issue
(defn constructor-application-form [typ cvsym cspec]
  (assert (symbol? cvsym))
  (let [capkc (constructor-application-count-clauses typ cvsym cspec)]
    `(case (count ~cvsym)
       ~@capkc
       (throw
         (Exception.
           "Unsupported constructor arity")))))

(defn hydrater-init-form [typ specsym cspec]
  (assert (type-symbol? typ))
  (assert (symbol? specsym))
  (let [cvsym  (gensym "constructor-vec_")
        capf   (constructor-application-form typ cvsym cspec)]
    `(if-let [~cvsym (constructor-vec ~specsym)]
       ~capf
       ~(if (some #(= 0 (count %)) cspec) 
          `(new ~typ)
          `(throw
             (Exception. 
               "hydration init requires constructor-vec")))))) ;; find some better exception class

;; some of the tests here feel redundant with those in hydrate
(defn hydrater-form [typ]
  (assert (type-symbol? typ))
  (let [specsym  (gensym "spec_")
        cspec    (constructors-spec (ensure-type typ))
        sr       (hydrater-reducing-fn-form typ)
        initf    (hydrater-init-form typ specsym cspec)
        initsym  (with-meta (gensym "hydrater-target_") {:tag typ})
        capf     (constructor-application-form typ specsym cspec)]
    `(fn [~specsym]
       (cond
         (instance? ~typ ~specsym)
         ~specsym

         (vector? ~specsym)
         ~capf

         (map? specsym)
         (let [~initsym ~initf]
           (reduce-kv
             ~sr
             ~initsym
             ~specsym))

         :else
         (throw (Exception. "Unsupported hydration spec"))))))

(defn resolve-type-flag [tf]
  (if (type? tf)
    tf
    ((:type-flags->types @hydration-database) tf)))

;; need to expand this for non-component game object members
;; also need to use constructor logic if that's a thing
(defn hydrate-game-object ^UnityEngine.GameObject [spec]
  (reduce-kv
    (fn [^UnityEngine.GameObject obj, k, v]
      (when-let [^System.MonoType t (resolve-type-flag k)]
        (.AddComponent obj t))
      obj)
    (if-let [n (:name spec)]
      (UnityEngine.GameObject. n)
      (UnityEngine.GameObject.))
    spec))

;; ============================================================
;; establish database
;; ============================================================

(defn populater-form-macro-map [tsyms]
  (->> tsyms
    (map (juxt
           identity
           #(try ;; total hack. Problem with reflect and UnityEngine.Component
              (populater-form %)
              (catch Exception e nil))))
    (filter second)
    (into {})))

(defmacro establish-component-populaters-mac [hdb]
  (let [cpfmf  (->>
                 ;;(all-component-type-symbols)
                 '[UnityEngine.Transform]
                 populater-form-macro-map)]
    `(let [hdb# ~hdb
           cpfm# ~cpfmf]
       (mu/merge-in hdb# [:populaters] cpfm#))))

(defmacro establish-value-type-populaters-mac [hdb]
  (let [vpfmf   (->>
                  ;;(all-value-type-symbols)
                  '[UnityEngine.Vector3] 
                  populater-form-macro-map)]
    `(let [hdb# ~hdb
           vpfm# ~vpfmf]
       (mu/merge-in hdb# [:populaters] vpfm#))))

(defmacro establish-value-type-hydraters-mac [hdb]
  (let [vts   (all-value-type-symbols)
        vpfs  (map hydrater-form vts)
        vpfmf (zipmap vts vpfs)]
    `(let [hdb# ~hdb
           vpfm# ~vpfmf]
       (mu/merge-in hdb# [:populaters] vpfm#))))

(defn establish-type-flags [hdb]
  ;; put something here 
  hdb)

;; mathematica isn't picking this up for some horrible reason
(def default-hydration-database
  (->
    {:populaters {}
     :hydraters {UnityEngine.GameObject hydrate-game-object}
     :type-flags->types {}}
    establish-component-populaters-mac
    establish-value-type-populaters-mac 
    ;;   establish-value-type-hydraters-mac
    establish-type-flags
    ))

(def hydration-database
  (atom default-hydration-database))

;; ============================================================
;; runtime hydration & population
;; ============================================================

(defn get-hydrate-type-flag [spec]
  (cond
    (map? spec)
    (or
      (:type spec)
      UnityEngine.GameObject)

    (vector? spec)
    (case (count spec)
      2 UnityEngine.Vector2
      3 UnityEngine.Vector3
      4 UnityEngine.Vector4)))

(defn hydrate
  ([spec]
     (hydrate spec (get-hydrate-type-flag spec)))
  ([spec, type-flag]
     (if type-flag
       (if-let [t (resolve-type-flag type-flag)]
         (if (or (vector? spec) (map? spec))
           (if-let [hfn (get (@hydration-database :hydraters) t)]
             (hfn spec)
             (throw (Exception. (str "No hydrater found for type " t))))
           (if (instance? t spec)
             spec
             (throw (Exception. (str "spec neither vector, map, nor instance of type " t)))))
         (throw (Exception. (str "No type found for type-flag " type-flag))))
       spec)))

(defn get-populate-type-flag [inst spec]
  (if (map? spec)
    (or (:type spec) (type inst))
    (type inst)))

(defn populate!
  ([inst spec]
     (populate! inst spec
       (get-populate-type-flag inst spec)))
  ([inst spec type-flag]     
     (let [t (resolve-type-flag type-flag)]
       (if-let [cfn (get (@hydration-database :populaters) t)]
         (cfn inst spec)
         (throw Exception.
           (str "No populater found for type-flag "
             type-flag))))))
