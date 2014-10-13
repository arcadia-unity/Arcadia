(ns arcadia.hydrate
  (:require [arcadia.internal.map-utils :as mu]
            [arcadia.reflect :as r]
            [clojure.string :as string]
            [clojure.set :as sets]
            [clojure.edn :as edn]
            clojure.walk
            [clojure.clr.io :as io])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly AssemblyName MemberInfo
            PropertyInfo FieldInfo]
           [UnityEngine GameObject Transform Vector3 Quaternion]
           System.AppDomain))

;;; TODO: get rid of types->type-flags, is pointless
;;; TODO: way too much ambiguity about types versus symbols naming types, lock it down
;;;; Actually there's not much ambiguity, for symbolic programming looks like we need symbols:
(comment
  (set! *warn-on-reflection* true)
  ;; this throws a reflection warning:
  (let [argform (with-meta (gensym "x_")
                  {:tag UnityEngine.Vector3})]
    (eval
      `(fn [~argform]
         (.x ~argform))))
  ;; this doesn't:
  (let [argform (with-meta (gensym "x_")
                  {:tag 'UnityEngine.Vector3})]
    (eval
      `(fn [~argform]
         (.x ~argform)))))

(declare hydration-database hydrate populate! dehydrate)

(defn same-or-subclass? [^Type t1, ^Type t2]
  (or (= t1 t2)
    (.IsSubclassOf t2 t1)))

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

(defn type-symbol? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (type? y)))))

(defn keyword-for-type [t]
  (let [^Type t (ensure-type t)]
    (nice-keyword
      (.Name t))))

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
        (.GetProperties t
          (enum-or BindingFlags/Instance BindingFlags/Public))))))

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

;; TODO make this more efficient
(defn setables-with-name [type-or-typesym name]
  (let [t (ensure-type type-or-typesym)]
    (filter #(= name (:name %))
      (concat
        (setable-fields t)
        (setable-properties t)))))

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
          (let [^String assembly-name assembly-name]
            (AssemblyName. assembly-name))
          
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

(defn array-type? [t]
  (and (type? t) (same-or-subclass? Array t)))

(defn array-typesym? [sym]
  (and (symbol? sym)
    (array-type?
      (ensure-type sym))))

;; the general tack taken here might be misguided. Do we want to
;; support map-based array hydration?
(defn standard-array-set-clause [setable ctx]
  (mu/checked-keys [[targsym valsym] ctx]
    (let [name         (:name setable)
          setable-type (type-for-setable setable)
          element-type (ensure-symbol   ; meh
                         (.GetElementType
                           (ensure-type setable-type)))
          ressym           (with-meta (gensym "hydrate-result_")
                             {:tag setable-type})]
      `(let [~ressym (if (instance? ~setable-type ~valsym)
                       ~valsym
                       (into-array ~element-type 
                         (map           ; grumble
                           (fn [element-spec#]
                             (hydrate element-spec# ~element-type)) ; hmurk not sure about this
                           ~valsym)))]
         (set! (. ~targsym ~name) ~ressym)))))

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

(defn standard-populater-set-clause [setable ctx]
  (mu/checked-keys [[targsym valsym] ctx]
    (if (array-typesym? (type-for-setable setable))
      (standard-array-set-clause setable ctx)
      (let [name         (:name setable)
            setable-type (type-for-setable setable)
            ressym       (with-meta (gensym "hydrate-result_")
                           {:tag setable-type})]
        `(let [~ressym (if (instance? ~setable-type ~valsym)
                         ~valsym
                         (hydrate ~valsym ~setable-type))]
           (set! (. ~targsym ~name) ~ressym))))))

