(ns arcadia.core
  (:require [clojure.string :as string]
            [arcadia.reflect :as r]
            [arcadia.internal.map-utils :as mu]
            arcadia.messages
            arcadia.literals
            arcadia.internal.editor-interop)
  (:import [UnityEngine
            Application
            MonoBehaviour
            GameObject
            Component
            PrimitiveType]
           [System.Text.RegularExpressions Regex]))

(defn- regex? [x]
  (instance? System.Text.RegularExpressions.Regex x))


;; ============================================================
;; application
;; ============================================================

(defn editor? 
  "Returns true if called from within the editor. Notably, calls
  from the REPL are considered to be form within the editor"
  []
  Application/isEditor)

;; ============================================================
;; lifecycle
;; ============================================================

(definline null-obj? [x]
  `(let [x# ~x]
     (UnityEngine.Object/op_Equality x# nil)))

(defn bound-var? [v]
  (and (var? v)
    (not
      (instance? clojure.lang.Var+Unbound
        (var-get v)))))

;; this one could use some more work
;; also the bound-var test doesn't seem to work in the repl on defscn stuff from user
(defmacro defscn [name & body]
  `(let [v# (declare ~name)]
     (when (not *compile-files*)
       (let [bldg# (do ~@body)]
         (when (and (bound-var? (resolve (quote ~name)))
                 (or (instance? UnityEngine.GameObject ~name)
                   (instance? UnityEngine.Component ~name))
                 (not (null-obj? ~name)))
           (destroy ~name))
         (def ~name bldg#)))
     v#))

;; ============================================================
;; defcomponent 
;; ============================================================

(defmacro ^:private defleaked [var]
  `(def ~(with-meta (symbol (name var)) {:private true})
     (var-get (find-var '~var))))

(defleaked clojure.core/validate-fields)
(defleaked clojure.core/parse-opts+specs)
(defleaked clojure.core/build-positional-factory)

(defn- emit-defclass* 
  "Do not use this directly - use defcomponent"
  [tagname name extends assem fields interfaces methods]
  (assert (and (symbol? extends) (symbol? assem)))
  (let [classname (with-meta
                    (symbol
                      (str (namespace-munge *ns*) "." name))
                    (meta name))
        interfaces (conj interfaces 'clojure.lang.IType)]
    `(defclass*
       ~tagname ~classname
       ~extends ~assem
       ~fields 
       :implements ~interfaces 
       ~@methods)))

;; ported from deftype. should remove opts+specs, bizarre as a key. 
(defn- component-defform [{:keys [name fields constant opts+specs ns-squirrel-sym]}]
  (validate-fields fields name)
  (let [gname name ;?
        [interfaces methods opts] (parse-opts+specs opts+specs)
        ns-part (namespace-munge *ns*)
        classname (symbol (str ns-part "." gname))
        hinted-fields fields
        fields (vec (map #(with-meta % nil) fields))
        [field-args over] (split-at 20 fields)
        frm `(do
               ~(emit-defclass*
                  name
                  gname
                  'UnityEngine.MonoBehaviour
                  'UnityEngine
                  (vec hinted-fields)
                  (vec interfaces)
                  methods)
               (import ~classname)
               ~(build-positional-factory gname classname fields)
               ~classname)]
    (if constant
      `(when-not (instance? System.MonoType (resolve (quote ~name)))
         ~frm)
      frm)))

(defn- normalize-method-implementations [mimpls]
  (for [[[interface] impls] (partition 2
                             (partition-by symbol? mimpls))
        [name args & fntail] impls]
    (mu/lit-map interface name args fntail)))

(defn- find-message-interface-symbol [s]
  (when (contains? arcadia.messages/messages s) ;; bit janky
    (symbol (str "arcadia.messages.I" s))))

(defn- awake-method? [{:keys [name]}]
  (= name 'Awake))

(defn- normalize-message-implementations [msgimpls]
  (for [[name args & fntail] msgimpls
        :let [interface (find-message-interface-symbol name)]]
    (mu/lit-map interface name args fntail)))

(defn- process-method [{:keys [name args fntail]}]
  `(~name ~args ~@fntail))

;; (defn- ensure-has-message [interface-symbol args mimpls]
;;   (if (some #(= (:name %) interface-symbol) mimpls)
;;     mimpls
;;     (cons {:interface (find-message-interface-symbol interface-symbol)
;;            :name     interface-symbol
;;            :args     args
;;            :fntail   nil}
;;       mimpls)))

(defn- ensure-has-method [{msg-name :name,
                           :keys [args interface]
                           :or {args '[_]},
                           :as default},
                          mimpls]
  (let [{:keys [interface]
         :or {interface (find-message-interface-symbol msg-name)}} default]
    (assert interface "Must declare interface or use known message name")
    (if (some #(= (:name %) msg-name) mimpls)
      mimpls
      (cons
        (merge {:interface interface
                :name     msg-name
                :args     args
                :fntail   nil}
          default)
        mimpls))))

(defn- process-require-trigger [impl ns-squirrel-sym]
  (update-in impl [:fntail]
    #(cons `(do
              (require (quote ~(ns-name *ns*))))
       %)))

(defn- process-static-ctor [impl ns-squirrel-sym]
  (let [this (with-meta (first (impl :args))
                        {:tag 'clojure.lang.IStaticConstructor})]
    (update-in impl [:fntail]
               #(cons `(.CarlylesMouse ~this) %))))

(defn- require-trigger-method? [mimpl]
  (boolean
    (#{'Awake 'OnDrawGizmosSelected 'OnDrawGizmos 'Start}
     (:name mimpl))))

(defn- require-static-ctor? [mimpl]
  (boolean
    (#{'Awake}
     (:name mimpl))))

(defn- collapse-method [impl]
  (mu/checked-keys [[name args fntail] impl]
    `(~name ~args ~@fntail)))

(defn default-on-after-deserialize [this]
  (require 'arcadia.literals)
  (try 
    (doseq [[field-name field-value]
            (eval (read-string (. this _serialized_data)))]
      (.. this
          GetType
          (GetField field-name)
          (SetValue this field-value)))
    (catch ArgumentException e
      (throw (ArgumentException. (str 
                                   "Could not deserialize "
                                   this
                                   ". EDN might be invalid."))))))

(defn default-on-before-serialize [this]
  (let [field-map (arcadia.internal.editor-interop/field-map this)
        serializable-fields (mapv #(.Name %) (arcadia.internal.editor-interop/serializable-fields this))
        field-map-to-serialize (apply dissoc field-map serializable-fields)]
    (set! (. this _serialized_data) (pr-str field-map-to-serialize))))

(defn- process-defcomponent-method-implementations [mimpls ns-squirrel-sym]
  (let [[msgimpls impls] ((juxt take-while drop-while)
                          (complement symbol?)
                          mimpls)]
    (->>
      (concat
        (normalize-message-implementations msgimpls)
        (normalize-method-implementations impls))
      (ensure-has-method {:name 'Start})
      (ensure-has-method {:name 'Awake})
;      (ensure-has-method
;        {:name 'OnAfterDeserialize
;         :interface 'UnityEngine.ISerializationCallbackReceiver
;         :args '[this]
;         :fntail '[(if UnityEngine.Application/isEditor
;                     (.StaticConstructor ^clojure.lang.IStaticConstructor this))
;                   (require 'arcadia.core)
;                   (arcadia.core/default-on-after-deserialize this)]})
;
;      (ensure-has-method
;        {:name 'OnBeforeSerialize
;         :interface 'UnityEngine.ISerializationCallbackReceiver
;         :args '[this]
;         :fntail '[(if UnityEngine.Application/isEditor
;                     (.StaticConstructor ^clojure.lang.IStaticConstructor this))
;                   (require 'arcadia.core)
;                   (arcadia.core/default-on-before-serialize this)]})
      (map (fn [impl]
             (if (require-trigger-method? impl)
               (process-require-trigger impl ns-squirrel-sym)
               impl)))
      (map (fn [impl]
             (if (require-static-ctor? impl)
               (process-static-ctor impl ns-squirrel-sym)
               impl)))
      (group-by :interface)
      (mapcat
        (fn [[k impls]]
          (cons k (map collapse-method impls)))))))


(defmacro defcomponent*
  "Defines a new component. See defcomponent for version with defonce
  semantics."
  [name fields & method-impls]
  (let [fields2 (mapv #(vary-meta % assoc :unsynchronized-mutable true) fields) ;make all fields mutable
        ns-squirrel-sym (gensym (str "ns-required-state-for-" name "_"))
        method-impls2 (process-defcomponent-method-implementations method-impls ns-squirrel-sym)]
    (component-defform
      {:name name
       :fields fields2
       :opts+specs method-impls2
       :ns-squirrel-sym ns-squirrel-sym})))

(defmacro defcomponent
  "Defines a new component. defcomponent forms will not evaluate if
  name is already bound, thus avoiding redefining the name of an
  existing type (possibly with live instances). For redefinable
  defcomponent, use defcomponent*."
  [name fields & method-impls] 
  (let [fields2 (conj (mapv #(vary-meta % assoc :unsynchronized-mutable true) fields) ;make all fields mutable
                      (with-meta '_serialized_data {:tag 'String
                                                    :unsynchronized-mutable true
                                                    UnityEngine.HideInInspector {}}))
        ns-squirrel-sym (gensym (str "ns-required-state-for-" name "_"))
        method-impls2 (process-defcomponent-method-implementations method-impls ns-squirrel-sym)]
    (component-defform
      {:name name
       :constant true
       :fields fields2
       :opts+specs method-impls2
       :ns-squirrel-sym ns-squirrel-sym})))


;; ============================================================
;; type utils
;; ============================================================

(defn- same-or-subclass? [^Type a ^Type b]
  (or (= a b)
    (.IsSubclassOf a b)))

;; put elsewhere
(defn- some-2
  "Uses reduced, should be faster + less garbage + more general than clojure.core/some"
  [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))

(defn- in? [x coll]
  (boolean (some-2 #(= x %) coll)))
 ; reference to tagged var, or whatever 

;; really ought to be testing for arity as well
(defn- type-has-method? [t mth]
  (in? (symbol mth) (map :name (r/methods t :ancestors true))))

(defn- type-name? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (instance? System.MonoType y)))))

(defn- type-of-local-reference [x env]
  (assert (contains? env x))
  (let [lclb ^clojure.lang.CljCompiler.Ast.LocalBinding (env x)]
    (when (.get_HasClrType lclb)
      (.get_ClrType lclb))))

(defn- type? [x]
  (instance? System.MonoType x))

(defn- ensure-type [x]
  (cond
    (type? x) x
    (symbol? x) (let [xt (resolve x)]
                  (if (type? xt)
                    xt
                    (throw
                      (Exception.
                        (str "symbol does not resolve to a type")))))
    :else (throw
            (Exception.
              (str "expects type or type symbol")))))

(defn- tag-type [x]
  (when-let [t (:tag (meta x))]
    (ensure-type t)))

(defn- type-of-reference [x env]
  (when (symbol? x)
    (or (tag-type x)
      (if (contains? env x)
        (type-of-local-reference x env) ; local
        (let [v (resolve x)] ;; dubious
          (when (not (and (var? v) (fn? (var-get v))))
            (tag-type v))))))) 

;; ============================================================
;; condcast->
;; ============================================================

(defn- maximize
  ([xs]
   (maximize (comparator >) xs))
  ([compr xs]
   (when (seq xs)
     (reduce
       (fn [mx x]
         (if (= 1 (compr mx x))
           x
           mx))
       xs))))

(defn- most-specific-type ^Type [& types]
  (maximize (comparator same-or-subclass?)
    (remove nil? types)))

(defn- contract-condcast-clauses [expr xsym clauses env]
  (let [expr-tor (type-of-reference expr env)
        xsym-tor (type-of-reference xsym env)
        etype (most-specific-type expr-tor xsym-tor)]
    (if etype
      (if-let [[_ then] (first
                          (filter #(= etype (ensure-type (first %)))
                            (partition 2 clauses)))]
        [then]
        (->> clauses
          (partition 2)
          (filter
            (fn [[t _]]
              (same-or-subclass? etype (ensure-type t))))
          (apply concat)))
      clauses)))

(defmacro cc* [expr xsym & clauses]
  (let [[clauses default] (if (even? (count clauses))
                            [clauses nil] 
                            [(butlast clauses)
                             [:else
                              `(let [~xsym ~expr]
                                 ~(last clauses))]])
        clauses (contract-condcast-clauses
                  expr xsym clauses &env)]
    (cond
      (= 0 (count clauses))
      (last default) ;; might be nil obvi

      (= 1 (count clauses)) ;; corresponds to exact type match. janky but fine
      (first clauses)

      :else
      `(cond
         ~@(->> clauses
             (partition 2)
             (mapcat
               (fn [[t then]]
                 `[(instance? ~t ~xsym)
                   (let [~(with-meta xsym {:tag t}) ~xsym]
                     ~then)])))
         ~@default))))

;; note this takes an optional default value. This macro is potentially
;; annoying in the case that you want to branch on a supertype, for
;; instance, but the cast would remove interface information. Use with
;; this in mind.
(defmacro condcast-> [expr xsym & clauses]
  `(let [~xsym ~expr] ; binding important for &env in cc*
     (cc* ~expr ~xsym ~@clauses)))

;; ============================================================
;; get-component
;; ============================================================

(defn- camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn- dedup-by [f coll]
  (map peek (vals (group-by f coll))))

;; have to dance around a bit thanks to type-args
;; might well have to use a C# helper function to really do this with good type information
(defn- get-component-inline-fn [x-expr type-expr]
  (let [[tfrm, tf] (if (type-name? type-expr)
                     [(list 'type-args type-expr), identity]
                     (let [tsym (with-meta (gensym "type_") {:tag 'System.MonoType})]
                       [tsym, (fn [expr]
                                `(let [~tsym ~type-expr]
                                   ~expr))]))
        x-sym (gensym "x_")]
    `(let [res# (condcast-> ~x-expr ~x-sym
                  UnityEngine.GameObject ~(tf `(.GetComponent ~x-sym ~tfrm))
                  UnityEngine.Component ~(tf `(.GetComponent (.gameObject ~x-sym) ~tfrm))
                  (throw (ArgumentException.
                           (str "Expects x to be GameObject or Component, instead got " (type ~x-sym)))))]
       (when-not (null-obj? res#) res#))))

(defn get-component
  "Returns the component of Type t if x, a GameObject or Component, has one attached, nil if it doesn't. Inlines to most efficient form given local type information.

  For reasons of performance and sanity, differs from Unity's (.GetComponent <obj> <t>) in that t must be a Type (cannot be a String). Future versions may also accept Strings for t if this becomes a problem."
  {:inline (fn [x t]
             (get-component-inline-fn x t))
   :inline-arities #{2}}
  ([x, ^Type t]
   (let [res (condcast-> x x
               UnityEngine.GameObject (.GetComponent x t)
               UnityEngine.Component (.GetComponent (.gameObject x) t)
               (throw (ArgumentException.
                        (str "Expects x to be GameObject or Component, instead got " (type x)))))]
     (when-not (null-obj? res) res))))

(defn add-component 
  "Add a component to a gameobject
  
  * gameobject - the GameObject recieving the component, a GameObject
  * type       - the type of the component, a Type"
  {:inline (fn [gameobject type]
             `(.AddComponent ~gameobject ~type))
   :inline-arities #{2}}
  [^GameObject gameobject ^Type type]
  (.AddComponent gameobject type))


