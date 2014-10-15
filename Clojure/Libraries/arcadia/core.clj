(ns arcadia.core
  (:require [clojure.string :as string]
            [arcadia.reflect :as r]
            [arcadia.internal.map-utils :as mu]
            arcadia.messages)
  (:import [UnityEngine
            Application
            MonoBehaviour
            GameObject
            PrimitiveType]))

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
;; defcomponent 
;; ============================================================

(defmacro defleaked [var]
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

(defmacro defcomponent*
  [name fields & opts+specs]
  (validate-fields fields name)
  (let [gname name 
        [interfaces methods opts] (parse-opts+specs opts+specs)
        ns-part (namespace-munge *ns*)
        classname (symbol (str ns-part "." gname))
        hinted-fields fields
        fields (vec (map #(with-meta % nil) fields))
        [field-args over] (split-at 20  fields)]
    `(let []
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
       ~classname)))

(defn- normalize-method-implementations [mimpls]
  (for [[[protocol] impls] (partition 2
                             (partition-by symbol? mimpls))
        [name args & fntail] impls]
    (mu/lit-map protocol name args fntail)))

(defn- find-message-protocol-symbol [s]
  (symbol (str "arcadia.messages/I" s)))

(defn- awake-method? [{:keys [name]}]
  (= name 'Awake))

(defn- normalize-message-implementations [msgimpls]
  (for [[name args & fntail] msgimpls
        :let [protocol (find-message-protocol-symbol name)]]
    (mu/lit-map protocol name args fntail)))

(defn- process-method [{:keys [protocol name args fntail]}]
  [protocol `(~name ~args ~@fntail)])

(defn- process-awake-method [impl]
  (process-method
    (update-in impl [:fntail]
      #(cons `(require (quote ~(ns-name *ns*))) %))))

(defn ^:private ensure-has-awake [mimpls]
  (if (some awake-method? mimpls)
    mimpls
    (cons {:protocol (find-message-protocol-symbol 'Awake)
           :name     'Awake
           :args     '[this]
           :fntail   nil}
      mimpls)))

(defn- process-defcomponent-method-implementations [mimpls]
  (let [[msgimpls impls] ((juxt take-while drop-while)
                          (complement symbol?)
                          mimpls)
        nrmls            (ensure-has-awake
                           (concat
                             (normalize-message-implementations msgimpls)
                             (normalize-method-implementations impls)))]
    (apply concat
      (for [impl nrmls]
        (if (awake-method? impl)
          (process-awake-method impl)
          (process-method impl))))))

(defmacro defcomponent
  "Defines a new component."
  [name fields & method-impls] 
  (let [fields2 (mapv #(vary-meta % assoc :unsynchronized-mutable true) fields) ;make all fields mutable
        method-impls2 (process-defcomponent-method-implementations method-impls)]
    `(defcomponent* ~name ~fields2 ~@method-impls2)))

;; ============================================================
;; get-component
;; ============================================================

(defn- camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn- dedup-by [f coll]
  (map peek (vals (group-by f coll))))

(defn- some-2
  "Uses reduced, should be faster + less garbage + more general than clojure.core/some"
  [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))

(defn- in? [x coll]
  (boolean (some-2 #(= x %) coll)))

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

(defn- tag-type [x]
  (when-let [t (:tag (meta x))]
    (cond ;; this does seem kind of stupid. Can we really tag things
          ;; with mere symbols as types?
      (type? t) t
      (type-name? t) (resolve t))))

(defn- type-of-reference [x env]
  (or (tag-type x) ; tagged symbol
      (when (contains? env x) (type-of-local-reference x env)) ; local
      (when (symbol? x) (tag-type (resolve x))))) ; reference to tagged var, or whatever 

;; really ought to be testing for arity as well
(defn- type-has-method? [t mth]
  (in? (symbol mth) (map :name (r/methods t :ancestors true))))

;; maybe we should be passing full method sigs around rather than
;; method names. 
(defn- known-implementer-reference? [x method-name env]
  (boolean
    (when-let [tor (type-of-reference x env)]
      (type-has-method? tor method-name))))

(defn- raise-args [[head & rst]]
  (let [gsyms (repeatedly (count rst) gensym)]
    `(let [~@(interleave gsyms rst)]
       ~(cons head gsyms))))

(defn- raise-non-symbol-args [[head & rst]]
  (let [bndgs (zipmap 
                (remove symbol? rst)
                (repeatedly gensym))]
    `(let [~@(mapcat reverse bndgs)]
       ~(cons head (replace bndgs rst)))))

(defmacro get-component* [obj t]
  (if (not-every? symbol? [obj t])
    (raise-non-symbol-args
      (list 'arcadia.core/get-component* obj t))
    (cond
      (contains? &env t)
      `(.GetComponent ~obj ~t)
      (and
        (known-implementer-reference? obj 'GetComponent &env)
        (type-name? t))
      `(.GetComponent ~obj (~'type-args ~t))
      :else
      `(.GetComponent ~obj ~t))))

(defn get-component
  "Returns the component of Type type if the game object has one attached, nil if it doesn't.
  
  * gameobject - the GameObject to query, a GameObject
  * type - the type of the component to get, a Type or String"
  {:inline (fn [gameobject type]
             (list 'arcadia.core/get-component* gameobject type))
   :inline-arities #{2}}
  [gameobject type]
  (.GetComponent gameobject type))

(defn add-component 
  "Add a component to a gameobject
  
  * gameobject - the GameObject recieving the component, a GameObject
  * type       - the type of the component, a Type"
  {:inline (fn [gameobject type]
             `(.AddComponent ~gameobject ~type))
   :inline-arities #{2}}
  [^GameObject gameobject ^Type type]
  (.AddComponent gameobject type))

;; note this takes an optional default value. This macro is potentially
;; annoying in the case that you want to branch on a supertype, for
;; instance, but the cast would remove interface information. Use with
;; this in mind.
(defmacro ^:private condcast [expr xsym & clauses]
  (let [[clauses default] (if (even? (count clauses))
                            [clauses nil] 
                            [(butlast clauses)
                             [:else (last clauses)]])
        exprsym (gensym "exprsym_")
        cs (->> clauses
             (partition 2)
             (mapcat
               (fn [[t then]]
                 `[(instance? ~t ~exprsym)
                   (let [~(with-meta xsym {:tag (resolve t)}) ~exprsym]
                     ~then)])))]
    `(let [~exprsym ~expr]
       ~(cons 'cond
          (concat cs default)))))

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

(defwrapper object-named GameObject Find
  "Finds a game object by name and returns it.
  
  * name - The name of the object to find, a String")

;; type-hinting of condcast isn't needed here, but seems a good habit to get into
(defn objects-named
  "Finds game objects by name.
  
  * name - the name of the objects to find, can be A String or regex"
  [name]
  (condcast name name
    System.String
    (for [^GameObject obj (objects-typed GameObject)
          :when (= (.name obj) name)]
      obj)
    
    System.Text.RegularExpressions.Regex
    (for [^GameObject obj (objects-typed GameObject)
          :when (re-matches name (.name obj))]
      obj)
    
    (throw (Exception. (str "Expects String or Regex, instead got " (type name))))))

(defwrapper object-tagged GameObject FindWithTag
  "Returns one active GameObject tagged tag. Returns null if no GameObject was found.
  
  * tag - the tag to seach for, a String")

(defwrapper objects-tagged GameObject FindGameObjectsWithTag
  "Returns a list of active GameObjects tagged tag. Returns empty array if no GameObject was found.
  
  * tag - the tag to seach for, a String")