(defn populater-key-clauses
  [{:keys [set-clause-fn]
    :as ctx}]
  (mu/checked-keys [[typesym setables-fn] ctx]
    (assert (symbol? typesym))
    (apply concat
      (for [{:keys [name] :as setable} (setables-fn typesym)
            k    (keys-for-setable setable)
            :let [cls (if set-clause-fn
                        (set-clause-fn setable ctx)
                        (standard-populater-set-clause setable ctx))]]
        `[~k ~cls]))))

(defn prcf-default-case-form [ctx]
  (if-let [case-default-fn (-> ctx
                             :populater-form-opts
                             :reducing-form-opts
                             :case-default-fn)]
    (case-default-fn ctx)
    (mu/checked-keys [[targsym] ctx]
      targsym)))

(defn populater-reducing-case-form [ctx]
  (mu/checked-keys [[typesym targsym valsym keysym] ctx]
    (let [skcs (populater-key-clauses ctx)
          default-case (prcf-default-case-form ctx)]
      `(case ~keysym
         ~@skcs
         ~default-case))))

(defn prepopulater-form [ctx]
  (mu/checked-keys [[specsym] ctx]
    (if-let [prepopulater-form-fn (:prepopulater-form-fn ctx)]
      (prepopulater-form-fn ctx)
      specsym)))

(defn populater-reducing-fn-form [ctx]
  (mu/checked-keys [[typesym] ctx]
    (let [keysym   (gensym "spec-key_")
          valsym   (gensym "spec-val_")
          targsym  (with-meta (gensym "targ_") {:tag typesym})
          caseform (populater-reducing-case-form
                     (mu/lit-assoc ctx keysym valsym targsym))
          fn-inner-name (symbol (str "populater-fn-for_" typesym))]
      `(fn ~fn-inner-name ~[targsym keysym valsym] 
         ~caseform
         ~targsym))))

(defn populater-map-clause [ctx]
  (mu/checked-keys [[specsym typesym initsym] ctx]
    (let [sr      (populater-reducing-fn-form ctx)
          ppf     (prepopulater-form ctx)]
      `(let [~specsym ~ppf]
         (reduce-kv
           ~sr
           ~initsym
           ~specsym)))))

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

(def log (atom []))

(defn populater-form
  ([typesym] (populater-form typesym {}))
  ([typesym ctx]
     (if (let [^Type t (resolve typesym)] ;this is genuinely stupid
           (.IsValueType t))
       (value-populater-form typesym ctx)
       (let [ctx      (mu/fill ctx :setables-fn setables)
             targsym  (with-meta (gensym "populater-target_") {:tag typesym})
             specsym  (gensym "spec_")
             pmc      (populater-map-clause
                        (-> ctx
                          (assoc :initsym targsym)
                          (mu/lit-assoc typesym specsym)))]
         `(fn ~(symbol (str "populater-fn-for_" typesym))
            [~targsym ~specsym]
            (swap! log conj [~targsym ~specsym])
            ~pmc)))))

;; ============================================================
;; hydrater-forms
;; ============================================================
 
(defn constructors-spec [type-or-typesym]
  (let [^Type t (ensure-type type-or-typesym)]
    (set
      (conj
        (for [^System.Reflection.MonoCMethod cinf (.GetConstructors t)
              :let [pinfs (.GetParameters cinf)]]
          (mapv (fn [^System.Reflection.ParameterInfo pinf]
                  (.ParameterType pinf))
            pinfs))
        (when (.IsValueType t) [])))))

;; janky + reflective for now. Need something to disambiguate dispatch
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

(defn hydrater-map-clause [ctx]
  (mu/checked-keys [[specsym typesym] ctx]
    (let [initsym   (with-meta (gensym "hydrater-target_")
                      {:tag typesym})
          initf     (hydrater-init-form ctx)
          pmc       (populater-map-clause
                      (mu/lit-assoc ctx initsym))]
      `(let ~[initsym initf]
         ~pmc))))

(defn hydrater-form 
  ([typesym]
     (hydrater-form typesym {}))
  ([typesym ctx]
     (let [specsym (gensym "spec_")
           ctx     (-> ctx
                     (mu/fill->
                       :setables-fn setables
                       :ctrspec (constructors-spec typesym))
                     (mu/lit-assoc specsym typesym))]
       
       `(fn ~(symbol (str "hydrater-fn-for_" typesym))
          [~specsym]
          (cond
            (instance? ~typesym ~specsym)
            ~specsym

            (map? ~specsym)
            ~(hydrater-map-clause ctx)

            (vector? ~specsym)
            ~(constructor-application-form
               (assoc ctx :cvsym specsym))

            :else
            (throw (Exception. "Unsupported hydration spec")))))))

(defn value-hydrater-form
  ([typesym]
     (value-hydrater-form typesym {}))
  ([typesym ctx]
     (let [ctx2 (mu/fill-> ctx :setables-fn setables)]
       (mu/checked-keys [[setables-fn] ctx2]
         (let [fields            (map :name (setables-fn typesym))
               field->setter-sym (get-field->setter-sym fields)
               setter-inits      (get-setter-inits typesym field->setter-sym)
               ctx3              (assoc ctx2
                                   :set-clause-fn value-type-set-clause
                                   :field->setter-sym field->setter-sym)]
           `(let [~@setter-inits]
              ~(hydrater-form typesym ctx3)))))))

