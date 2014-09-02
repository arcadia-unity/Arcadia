(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su]
            [unity.reflect-utils :as ru]
            [clojure.string :as string]
            [clojure.set :as sets])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

(declare hydration-database hydrate populate!)

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

(defn keyword-for-type [t]
  (nice-keyword
    (.Name ^System.MonoType
      (ensure-type t))))

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
       (extract-property %) ; this is a cop-out, isolate circumstances
                            ; in which this would return nil later
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

(comment ;; this works well, but each type takes about 500
         ;; milliseconds, and there are 80 built-in component types
         ;; alone. hrm. would it be that slow if we did it as a macro?
  (refresh-hydration-database))

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
               (cast-as (hydrate ~vsym ~styp)
                 ~styp))]))))

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
         ~targsym)
       ~targsym)))

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
         ~targsym)
       ~targsym)))

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
        dstrsyms (take (apply max arities)
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

(defn constructor-vec [m]
  (:constructor m))

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

         (map? ~specsym)
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
;; basically make this match API of hydrater-form
(defn hydrate-game-object ^UnityEngine.GameObject [spec]
  (reduce-kv
    (fn [^UnityEngine.GameObject obj, k, v]
      (when-let [^System.MonoType t (resolve-type-flag k)]
        ;; do something more general if types besides Transform
        ;; have same prob:
        (let [c (if (= UnityEngine.Transform t)
                  (.GetComponent obj t)
                  (.AddComponent obj t))]
          (populate! c v t)))
      obj)
    (if-let [^String n (:name spec)]
      (UnityEngine.GameObject. n)
      (UnityEngine.GameObject.))
    spec))

;; ============================================================
;; establish database
;; ============================================================

(defn form-macro-map [f tsyms]
  (->> tsyms
    (map (juxt
           identity
           #(try ;; total hack. Problem with reflect and UnityEngine.Component
              (f %)
              (catch Exception e nil))))
    (filter second)
    (into {})))

(defmacro establish-component-populaters-mac [hdb]
  (let [cpfmf  (->>
                 ;;(all-component-type-symbols)
                 '[UnityEngine.Transform]
                 (form-macro-map populater-form))]
    `(let [hdb# ~hdb
           cpfm# ~cpfmf]
       (mu/merge-in hdb# [:populaters] cpfm#))))

(defmacro establish-value-type-populaters-mac [hdb]
  (let [vpfmf   (->>
                  ;;(all-value-type-symbols) ;; thousands
                  '[UnityEngine.Vector3] 
                  (form-macro-map populater-form))]
    `(let [hdb# ~hdb
           vpfm# ~vpfmf]
       (mu/merge-in hdb# [:populaters] vpfm#))))

(defmacro establish-value-type-hydraters-mac [hdb]
  (let [vpfmf (->>
                ;; (all-value-type-symbols) ;; thousands
                '[UnityEngine.Vector3]
                (form-macro-map hydrater-form))]
    `(let [hdb# ~hdb
           vpfm# ~vpfmf]
       (mu/merge-in hdb# [:hydraters] vpfm#))))

(defn establish-type-flags [hdb]
  ;; put something here
  (let [tks (seq
              (set
                (concat
                  (keys (:populaters hdb))
                  (keys (:hydraters hdb)))))]
    (mu/merge-in hdb [:type-flags->types]
      (zipmap
        (map keyword-for-type tks)
        tks))))

;; mathematica isn't picking this up for some horrible reason
(def default-hydration-database
  (->
    {:populaters {}
     :hydraters {UnityEngine.GameObject #'hydrate-game-object}
     :type-flags->types {}}
    establish-component-populaters-mac
    establish-value-type-populaters-mac 
    establish-value-type-hydraters-mac
    establish-type-flags))

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
           spec)
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
       (if (or (vector? spec) (map? spec))
         (if-let [cfn (get (@hydration-database :populaters) t)]
           (cfn inst spec)
           (throw (Exception. (str "No populater found for type " type))))
         (throw (Exception. "spec neither vector nor map"))))))
