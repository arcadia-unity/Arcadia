(ns unity.hydrate
  (:require [unity.internal.map-utils :as mu]
            [unity.reflect :as r]
            [clojure.string :as string]
            [clojure.set :as sets]
            [clojure.edn :as edn]
            clojure.walk
            [clojure.clr.io :as io])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly AssemblyName]
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
  typ) ; good enough for now

(defn extract-property
  ^System.Reflection.PropertyInfo
  [{:keys [declaring-class name]}]
  (first
    (filter
      (fn [^System.Reflection.PropertyInfo p]
        (= (.Name p) (clojure.core/name name)))
      (.GetProperties ^System.MonoType (resolve declaring-class)))))

(defn setable-properties [typ]
  (->>
    (r/properties (ensure-type typ) :ancestors true)
    (filter
      #(and
         (extract-property %) ; this is a cop-out, isolate circumstances
                              ; in which this would return nil later
         (.CanWrite (extract-property %))))))

(defn setable-fields [typ]
  (->> 
    (r/fields (ensure-type typ) :ancestors true)
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

(defn load-assembly
  "Takes string or AssemblyName, returns corresponding Assembly"
  [assembly-name]
  (let [^AssemblyName an
        (cond
          (instance? AssemblyName assembly-name)
          assembly-name
          
          (string? assembly-name)
          (AssemblyName. (cast-as assembly-name String))
          
          :else
          (throw (Exception. "Expects AssemblyName or string")))]
    (System.Reflection.Assembly/Load an)))

(defn all-component-types
  ([] (all-component-types [(load-assembly "UnityEngine")]))
  ([assems]
     (for [^Assembly assem assems
           ^System.MonoType t (.GetTypes assem)
           :when (isa? t UnityEngine.Component)]
       t)))

(defn all-component-type-symbols []
  ;; weirdly, you have to filter. looks redundant but you hit
  ;; something weird, see problematic_typesyms.edn
  (filter resolve
    (map type-symbol (all-component-types))))

(defn all-value-types
  ([] (all-value-types [(load-assembly "UnityEngine")]))
  ([assems]
     (for [^Assembly assem assems
           ^System.MonoType t (.GetTypes assem)
           :when (.IsValueType t)]
       t)))

(defn all-value-type-symbols []
  (filter resolve
    (map type-symbol (all-value-types))))

;; ============================================================
;; populater forms
;; ============================================================

(defn populater-key-clauses
  [{:keys [setables-fn]
    :or {setables-fn setables}
    :as ctx}]
  (mu/checked-keys [[targsym valsym typesym] ctx]
    (apply concat
      (for [{n :name, :as setable} (setables-fn (ensure-type typesym))
            :let [styp (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n)
               (cast-as (hydrate ~valsym ~styp)
                 ~styp))]))))

(defn populater-reducing-fn-form [ctx]
  (mu/checked-keys [[typesym] ctx]
    (let [ksym (gensym "spec-key_")
          vsym (gensym "spec-val_")
          targsym (with-meta (gensym "targ_") {:tag typesym})
          skcs (populater-key-clauses 
                 (assoc ctx
                   :targsym targsym,
                   :valsym vsym))
          fn-inner-name (symbol (str "populater-fn-for_" typesym))]
      `(fn ~fn-inner-name ~[targsym ksym vsym] 
         (case ~ksym
           ~@skcs
           ~targsym)
         ~targsym))))

(defn populater-form
  ([typesym] (populater-form typesym {}))
  ([typesym ctx]
     (let [targsym  (with-meta (gensym "populater-target_") {:tag typesym})
           specsym  (gensym "spec_")
           sr       (populater-reducing-fn-form
                      (mu/lit-assoc ctx typesym))]
       `(fn ~(symbol (str "populater-fn-for_" typesym))
          [~targsym spec#]
          (reduce-kv
            ~sr
            ~targsym
            spec#)))))

;; ============================================================
;; hydrater-forms
;; ============================================================

(defn hydrater-key-clauses
  [{:keys [setables-fn]
    :or {setables-fn setables}
    :as ctx}]
  (mu/checked-keys [[targsym typesym valsym] ctx]
    (apply concat
      (for [{n :name, :as setable} (setables-fn
                                     (ensure-type typesym))
            :let [styp (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n)
               (cast-as (hydrate ~valsym ~styp)
                 ~styp))]))))

(defn hydrater-reducing-fn-form [ctx]
  (mu/checked-keys [[typesym] ctx]
    (let [ksym (gensym "spec-key_")
          valsym (gensym "spec-val_")
          targsym (with-meta (gensym "targ_") {:tag typesym})
          skcs (hydrater-key-clauses
                 (mu/lit-assoc ctx targsym valsym))
          fn-inner-name (symbol (str "hydrater-reducing-fn-for-" typesym))]
      `(fn ~fn-inner-name ~[targsym ksym valsym] 
         (case ~ksym
           ~@skcs
           ~targsym)
         ~targsym))))

(defn constructors-spec [type]
  (set (map :parameter-types (r/constructors type))))

;; janky + reflective 4 now. Need something to disambiguate dispatch
;; by argument type rather than arity
(defn constructor-application-count-clauses
  " ctrspec should be: #{[type...]...}"
  [ctx]
  (mu/checked-keys [[typesym cvsym ctrspec] ctx]
    (let [arities (set (map count ctrspec))
          dstrsyms (take (apply max arities)
                     (repeatedly gensym))]
      (apply concat
        (for [cnt arities
              :let [args (vec (take cnt dstrsyms))]]
          [cnt
           `(let [~args ~cvsym]
              (new ~typesym ~@args))])))))

;; can make the following more optimal if it becomes an issue
(defn constructor-application-form [ctx]
  (mu/checked-keys [[cvsym] ctx]
    (let [capkc (constructor-application-count-clauses ctx)]
      `(case (count ~cvsym)
         ~@capkc
         (throw
           (Exception.
             "Unsupported constructor arity"))))))

(defn constructor-vec [m]
  (:constructor m))

(defn hydrater-init-form [ctx]
  (mu/checked-keys [[typesym specsym ctrspec] ctx]
    (let [cvsym  (gensym "constructor-vec_")
          capf   (constructor-application-form
                   (mu/lit-assoc ctx cvsym))]
      `(if-let [~cvsym (constructor-vec ~specsym)]
         ~capf
         ~(if (some #(= 0 (count %)) ctrspec) 
            `(new ~typesym)
            `(throw
               (Exception. 
                 "hydration init requires constructor-vec"))))))) ;; find some better exception class

;; some of the tests here feel redundant with those in hydrate
(defn hydrater-form
  ([typesym]
     (hydrater-form typesym {}))
  ([typesym
    {:keys [setables-fn]
     :or {setables-fn setables}
     :as ctx0}]
     (let [ctx      (mu/lit-assoc ctx0 setables-fn)
           specsym  (gensym "spec_") 
           ctrspec  (constructors-spec (ensure-type typesym))
           sr       (hydrater-reducing-fn-form
                      (mu/lit-assoc ctx typesym setables-fn))
           initf    (hydrater-init-form
                      (mu/lit-assoc ctx typesym specsym ctrspec))
           initsym  (with-meta (gensym "hydrater-target_") {:tag typesym})
           capf     (constructor-application-form
                      (mu/lit-assoc
                        (assoc ctx :cvsym specsym)
                        typesym specsym ctrspec))]
       `(fn ~(symbol (str "hydrater-fn-for_" typesym))
          [~specsym]
          (cond
            (instance? ~typesym ~specsym)
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
            (throw (Exception. "Unsupported hydration spec")))))))

(defn resolve-type-flag [tf]
  (if (type? tf)
    tf
    ((:type-flags->types @hydration-database) tf)))

(defn populate-game-object! ^UnityEngine.GameObject
  [^UnityEngine.GameObject gob spec]
  (reduce-kv
    (fn [^UnityEngine.GameObject obj, k, vspecs]
      (when-let [^System.MonoType t (resolve-type-flag k)]
        (if (= UnityEngine.Transform t)
          (doseq [cspec vspecs]
            (populate! (.GetComponent obj t) cspec t))
          (doseq [cspec vspecs]
            (populate! (.AddComponent obj t) cspec t))))
      obj)
    gob
    spec))

;; need to expand this for non-component game object members
;; also need to use constructor logic if that's a thing
;; basically make this match API of hydrater-form
(defn hydrate-game-object ^UnityEngine.GameObject [spec]
  (populate-game-object!
    (if-let [^String n (:name spec)]
      (UnityEngine.GameObject. n)
      (UnityEngine.GameObject.))
    spec))


;; ============================================================
;; establish database
;; ============================================================

;; ------------------------------------------------------------
;; qwik and dirty way
;; ------------------------------------------------------------

(def setables-path
  "Assets/Clojure/Libraries/unity/setables.edn")
(def problematic-typesyms-path "Assets/Clojure/Libraries/unity/problematic_typesyms.edn")

(defn squirrel-setables-away [typesyms
                              setables-path 
                              problematic-typesyms-path]
  (let [problematic-typesyms (atom [])
        setables-map (clojure.walk/prewalk
                       (fn [x] 
                         (if (symbol? x)
                           (name x)
                           x))
                       (into {}
                         (filter second
                           (for [tsym typesyms]
                             [tsym,
                              (try ;; nasty nasty nasty
                                (vec (setables tsym))
                                (catch System.ArgumentException e
                                  (swap! problematic-typesyms conj tsym)
                                  nil))]))))]
    (with-open [f (io/output-stream setables-path)]
      (spit f
        (pr-str
          setables-map)))
    (with-open [f (io/output-stream problematic-typesyms-path)]
      (spit f
        (pr-str
          @problematic-typesyms)))
    (println "squirreling successful")))

;; then evaluate the following, once, and wait a bit:
;; (squirrel-setables-away
;;   (concat
;;     (all-component-type-symbols)
;;     (all-value-type-symbols))
;;   setables-path
;;   problematic-typesyms-path)

;; then you can retrieve-it like so:

(defn retrieve-squirreled-setables [setables-path]
  (clojure.walk/prewalk
    (fn [x]
      (if (string? x)
        (symbol x)
        x))
    (edn/read-string
      (slurp setables-path))))

;; and here's the cake:
(def setables-cache (atom nil))

(defn refresh-setables-cache []
  (reset! setables-cache
    (retrieve-squirreled-setables setables-path)))

(refresh-setables-cache)

(def setables-cache
  (retrieve-squirreled-setables setables-path))

(defn setables-cached [typesym]
  (@setables-cache typesym))

(def problem-log (atom []))

(defn form-macro-map [f tsyms ctx]
  (->> tsyms
    (map
      (fn [tsym]
        [tsym,
         (try ;; total hack
           (f tsym ctx)
           (catch Exception e
             (do
               (swap! problem-log conj tsym)
               nil)))]))
    (filter second)
    (into {})))

(defmacro establish-component-populaters-mac [hdb]
  (let [cpfmf (form-macro-map
                populater-form
                ;[UnityEngine.Transform UnityEngine.BoxCollider]
                (all-component-type-symbols)
                {:setables-fn
                 setables-cached
                ;setables
                 })]
    `(let [hdb# ~hdb
           cpfm# ~cpfmf]
       (mu/merge-in hdb# [:populaters] cpfm#))))

(defmacro establish-value-type-populaters-mac [hdb]
  (let [vpfmf (form-macro-map
                populater-form
                ;'[UnityEngine.Vector3] 
                (all-value-type-symbols)
                {:setables-fn
                 setables-cached
                 ;setables
                 })]
    `(let [hdb# ~hdb
           vpfm# ~vpfmf]
       (mu/merge-in hdb# [:populaters] vpfm#))))

;; probably faster compile if you consolidate with populaters
(defmacro establish-value-type-hydraters-mac [hdb]
  (let [vpfmf (form-macro-map
                hydrater-form
                ;'[UnityEngine.Vector3]
                (all-value-type-symbols) ;; 231
                {:setables-fn
                 setables-cached
                 ;setables
                 })]
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
    {:populaters {UnityEngine.GameObject #'populate-game-object!}
     :hydraters {UnityEngine.GameObject #'hydrate-game-object}
     :type-flags->types {}}
    establish-component-populaters-mac
    establish-value-type-populaters-mac 
    establish-value-type-hydraters-mac
    establish-type-flags
    identity))

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