(defn resolve-type-flag [tf]
  (if (type? tf)
    tf
    ((:type-flags->types @hydration-database) tf)))

(declare hydrate-game-object assoc-in-mv)

(defn hydrate-game-object-children
  [^UnityEngine.GameObject obj, specs]
  (let [^UnityEngine.Transform trns (.GetComponent obj UnityEngine.Transform)]
    (doseq [spec specs]
      (hydrate-game-object
        (assoc-in-mv spec [:transform 0 :parent] trns)))))

(defn game-object-children [^GameObject obj]
  (let [^Transform trns (.GetComponent obj UnityEngine.Transform)]
    (for [^Transform trns' trns]
      (.gameObject trns'))))

(defn playing? []
  UnityEngine.Application/isPlaying)

(defn clear-game-object-children [^GameObject obj]
  (let [playing (playing?)]
    (doseq [^GameObject child  (vec (game-object-children obj))]
      (if playing ;; maybe this is stupid, just destroy immediate
        (.Destroy child)
        (.DestroyImmediate child)))))

(defn game-object-prepopulate [^GameObject obj, spec]
  (as-> spec spec
    (if-let [t (first (:transform spec))] ;; obsoletes resolve-type-key
      (do (populate! (.GetComponent obj UnityEngine.Transform) t)
          (dissoc spec :transform))
      spec)
    (if-let [cs (:children spec)]
      (do ;;(clear-game-object-children obj)
          (hydrate-game-object-children obj cs)
          (dissoc spec :children))
      spec)))

(defmacro transform-prepopulate-helper-mac [targ spec key field type]
  (let [valsym (with-meta (gensym "val_") {:tag type})]
    `(if-let [e# (find ~spec ~key)] ;; local before global, suprisingly
       (let [specval#    (val e#)
             ~valsym (if (instance? ~type specval#)
                       specval#
                       (hydrate specval# ~type))]
         (set! (. ~targ ~field) ~valsym)
         (dissoc ~spec ~key))
       ~spec)))

(def log (atom []))

;; locals before globals, surprisingly
(defn transform-prepopulate [^Transform trns, spec]
  (swap! log conj (:local-position spec))
  (-> spec
    ;; (dissoc :local-rotation :rotation :local-euler-angles)
    ;; (dissoc :local-rotation :rotation :euler-angles)
    ;; (dissoc
    ;;   ;:position :local-position
    ;;   #_:local-rotation :rotation
    ;;   :euler-angles :local-euler-angles
    ;;   ;:scale :local-scale
    ;;   )
    (as-> spec
      (transform-prepopulate-helper-mac trns spec :parent             parent           Transform)
      (transform-prepopulate-helper-mac trns spec :local-position     localPosition    Vector3)
      (transform-prepopulate-helper-mac trns spec :position           position         Vector3)
      (transform-prepopulate-helper-mac trns spec :local-rotation     localRotation    Quaternion)
      (transform-prepopulate-helper-mac trns spec :rotation           rotation         Quaternion)
      (transform-prepopulate-helper-mac trns spec :local-euler-angles localEulerAngles Vector3)
      (transform-prepopulate-helper-mac trns spec :euler-angles       eulerAngles      Vector3)
      (transform-prepopulate-helper-mac trns spec :local-scale        localScale       Vector3)
      ; following doesn't exist:
      ;(transform-prepopulate-helper-mac trns spec :scale              scale            Vector3) 
      )))

;; all about the snappy fn names
(defn game-object-populate-case-default-fn [ctx]
  (mu/checked-keys [[targsym keysym valsym] ctx]
    `(do
       (if-let [^System.MonoType t# (resolve-type-flag ~keysym)]
         (if (same-or-subclass? UnityEngine.Component t#)
           (let [vspecs# ~valsym]
             (if (vector? vspecs#)
               (if (= UnityEngine.Transform t#)
                 (doseq [cspec# vspecs#]
                   (populate! (.GetComponent ~targsym t#) cspec# t#))
                 (doseq [cspec# vspecs#]
                   (populate! (.AddComponent ~targsym t#) cspec# t#)))
               (throw
                 (Exception.
                   (str
                     "GameObject population for " t# 
                     " at spec-key " ~keysym
                     " requires vector"))))))
         (if (= :children ~keysym)
           (hydrate-game-object-children ~targsym ~valsym)))
       ~targsym)))

(defmacro populate-game-object-mac []
  (populater-form 'UnityEngine.GameObject
    {:populater-form-opts
     {:reducing-form-opts
      {:case-default-fn game-object-populate-case-default-fn}}
     
     :prepopulater-form-fn
     (fn hydrate-prepopulater-form-fn [ctx]
       (mu/checked-keys [[initsym specsym] ctx]
         `(game-object-prepopulate ~initsym ~specsym)))}))

(def populate-game-object!
  (populate-game-object-mac))

(defmacro hydrate-game-object-mac []
  (hydrater-form 'UnityEngine.GameObject
    {:populater-form-opts
     {:reducing-form-opts
      {:case-default-fn game-object-populate-case-default-fn}}
     
     :prepopulater-form-fn
     (fn hydrate-prepopulater-form-fn [ctx]
       (mu/checked-keys [[initsym specsym] ctx]
         `(game-object-prepopulate ~initsym ~specsym)))}))

(def hydrate-game-object
  (hydrate-game-object-mac))

;; ============================================================
;; dehydration
;; ============================================================

(declare array-dehydration-form)

(defn array-element-dehydration-form [ctx]
  ;; typesym here is typesym of containing array
  (mu/checked-keys [[element-sym element-typesym] ctx]
    (if (array-typesym? element-typesym)
      (array-dehydration-form
        (-> ctx
          (dissoc :element-sym :element-typesym)
          (assoc
            :targsym element-sym
            :typesym element-typesym)))
      `(dehydrate ~element-sym ~element-typesym))))

(defn array-dehydration-form [ctx]
  (mu/checked-keys [[targsym typesym] ctx]
    (let [index-sym       (gensym "index_")
          element-type    (.GetElementType (ensure-type typesym))
          element-typesym (ensure-symbol element-type)
          element-sym (with-meta (gensym "element_")
                        (if (not (value-type? element-type))
                          {:tag element-typesym}
                          {}))
          aedf        (array-element-dehydration-form
                        (mu/lit-assoc ctx element-sym element-typesym))]
      `(areduce ~targsym index# bldg# []
         (let [~element-sym (aget ~targsym index#)]
           (conj bldg# ~aedf))))))

(defn object-element-dehydration-form [setable ctx]
  (mu/checked-keys [[name] setable
                    [targsym] ctx]
    (let [element-typesym (type-for-setable setable)]
      (if (array-typesym? element-typesym)
        (let [arsym (with-meta (gensym "array_")
                      {:tag element-typesym})
              adf   (array-dehydration-form
                      (assoc ctx
                        :targsym arsym
                        :typesym element-typesym))]
          `(when-let [~arsym (. ~targsym ~name)]
             ~adf))
        `(when-let [elem# (. ~targsym ~name)]
           (dehydrate elem#  ~element-typesym))))))

(defn object-dehydration-form [ctx]
  (mu/checked-keys [[setables-fn typesym] ctx]
    (let [{:keys [setables-fn setables-filter]} ctx]
      (into {:type typesym}
        (for [setable (cond->> (setables-fn typesym)
                        setables-filter (filter #(setables-filter % ctx)))
              :let [k  (nice-keyword (:name setable))
                    dc (object-element-dehydration-form setable ctx)]]
          `[~k ~dc])))))

(defn dehydration-form [ctx]
  (mu/checked-keys [[typesym] ctx]
    (if (array-type? (ensure-type typesym))
      (array-dehydration-form ctx)
      (object-dehydration-form ctx))))
 
(defn dehydrater-form
  ([typesym] 
     (dehydrater-form typesym {}))
  ([typesym ctx]     
     (let [targsym (with-meta (gensym "target-obj_") {:tag typesym})
           df      (dehydration-form
                     (-> ctx
                       (mu/fill :setables-fn setables)
                       (mu/lit-assoc targsym typesym)))]
       `(fn ~(symbol (str "dehydrater-fn-for_" typesym))
          [~targsym]
          ~df))))

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

;; some shortcuts here (standard-populater-set-clause), see
;; populater-key-clauses
(defn sepf-clause [sharable ctx]
  (mu/checked-keys [[direct shared]   sharable
                    [specsym typesym] ctx]
    (assert (symbol? direct))
    (assert (symbol? shared))
    (let [direct-setable (first (setables-with-name typesym direct))
          shared-setable (first (setables-with-name typesym shared))
          direct-valsym  (gensym "direct-valsym_")
          shared-valsym  (gensym "shared-valsym_")
          d-clause       (standard-populater-set-clause direct-setable
                           (assoc ctx :valsym direct-valsym))
          s-clause       (standard-populater-set-clause shared-setable
                           (assoc ctx :valsym shared-valsym))
          direct-key     (first (keys-for-setable direct-setable)) ;; the hell with it, one key per setable 4 now
          shared-key     (first (keys-for-setable shared-setable))]
      `(if-let [[_# ~direct-valsym] (find ~specsym ~direct-key)]
         (do ~d-clause
             (-> ~specsym (dissoc ~shared-key) (dissoc ~direct-key)))
         (if-let [[_# ~shared-valsym] (find ~specsym ~shared-key)]
           (do ~s-clause
               (dissoc ~specsym ~shared-key))
           ~specsym)))))

(defn shared-elem-prepopulater-form [sharables ctx]
  (mu/checked-keys [[typesym initsym specsym] ctx]
    (let [targsym initsym 
          ctx2    (mu/lit-assoc ctx targsym specsym)
          pfcs    (for [s sharables] (sepf-clause s ctx2))]
      `(as-> ~specsym ~specsym
         ~@pfcs))))

(def shared-elem-components
  {'UnityEngine.MeshFilter [{:direct 'mesh
                             :shared 'sharedMesh}]
   'UnityEngine.MeshRenderer [{:direct 'materials
                               :shared 'sharedMaterials}
                              {:direct 'material
                               :shared 'sharedMaterial}]
   'UnityEngine.CapsuleCollider [{:direct 'material
                                  :shared 'sharedMaterial}]})

;; this is kind of insane. Cleaner way?
(defmacro establish-component-populaters-mac [m]
  (let [specials (conj (set (keys shared-elem-components))
                   'UnityEngine.Transform)
        cpfmf (->
                (tsym-map
                  populater-form
                  (remove specials
                    (all-component-type-symbols))
                  {:setables-fn setables})
                (as-> cpfmf 
                  (reduce-kv
                    (fn [bldg t sharables]
                      (assoc bldg t
                        (populater-form t
                          {:prepopulater-form-fn
                           (fn [ctx]
                             (shared-elem-prepopulater-form
                               (shared-elem-components t)
                               ctx))})))
                    cpfmf
                    shared-elem-components)
                  (assoc cpfmf 'UnityEngine.Transform
                    (populater-form 'UnityEngine.Transform
                      {:prepopulater-form-fn
                       (fn [ctx]
                         (mu/checked-keys [[initsym specsym] ctx]
                           `(transform-prepopulate ~initsym ~specsym)))}))))]
    `(merge ~m ~cpfmf)))

(defmacro establish-value-type-populaters-mac [m]
  (let [vpfmf (tsym-map
                populater-form
                ;;'[UnityEngine.Vector3] 
                (all-value-type-symbols)
                {:setables-fn
                 setables
                 ;;setables
                 })]
    `(merge ~m ~vpfmf)))

;; probably faster compile if you consolidate with populaters
(defmacro establish-value-type-hydraters-mac [m]
  (let [vhfmf (tsym-map
                value-hydrater-form
                ;;'[UnityEngine.Vector3]
                (all-value-type-symbols) ;; 231
                {:setables-fn
                 setables
                 ;;setables
                 })]
    `(merge ~m ~vhfmf)))

(def component-hydrater-field-blacklist
  #{'parent 'name 'tag 'active 'hideFlags})

(defn component-dehydrater-setables-filter [setable ctx]
  (mu/checked-keys [[typesym] ctx
                    [name] setable]
    (and
      (not (component-hydrater-field-blacklist name))
      (if-let [[_ sharables] (find shared-elem-components typesym)]
        (not-any? #(= name (:direct %)) sharables)
        true))))

(defn transform-dehydrater-setables-filter [setable ctx]
  (mu/checked-keys [[typesym] ctx
                    [name] setable]
    ('#{localPosition localRotation localScale}
     name)))

(defmacro establish-component-dehydraters-mac [m]
  (let [dhm (tsym-map
              dehydrater-form
              ;;'[UnityEngine.Transform UnityEngine.BoxCollider]
              (remove #{'UnityEngine.Transform}
                (all-component-type-symbols))
              {:setables-fn setables
               :setables-filter component-dehydrater-setables-filter})
        dhm (assoc dhm
              'UnityEngine.Transform
              (dehydrater-form 'UnityEngine.Transform
                {:setables-fn setables
                 :setables-filter transform-dehydrater-setables-filter}))]
    `(merge ~m ~dhm)))

(defmacro establish-value-type-dehydraters-mac [m]
  (let [dhm (tsym-map
              dehydrater-form
              (all-value-type-symbols)
              ;;'[UnityEngine.Vector3]
              {:setables-fn setables})]
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
  (establish-component-populaters-mac {})
  ;;nil
  )

(def default-value-type-populaters
  (establish-value-type-populaters-mac {})
  ;;nil
  )

(def default-value-type-hydraters
  (establish-value-type-hydraters-mac {})
  ;;nil
  )

(def default-component-dehydraters
  (establish-component-dehydraters-mac {})
  ;;nil
  )

;; (def default-value-type-dehydraters
;;   (establish-value-type-dehydraters-mac {}))

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
    ;;(update-in [:dehydraters] merge default-value-type-dehydraters)
    establish-type-flags
    establish-inverse-type-flags))

(def hydration-database
  (atom default-hydration-database))

;; ============================================================
;; public-facing type registration api
;; ============================================================

(defn component-type? [x]
  (and (type? x)
    (same-or-subclass? UnityEngine.MonoBehaviour x)))

(defn value-type? [x]
  (and (type? x)
    (.IsValueType x)))

(defn value-typesym? [x]
  (and
    (type-symbol? x)
    (.IsValueType
      (ensure-type x))))

(defn ns-qualify-kw [kw ns]
  (let [nsn (if (same-or-subclass? clojure.lang.Namespace (type ns))
              (str (ns-name ns))
              (str ns))]
    (keyword nsn (name kw))))

(defn- registration [{:keys [populater hydrater dehydrater type-flag] :as argsm}]
  (mu/checked-keys [[type] argsm]
    (swap! hydration-database
      (fn [hdb]
        (cond-> hdb
          hydrater   (assoc-in [:populaters type]  hydrater)
          populater  (assoc-in [:populaters type]  populater)
          dehydrater (assoc-in [:dehydraters type] dehydrater)
          type-flag  (->
                       (assoc-in [:type-flags->types type-flag] type)
                       (assoc-in [:types->type-flags type] type-flag)))))
    nil))

(defn hydrater-for-registration [typesym option-map]
  (eval
    (value-hydrater-form typesym
      (merge {}
        (::hydrater-options option-map)))))

(defn dehydrater-for-registration [typesym option-map]
  (eval
    (dehydrater-form typesym
      (merge
        {:setables-filter component-dehydrater-setables-filter}
        (::dehydrater-options option-map)))))

(defn populater-for-registration [typesym option-map]
  (eval
    (populater-form typesym
      (merge {}
        (::populater-options option-map)))))

(defn type-flag-for-registration [type option-map]
  (or (:type-flag option-map)
    (ns-qualify-kw
      (keyword-for-type
        (ensure-type type))
      *ns*)))

(defn- register-component-type
  ([type-or-typesym]
     (register-component-type type-or-typesym {}))
  ([type-or-typesym option-map]
     (let [^Type t (ensure-type type-or-typesym)]
       (if-not (component-type? t)
         (throw
           (System.ArgumentException.
             "register-component-type expects component type"))
         (let [typesym    (ensure-symbol t)
               populater  (populater-for-registration typesym option-map)
               dehydrater (dehydrater-for-registration typesym option-map
                            (assoc option-map :setables-filter
                                   component-dehydrater-setables-filter))
               type-flag  (type-flag-for-registration t option-map)]
           (registration
             (mu/lit-assoc {:type t}
               type-flag populater dehydrater)))))))

(defn- register-value-type
  ([type-or-typesym]
     (register-component-type type-or-typesym {}))
  ([type-or-typesym option-map]
     (let [^Type t (ensure-type type-or-typesym)]
       (if-not (value-type? t)
         (throw
           (System.ArgumentException.
             "register-value-type expects value type"))
         (let [typesym    (ensure-symbol t)
               hydrater   (hydrater-for-registration typesym option-map)
               populater  (populater-for-registration typesym option-map)
               dehydrater (dehydrater-for-registration typesym option-map)
               type-flag  (type-flag-for-registration t option-map)]
           (registration
             (mu/lit-assoc {:type t}
               hydrater populater dehydrater type-flag)))))))

(defn- register-normal-type
  ([type-or-typesym]
     (register-component-type type-or-typesym {}))
  ([type-or-typesym option-map]
     (let [^Type t (ensure-type type-or-typesym)]
       (let [typesym    (ensure-symbol t)
             hydrater   (hydrater-for-registration typesym option-map)
             populater  (populater-for-registration typesym option-map)
             dehydrater (dehydrater-for-registration typesym)
             type-flag  (type-flag-for-registration t option-map)]
         (registration
           (mu/lit-assoc {:type t}
             hydrater populater dehydrater type-flag))))))

(defn register-type
  ([type-or-typesym]
     (register-type type-or-typesym {}))
  ([type-or-typesym option-map]
     (let [^Type t (ensure-type type-or-typesym)]
       (cond
         (component-type? t)
         (register-component-type t option-map)

         (.IsValueType t)
         (register-value-type t option-map)

         :else
         (register-normal-type t option-map)))))

(defn unregister-type [type-or-typesym]
  (let [^Type t (ensure-type type-or-typesym)]
    (swap! hydration-database
      (fn [hdb]
        (let [type-flag (get-in hdb [:types->type-flags t])]
          (-> hdb
            (mu/dissoc-in [:populaters t])
            (mu/dissoc-in [:hydraters t])
            (mu/dissoc-in [:dehydraters t])
            (mu/dissoc-in [:type-flags->types type-flag])
            (mu/dissoc-in [:types->type-flags t])))))
    nil))

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

;; ============================================================
;; structural manipulation functions
;; ============================================================

(declare assoc-in-mv)

(defn- mv-default-clause [[k & ks]]
  (cond
    (= 0 k) []
    (number? k) (throw
                  (Exception.
                    "assoc-ing into fresh vector fails for non-zero indices"))
    :else {}))

;; update-in-mv ----------------------

(declare update-in-mv)

(defn- update-in-mv-vec [v [k & ks :as path] f]
  (let [num-q (number? k)]
    (if ks
      (if num-q
        (assoc v k
          (update-in-mv
            (get v k (mv-default-clause ks))
            ks f))
        (mapv #(update-in-mv % path f) v))
      (if num-q
        (assoc v k (f (get v k)))
        (mapv #(update-in-mv % path f) v)))))

(defn- update-in-mv-map [m [k & ks] f]
  (if ks
    (assoc m k
      (update-in-mv (get m k) ks f))
    (assoc m k (f (get m k)))))

(defn update-in-mv
  ([m path f & args]
     (update-in-mv path #(apply f % args)))
  ([m path f]
     (if (vector? m)
       (update-in-mv-vec m path f)
       (update-in-mv-map m path f))))

;; assoc-in-mv --------------------------

(defn assoc-in-mv [v path x]
  (update-in-mv v path (fn [_] x)))

;; deep-merge ---------------------------

;; remove when reduce-kv patch lands
(defn- stopgap-reduce-kv [f init coll]
  (if (vector? coll)
    (let [c (count coll)]
      (loop [bldg init, i (int 0)]
        (if (< i c)
          (recur (f bldg i (nth coll i)), (inc i))
          bldg)))
    (reduce-kv f init coll)))

;; another, wackier version of this would map across vectors when the
;; corresponding spec val isn't a vector, but that would sacrifice
;; associativity, which seems important for merge
(defn deep-merge-mv [& maps]
  (let [m-or-v?     #(or (vector? %) (map? %))
        merge-entry (fn merge-entry [m k v1]
                      (if (contains? m k)
                        (let [v0 (get m k)] ;; something seems amiss
                          (assoc m k
                            (cond
                              (not (m-or-v? v0)) v1
                              (m-or-v? v1) (deep-merge-mv v0 v1)
                              :else v1))) 
                        (assoc m k v1)))
        merge2 (fn merge2 [m1 m2]
                 (stopgap-reduce-kv merge-entry (or m1 (empty m2)) m2))]
    (reduce merge2 maps)))

;; select-paths-mv ----------------------

(defn select-paths-mv
  ([m path]
     (assoc-in-mv (empty m) path (get-in m path)))
  ([m path & paths]
     (loop [bldg (select-paths-mv m path),
            paths paths]
       (if-let [[p & rps] (seq paths)]
         (recur (assoc-in-mv bldg p (get-in m p)), rps)
         bldg))))


;; ============================================================
;; tests
;; ============================================================

;; not the best place to put them, move elsewhere when we have a
;; stable testing story

(require '[clojure.test :as test])

(defn run-tests [& args]
  (binding [test/*test-out* *out*]
    (apply test/run-tests args)))

(defmacro with-temporary-object [[name objexpr] & body]
  `(let [~name  ~objexpr  
         retval# (do ~@body)]
     (UnityEngine.Object/DestroyImmediate ~name false)
     retval#))

(defn respec [spec]
  (with-temporary-object [obj (hydrate spec)]
    (dehydrate obj)))

(defn spec-idempotent-under-hydration? [spec]
  (let [spec' (respec spec)]
    (= spec' (respec spec'))))

(test/deftest test-hydrater-positional-idempotence
  (test/testing "basic"
    (test/is
      (spec-idempotent-under-hydration?
        {:children  [{:transform [{:local-position [1 2 3]}]}]
         :transform [{:local-position [1 2 3]}]}))
    (test/is
      (spec-idempotent-under-hydration?
        {:children (->> (vec (repeat 3 {:transform [{:local-position [1 2 3]}]})))
         :transform [{:local-position [1 2 3]}]})))
  (test/testing "nesting"
    (test/is
      (spec-idempotent-under-hydration?
        (as-> {:transform [{:local-position [1 2 3]}]} spec
          (assoc spec :children [spec])
          (assoc spec :children [spec])
          (assoc spec :children [spec]))))
    (test/is
      (spec-idempotent-under-hydration?
        (as-> {:transform [{:local-position [1 2 3]}]} spec
          (assoc spec :children [spec spec])
          (assoc spec :children [spec spec])
          (assoc spec :children [spec spec]))))))


;; structural manipulation functions ----------------------------------

(test/deftest test-structural
  (test/is (= {:a [{:c :E, :b :B} {:c :C}]}
             (let [m1 {:a [{:b :B} {:c :C}]}
                   m2 {:a [{:c :E}]}]
               (deep-merge-mv m1 m2))))
  (test/is (= {:a [{:b :B} {:c :C}]}
             (let [m1 {:a [{:b :B} {:c :C}]}
                   m2 {:a []}]
               (deep-merge-mv m1 m2))))
  (test/is
    (let [m1 {:a [{:b :B} {:c :C}]}
          m2 {:a []}]
      (=
        (deep-merge-mv m1 m2)
        (deep-merge-mv m2 m1))))
  (test/is (= (deep-merge-mv
                {:a {:b :B}}
                {})
             {:a {:b :B}}))
  (test/is (= (deep-merge-mv
                {}
                {:a {:b :B}})
             {:a {:b :B}})))