;; ============================================================
;; parent/child
;; ============================================================

(defn unparent ^GameObject [^GameObject child]
  (set! (.parent (.transform child)) nil)
  child)

(defn unchild ^GameObject [^GameObject parent ^GameObject child]
  (when (= parent (.parent (.transform child)))
    (unparent child))
  parent)

(defn set-parent ^GameObject [^GameObject child ^GameObject parent]
  (set! (.parent (.transform child)) (.transform parent))
  child)

(defn set-child ^GameObject [^GameObject parent child]
  (set-parent child parent)
  parent)

;; ============================================================
;; wrappers
;; ============================================================

(defn- hintable-type [t]
  (cond (= t System.Single) System.Double
    (= t System.Int32) System.Int64
    (= t System.Boolean) nil
    :else t))

(defmacro ^:private defwrapper
  "Wrap static methods of C# classes
  
  * class - the C# class to wrap, a Symbol
  * method - the C# method to wrap, a Symbol
  * docstring - the documentation for the wrapped method, a String
  * name - the name of the corresponding Clojure function, a Symbol
  
  Used internally to wrap parts of the Unity API, but generally useful."
  ([class]
   `(do ~@(map (fn [m]
          `(defwrapper
             ~class
             ~(symbol (.Name m))
             ~(str "TODO No documentation for " class "/" (.Name m))))
        (->>
          (.GetMethods
            (resolve class)
            (enum-or BindingFlags/Public BindingFlags/Static))
          (remove #(or (.IsSpecialName %) (.IsGenericMethod %)))))))
  ([class method docstring]
   `(defwrapper
     ~(symbol (string/lower-case
                (camels-to-hyphens (str method))))
     ~class
     ~method
     ~docstring))
  ([name class method docstring & body]
   `(defn ~name
      ~(str docstring (let [link-name (str (.Name (resolve class)) "." method)]
                        (str "\n\nSee also ["
                        link-name
                        "](http://docs.unity3d.com/ScriptReference/"
                        link-name
                        ".html) in Unity's reference.")))
      ~@(->> (.GetMethods
               (resolve class)
               (enum-or BindingFlags/Public BindingFlags/Static))
             (filter #(= (.Name %) (str method)))
             (remove #(.IsGenericMethod %))
             (dedup-by #(.Length (.GetParameters %)))
             (map (fn [m]
                    (let [params (map #(with-meta (symbol (.Name %))
                                                  {:tag (hintable-type (.ParameterType %))})
                                      (.GetParameters m))] 
                      (list (with-meta (vec params) {:tag (hintable-type (.ReturnType m))})
                            `(~(symbol (str class "/" method)) ~@params))))))
      ~@body)))

(defwrapper instantiate UnityEngine.Object Instantiate
  "Clones the object original and returns the clone.
  
  * original the object to clone, GameObject or Component
  * position the position to place the clone in space, a Vector3
  * rotation the rotation to apply to the clone, a Quaternion"
  ([^UnityEngine.Object original ^UnityEngine.Vector3 position]
   (UnityEngine.Object/Instantiate original position Quaternion/identity)))

(defn create-primitive
  "Creates a game object with a primitive mesh renderer and appropriate collider.
  
  * prim - the kind of primitive to create, a Keyword or a PrimitiveType.
           Keyword can be one of :sphere :capsule :cylinder :cube :plane :quad"
  [prim]
  (if (= PrimitiveType (type prim))
    (GameObject/CreatePrimitive prim)
    (GameObject/CreatePrimitive (case prim
                                  :sphere   PrimitiveType/Sphere
                                  :capsule  PrimitiveType/Capsule
                                  :cylinder PrimitiveType/Cylinder
                                  :cube     PrimitiveType/Cube
                                  :plane    PrimitiveType/Plane
                                  :quad     PrimitiveType/Quad))))

(defn destroy 
  "Removes a gameobject, component or asset.
  
  * obj - the object to destroy, a GameObject, Component, or Asset
  * t   - timeout before destroying object, a float"
  ([^UnityEngine.Object obj]
   (if (editor?)
    (UnityEngine.Object/DestroyImmediate obj)
    (UnityEngine.Object/Destroy obj)))
  ([^UnityEngine.Object obj ^double t]
   (UnityEngine.Object/Destroy obj t)))

(defwrapper object-typed UnityEngine.Object FindObjectOfType
  "Returns the first active loaded object of Type type.
  
  * type - The type to search for, a Type")

(defwrapper objects-typed UnityEngine.Object FindObjectsOfType
  "Returns a list of all active loaded objects of Type type.
  
  * type - The type to search for, a Type")

(defn ^GameObject object-named
  "Finds first GameObject in scene the name of which matches name parameter, which can be a string or a regular expression, or nil if no match can be found. 
  
  Name type:
  String - Finds first GameObject the name of which exactly matches name parameter.
  Regex - Finds first GameObject the name of which matches on (re-find <name parameter> (.name <GameObject instance>)).

  Note that this is not the most efficient way to manage references into the scene graph. See also objects-named."
  [name]
  (condcast-> name name
    String (GameObject/Find name)
    Regex (first
            (for [^GameObject obj (objects-typed GameObject)
                  :when (re-find name (.name obj))]
              obj))
    (throw (Exception. (str "Expects String or Regex, instead got " (type name))))))

;; type-hinting of condcast isn't needed here, but seems a good habit to get into
(defn objects-named
  "Finds all GameObjects in scene the name of which match name parameter, which can be a string or a regular expression, or nil if no match can be found. 
  
  Name type:
  String - Finds all GameObjects the name of which exactly match name parameter.
  Regex - Finds all GameObjects the name of which match on (re-find <name parameter> (.name <GameObject instance>)).

  Note that this is not the most efficient way to manage references into the scene graph.

  See also objects-named."
  [name]
  (condcast-> name name
    System.String
    (for [^GameObject obj (objects-typed GameObject)
          :when (= (.name obj) name)]
      obj)
    
    System.Text.RegularExpressions.Regex
    (for [^GameObject obj (objects-typed GameObject)
          :when (re-find name (.name obj))]
      obj)
    
    (throw (Exception. (str "Expects String or Regex, instead got " (type name))))))

(defwrapper object-tagged GameObject FindWithTag
  "Returns one active GameObject tagged tag. Returns null if no GameObject was found.
  
  * tag - the tag to seach for, a String")

(defwrapper objects-tagged GameObject FindGameObjectsWithTag
  "Returns a list of active GameObjects tagged tag. Returns empty array if no GameObject was found.
  
  * tag - the tag to seach for, a String")
