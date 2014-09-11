(ns unity.hydrate
  (:require [unity.internal.map-utils :as mu]
            [unity.reflect :as r]
            [clojure.string :as string]
            [clojure.set :as sets]
            [clojure.edn :as edn]
            clojure.walk
            [clojure.clr.io :as io])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly AssemblyName MemberInfo
            PropertyInfo FieldInfo]
           [UnityEngine GameObject Transform]
           System.AppDomain))

(declare hydration-database hydrate populate! dehydrate)

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn type? [x]
  (instance? System.MonoType x))

(defn nice-keyword [s]
  (keyword
    (clojure.string/lower-case
      (camels-to-hyphens (name s)))))

(defn ensure-type ^System.MonoType [t]
  (cond
    (symbol? t) (resolve t)
    (type? t) t
    :else (throw
            (ArgumentException.
              (str "Expects symbol or type, instead (type t) = "
                (type t))))))

(defn type-symbol [^System.MonoType t]
  (symbol (.FullName t)))

(defn ensure-symbol [t]
  (cond
    (symbol? t) t
    (type? t) (type-symbol t)
    :else   (throw
              (ArgumentException.
                (str "Expects symbol or type, instead (type t) = "
                  (type t))))))

(defmacro cast-as [x type] ;; not sure this works
  (let [xsym (with-meta (gensym "caster_") {:tag (ensure-symbol type)})]
    `(let [~xsym ~x] ~xsym)))

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

(defn extract-property ;; this is stupid
  ^System.Reflection.PropertyInfo
  [{:keys [declaring-class name]}]
  (let [^System.MonoType t (resolve declaring-class)]
    (-> (.GetProperties t)
      (filter
        (fn [^System.Reflection.PropertyInfo p]
          (= (.Name p) (clojure.core/name name))))
      first)))

(defn setable-property? [^PropertyInfo p]
  (boolean
    (and
      (not (= "Item" (.Name p)))
      (.CanRead p)
      (.CanWrite p))))

(defn setable-properties [type-or-typesym]
  (let [^System.MonoType t (ensure-type type-or-typesym)
        property->map (var-get #'clojure.reflect/property->map)]
    (map (comp r/reflection-transform property->map)
      (filter setable-property?
        (.GetProperties t)))))

(defn setable-field? [^FieldInfo f]
  (boolean
    (and
      (.IsPublic f)
      (not (.IsStatic f)))))

(defn setable-fields [type-or-typesym]
  (let [^System.Type t (ensure-type type-or-typesym)
        field->map (var-get #'clojure.reflect/field->map)]
    (for [^FieldInfo f (.GetFields t)
          :when (setable-field? f)]
      (r/reflection-transform
       (field->map f)))))

(defn dedup-by [f coll]
  (map peek (vals (group-by f coll))))

(defn setables [typesym]
  (let [t (ensure-type typesym)]
    (dedup-by :name
      (concat
        (setable-fields t)
        (setable-properties t)))))

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

(defn decently-named? [^System.Type t]
  (boolean
    (let [flnm (.FullName t)]
      (not (re-find #"[^a-zA-Z0-9.]" flnm)))))

(defn all-component-types
  ([] (all-component-types [(load-assembly "UnityEngine")]))
  ([assems]
     (for [^Assembly assem assems
           ^System.MonoType t (.GetTypes assem)
           :when (and
                   (isa? t UnityEngine.Component)
                   (decently-named? t))]
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
           :when (and
                   (.IsValueType t)
                   (decently-named? t))]
       t)))

(defn all-value-type-symbols []
  (filter resolve
    (map type-symbol (all-value-types))))

;; ============================================================
;; populater forms
;; ============================================================

;; this is kind of mangled to accommodate value type populaters.
;; clean up later.

;; could break things up into different kinds of context (setable,
;; targsym, and valsym are more specific than field->setter-sym, for
;; example)
(defn value-type-set-clause [setable ctx]
  (mu/checked-keys [[targsym valsym field->setter-sym] ctx
                    [name] setable]
    (let [setable-type (type-for-setable setable)
          setter-sym (field->setter-sym name)
          valsym' (with-meta (gensym) {:tag setable-type})]
      `(let [~valsym' ~valsym]
         (.SetValue ~setter-sym ~targsym ~valsym')))))


(defn populater-key-clauses
  [{:keys [set-clause-fn]
    :as ctx}]
  (mu/checked-keys [[targsym valsym typesym setables-fn] ctx]
    (assert (symbol? typesym))
    (apply concat
      (for [{n :name, :as setable} (setables-fn typesym)
            :let [setable-type (type-for-setable setable)]
            k    (keys-for-setable setable)
            :let [cls  (if set-clause-fn
                          (set-clause-fn setable ctx)
                          `(set! (. ~targsym ~n)
                             (cast-as (hydrate ~valsym ~setable-type)
                               ~setable-type)))]]
        `[~k  ~cls]))))

(defn populater-reducing-fn-form [ctx]
  (mu/checked-keys [[typesym] ctx]
    (let [ksym (gensym "spec-key_")
          valsym (gensym "spec-val_")
          targsym (with-meta (gensym "targ_") {:tag typesym})
          skcs (populater-key-clauses 
                 (mu/lit-assoc ctx targsym, valsym))
          fn-inner-name (symbol (str "populater-fn-for_" typesym))]
      `(fn ~fn-inner-name ~[targsym ksym valsym] 
         (case ~ksym
           ~@skcs
           ~targsym)
         ~targsym))))

(defn get-field->setter-sym [fields]
  (into {}
    (for [f fields]
      [f (with-meta
           (gensym
             (str "FieldInfo-for_" f))
           {:tag 'System.Reflection.FieldInfo})])))

(defn get-setter-inits [typesym field->setter-sym]
  (mapcat
    (fn [[f ss]]
      [ss `(.GetField ~typesym ~(str f))])
    field->setter-sym))

;; not the prettiest
(defn value-populater-form
  ([typesym] (value-populater-form typesym {}))
  ([typesym ctx]
     (let [{:keys [setables-fn]
            :or {setables-fn setables}} ctx
            fields   (map :name (setables-fn typesym))
            field->setter-sym (get-field->setter-sym fields)
            setter-inits (get-setter-inits typesym field->setter-sym)
            targsym  (with-meta (gensym "populater-target_") {:tag typesym})
            specsym  (gensym "spec_")
            sr       (populater-reducing-fn-form
                       (assoc ctx
                         :setables-fn setables-fn
                         :set-clause-fn value-type-set-clause
                         :typesym typesym
                         :field->setter-sym field->setter-sym))]
       `(let [~@setter-inits]
          (fn ~(symbol (str "populater-fn-for_" typesym))
            [~targsym spec#]
            (reduce-kv
              ~sr
              ~targsym
              spec#))))))

(defn populater-form
  ([typesym] (populater-form typesym {}))
  ([typesym ctx]
     (if (.IsValueType (resolve typesym))
       (value-populater-form typesym ctx)
       (let [targsym  (with-meta (gensym "populater-target_") {:tag typesym})
             specsym  (gensym "spec_")
             sr       (populater-reducing-fn-form
                        (mu/lit-assoc ctx typesym))]
         `(fn ~(symbol (str "populater-fn-for_" typesym))
            [~targsym spec#]
            (reduce-kv
              ~sr
              ~targsym
              spec#))))))

;; ============================================================
;; hydrater-forms
;; ============================================================

;; (defn hydrater-key-clauses
;;   [{:keys [setables-fn]
;;     :or {setables-fn setables}
;;     :as ctx}]
;;   (mu/checked-keys [[targsym typesym valsym] ctx]
;;     (assert (symbol? typesym))
;;     (apply concat
;;       (for [{n :name, :as setable} (setables-fn typesym)
;;             :let [styp (type-for-setable setable)]
;;             k  (keys-for-setable setable)]
;;         `[~k (set! (. ~targsym ~n)
;;                (cast-as (hydrate ~valsym ~styp)
;;                  ~styp))]))))

;; (defn hydrater-reducing-fn-form [ctx]
;;   (mu/checked-keys [[typesym] ctx]
;;     (let [ksym (gensym "spec-key_")
;;           valsym (gensym "spec-val_")
;;           targsym (with-meta (gensym "targ_") {:tag typesym})
;;           skcs (hydrater-key-clauses
;;                  (mu/lit-assoc ctx targsym valsym))
;;           fn-inner-name (symbol (str "hydrater-reducing-fn-for-" typesym))]
;;       `(fn ~fn-inner-name ~[targsym ksym valsym] 
;;          (case ~ksym
;;            ~@skcs
;;            ~targsym)
;;          ~targsym))))
 
(defn constructors-spec [type]
  (set
    (conj 
      (map :parameter-types (r/constructors type))
      (when (.IsValueType type) []))))

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


;; abomination. clean up after verifying.
(declare setables-cached)
(defn value-hydrater-form
  ([typesym]
     (value-hydrater-form typesym {}))
  ([typesym
    {:keys [setables-fn]
     :or {setables-fn setables-cached}
     :as ctx0}]
     (let [ctx      (mu/lit-assoc ctx0 setables-fn)
           specsym  (gensym "spec_") 
           ctrspec  (constructors-spec (ensure-type typesym))
           fields   (map :name (setables-fn typesym))
           field->setter-sym (get-field->setter-sym fields)
           setter-inits (get-setter-inits typesym field->setter-sym)
           sr       (populater-reducing-fn-form
                      (assoc ctx
                        :setables-fn setables-fn
                        :set-clause-fn value-type-set-clause
                        :typesym typesym
                        :field->setter-sym field->setter-sym))
           initf    (hydrater-init-form
                      (mu/lit-assoc ctx typesym specsym ctrspec))
           initsym  (with-meta (gensym "hydrater-target_") {:tag typesym})
           capf     (constructor-application-form
                      (mu/lit-assoc
                        (assoc ctx :cvsym specsym)
                        typesym specsym ctrspec))]
       `(let [~@setter-inits]
          (fn ~(symbol (str "hydrater-fn-for_" typesym))
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
              (throw (Exception. "Unsupported hydration spec"))))))))


;; some of the tests here feel redundant with those in hydrate
  
(defn hydrater-form
  ([typesym]
     (hydrater-form typesym {}))
  ([typesym
    {:keys [setables-fn]
     :or {setables-fn setables}
     :as ctx0}]
     (let [ctx (mu/lit-assoc ctx0 setables-fn)]
       (if (.IsValueType (resolve typesym))
         (value-hydrater-form typesym ctx)
         (let [specsym  (gensym "spec_") 
               ctrspec  (constructors-spec (ensure-type typesym))
               sr       (populater-reducing-fn-form
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
                (throw (Exception. "Unsupported hydration spec")))))))))

(defn resolve-type-flag [tf]
  (if (type? tf)
    tf
    ((:type-flags->types @hydration-database) tf)))

(declare hydrate-game-object)

(defn hydrate-game-object-children
  [^UnityEngine.GameObject obj, specs]
  (let [^UnityEngine.Transform trns (.GetComponent obj UnityEngine.Transform)]
    (doseq [spec specs]
      (hydrate-game-object
        (mu/assoc-in-mv spec [:transform 0 :parent] trns)))))

(defn game-object-prepopulate! [^GameObject obj, spec]
  (if-let [t (first (:transform spec))] ;; obsoletes resolve-type-key
    (do (populate! (.GetComponent obj UnityEngine.Transform) t)
        (dissoc spec :transform))
    spec))

;; this doesn't work for other properties yet (eg tag, layer, etc)!
(defn populate-game-object! ^UnityEngine.GameObject
  [^UnityEngine.GameObject gob spec]
  (reduce-kv
    (fn [^UnityEngine.GameObject obj, k, vspecs]
      (if (= :children k)
        (hydrate-game-object-children obj vspecs)
        (if-let [^System.MonoType t (resolve-type-flag k)]
          (if (= UnityEngine.Transform t)
            (doseq [cspec vspecs]
              (populate! (.GetComponent obj t) cspec t))
            (doseq [cspec vspecs]
              (populate! (.AddComponent obj t) cspec t)))))
      obj)
    gob
    spec))

;; generated, then tweaked, then inlined. Apologies for the mess.
(defn hydrate-game-object
  ^UnityEngine.GameObject [spec]
  (cond
    (instance? UnityEngine.GameObject spec) spec
    (vector? spec)
    (case (count spec)
      0 (let [[] spec] (new UnityEngine.GameObject))
      1 (let [[G__539] spec]
          (new UnityEngine.GameObject G__539))
      2 (let [[G__539 G__540] spec]
          (new UnityEngine.GameObject G__539 G__540))
      (throw (Exception. "Unsupported constructor arity")))
    (map? spec)
    (let [^UnityEngine.GameObject obj
          (if-let [constructor-vec_535 (constructor-vec spec)]
            (case (count constructor-vec_535)
              0 (let [[] constructor-vec_535]
                  (new UnityEngine.GameObject))
              1 (let [[G__536] constructor-vec_535]
                  (new UnityEngine.GameObject G__536))
              2 (let [[G__536 G__537] constructor-vec_535]
                  (new UnityEngine.GameObject G__536 G__537))
              (throw (Exception. "Unsupported constructor arity")))
            (new UnityEngine.GameObject))
          spec' (game-object-prepopulate! obj spec)]
      (reduce-kv
        (fn populater-fn-for_UnityEngine.GameObject
          [^UnityEngine.GameObject obj spec-key spec-val]
          (case spec-key
            :is-static
            (set!
              (. ^UnityEngine.GameObject obj isStatic)
              (cast-as
                (hydrate spec-val System.Boolean)
                System.Boolean))
            
            :layer
            (set!
              (. ^UnityEngine.GameObject obj layer)
              (cast-as
                (hydrate spec-val System.Int32)
                System.Int32))
            
            :active
            (set!
              (. ^UnityEngine.GameObject obj active)
              (cast-as
                (hydrate spec-val System.Boolean)
                System.Boolean))
            
            :tag
            (set!
              (. ^UnityEngine.GameObject obj tag)
              (cast-as
                (hydrate spec-val System.String)
                System.String))
            
            :name
            (set!
              (. ^UnityEngine.GameObject obj name)
              (cast-as
                (hydrate spec-val System.String)
                System.String))
            
            :hide-flags
            (set!
              (. ^UnityEngine.GameObject obj hideFlags)
              (cast-as
                (hydrate spec-val UnityEngine.HideFlags)
                UnityEngine.HideFlags))
            
            :children
            (hydrate-game-object-children obj spec-val)
            
            (do (when-let [^System.MonoType t (resolve-type-flag spec-key)]
                  (let [vspecs spec-val]
                    (if (vector? vspecs)
                      (if (= UnityEngine.Transform t)
                        (doseq [cspec vspecs]
                          (populate! (.GetComponent obj t) cspec t))
                        (doseq [cspec vspecs]
                          (populate! (.AddComponent obj t) cspec t)))
                      (throw
                        (Exception.
                          (str
                            "Component hydration for " t
                            " at spec-key " spec-key
                            " requires vector"))))))
                obj))
          ^UnityEngine.GameObject obj)
        obj
        spec'))
    :else
    (throw (Exception. "Unsupported hydration spec"))))

;; ============================================================
;; dehydration
;; ============================================================

(defn dehydrater-map-form
  [{:keys [setables-fn setables-filter]
    :or {setables-fn setables
         setables-filter (constantly true)}
    :as ctx}]
  (mu/checked-keys [[targsym typesym] ctx]
    (into {:type typesym}
      (for [setable  (setables-fn typesym)
            :when (setables-filter setable)
            :let [n       (:name setable)
                  typesym (ensure-symbol
                            (:type setable))
                  k       (nice-keyword n)
                  twiddle (gensym "dehydrater-twiddle_")]]
        `[~k
          (let [~twiddle (. ~targsym ~n)]
            (dehydrate ~twiddle)
            ;;(identity ~twiddle) WORKS
            ;;[~twiddle] WORKS
            ;; (cast-as ~twiddle ~typesym) FAILS
            )
          ;(. ~targsym ~n) WORKS
          ]))))

(defn dehydrater-form
  ([typesym] 
     (dehydrater-form typesym {}))
  ([typesym
    {:keys [setables-fn]
     :or {setables-fn setables}
     :as ctx0}]     
     (let [ctx     (mu/lit-assoc ctx0 setables-fn)
           targsym (with-meta (gensym "target-obj_") {:tag typesym})
           dmf     (dehydrater-map-form
                     (mu/lit-assoc ctx targsym typesym))]
       `(fn ~(symbol (str "dehydrater-fn-for_" typesym))
          [~targsym]
          ~dmf))))

(defn game-object-children [^GameObject obj]
  (let [^Transform trns (.GetComponent obj UnityEngine.Transform)]
    (for [^Transform trns' trns]
      (.gameObject trns'))))

(defn hydration-keyword-for-type [t]
  ((@hydration-database :types->type-flags) t))

(defn game-object-component-dehydration [^GameObject obj]
  (let [cs (.GetComponents obj UnityEngine.Component)]
    (mu/map-keys
      (group-by :type (map dehydrate cs))
      hydration-keyword-for-type)))

;; strictly speaking I should pipe all the map literal stuff through
;; dehydrate, in the name of dynamicism and extensibility and
;; consistency and so on. Seems stupid for simple value types though
(defn dehydrate-game-object [^GameObject obj]
  (merge
    (game-object-component-dehydration obj)
    {:children   (mapv dehydrate
                   (game-object-children obj))
     :type       UnityEngine.GameObject,
     :is-static  (. obj isStatic),
     :layer      (. obj layer),
     :active     (. obj active),
     :tag        (. obj tag),
     :name       (. obj name),
     :hide-flags (. obj hideFlags)}))

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
;; time weirdly variable, not sure why

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
(def setables-cache
  (atom nil))

(defn refresh-setables-cache []
  (reset! setables-cache
    (retrieve-squirreled-setables setables-path)))

;; when stable, make this disappear at runtime with some macro stuff
(refresh-setables-cache)

;; should get rid of all this ensure-type nonsense, hard to reason
;; about what's going on
(def setables-cached
  (memoize
    (fn [typesym]
      (assert (symbol? typesym))
      (@setables-cache
        typesym))))

(def problem-log (atom []))

(defn tsym-map [f tsyms ctx]
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

(defmacro establish-component-populaters-mac [m]
  (let [cpfmf (tsym-map
                populater-form
                ;;'[UnityEngine.Transform UnityEngine.BoxCollider]
                (all-component-type-symbols)
                {:setables-fn
                 setables-cached
                ;;setables
                 })]
    `(merge ~m ~cpfmf)))

(defmacro establish-value-type-populaters-mac [m]
  (let [vpfmf (tsym-map
                populater-form
                ;;'[UnityEngine.Vector3] 
                (all-value-type-symbols)
                {:setables-fn
                 setables-cached
                 ;;setables
                 })]
    `(merge ~m ~vpfmf)))

;; probably faster compile if you consolidate with populaters
(defmacro establish-value-type-hydraters-mac [m]
  (let [vhfmf (tsym-map
                hydrater-form
                ;;'[UnityEngine.Vector3]
                (all-value-type-symbols) ;; 231
                {:setables-fn
                 setables-cached
                 ;;setables
                 })]
    `(merge ~m ~vhfmf)))


(defmacro establish-component-dehydraters-mac [m]
  (let [dhm (tsym-map
              dehydrater-form
              ;;'[UnityEngine.Transform UnityEngine.BoxCollider]
              (all-component-type-symbols)
              {:setables-fn setables-cached
               :setables-filter (fn [{:keys [name declaring-class]}]
                                  (if (= declaring-class 'UnityEngine.Transform)
                                    (and
                                      (not= name 'parent)
                                      (not= name 'name))
                                    true))})]
    `(merge ~m ~dhm)))

(defmacro establish-value-type-dehydraters-mac [m]
  (let [dhm (tsym-map
              dehydrater-form
              (all-component-type-symbols)
              ;;'[UnityEngine.Vector3]
              {:setables-fn setables-cached})]
    `(merge ~m ~dhm)))

(defn establish-type-flags [hdb]
  ;; put something here
  (let [tks (seq
              (set
                (concat
                  (keys (:populaters hdb))
                  (keys (:hydraters hdb))
                  (keys (:dehydraters hdb)))))]
    (update-in hdb [:type-flags->types] merge
      (zipmap (map keyword-for-type tks) tks))))

(defn establish-inverse-type-flags [hdb]
  (let [tks (seq
              (set
                (concat
                  (keys (:populaters hdb))
                  (keys (:hydraters hdb))
                  (keys (:dehydraters hdb)))))]
    (update-in hdb [:types->type-flags] merge
      (zipmap tks (map keyword-for-type tks)))))

;; ============================================================
;; database
;; ============================================================

(def default-component-populaters
  (establish-component-populaters-mac {}))

(def default-value-type-populaters
  (establish-value-type-populaters-mac {}))

(def default-value-type-hydraters
  (establish-value-type-hydraters-mac {}))

(def default-component-dehydraters
  (establish-component-dehydraters-mac {}))

(def default-value-type-dehydraters
  (establish-value-type-dehydraters-mac {}))

(def default-hydration-database
  (->
    {:populaters {UnityEngine.GameObject #'populate-game-object!}
     :hydraters {UnityEngine.GameObject #'hydrate-game-object}
     :dehydraters {UnityEngine.GameObject #'dehydrate-game-object}
     :type-flags->types {}}
    (update-in [:populaters] merge default-component-populaters)
    (update-in [:populaters] merge default-value-type-populaters)
    (update-in [:hydraters] merge default-value-type-hydraters)
    (update-in [:dehydraters] merge default-component-dehydraters)
    (update-in [:dehydraters] merge default-value-type-dehydraters)
    establish-type-flags
    establish-inverse-type-flags))

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
 
(defn dehydrate
  ([x] (dehydrate x (type x)))
  ([x t]
     (if-let [f ((:dehydraters @hydration-database) t)]
       (f x)
       x)))

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

