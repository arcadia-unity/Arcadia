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
            PrimitiveType]))

;; ============================================================
;; application
;; ============================================================

(defonce ^:private editor-available
  (boolean
    (try
      (import 'UnityEditor.EditorApplication)
      (catch NullReferenceException e
        nil))))

;; can't use the obvious macro, because we want this logic to avoid
;; being expanded away at AOT
;; however we end up dealing with eval will have to at least allow it
;; to show up in code
(def ^:private in-editor
  (if editor-available
    (eval `(UnityEditor.EditorApplication/isPlaying))
    false))

(defn editor? 
  "Returns true if called from within the editor. Notably, calls
  from the REPL are considered to be form within the editor"
  []
  in-editor)

;; ============================================================
;; null obj stuff

(defn null-obj? [^UnityEngine.Object x]
  (UnityEngine.Object/op_Equality x nil))


;; TODO better name
(definline obj-nil [x]
  `(let [x# ~x]
     (when-not (null-obj? x#) x#)))

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
              (str "expects type or symbol")))))

(defn- tag-type [x]
  (when-let [t (:tag (meta x))]
    (ensure-type t)))

(defn- type-of-reference [x env]
  (or (tag-type x)
    (and (symbol? x)
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
  (let [etype (most-specific-type
                (type-of-reference expr env)
                (tag-type xsym))]
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

;; note this takes an optional default value. This macro is potentially
;; annoying in the case that you want to branch on a supertype, for
;; instance, but the cast would remove interface information. Use with
;; this in mind.
(defmacro condcast-> [expr xsym & clauses]
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
      `(let [~xsym ~expr]
         ~default) ;; might be nil obvi

      (= 1 (count clauses)) ;; corresponds to exact type match. janky but fine
      `(let [~xsym ~expr]
         ~@clauses)

      :else
      `(let [~xsym ~expr]
         (cond
           ~@(->> clauses
               (partition 2)
               (mapcat
                 (fn [[t then]]
                   `[(instance? ~t ~xsym)
                     (let [~(with-meta xsym {:tag t}) ~xsym]
                       ~then)]))))))))

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn- dedup-by [f coll]
  (map peek (vals (group-by f coll))))

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

(defn- gc-default-body [obj t]
  `(condcast-> ~t t2#
     Type   (condcast-> ~obj obj2#
              UnityEngine.GameObject (.GetComponent obj2# t2#)
              UnityEngine.Component (.GetComponent obj2# t2#))
     String (condcast-> ~obj obj2#
              UnityEngine.GameObject (.GetComponent obj2# t2#)
              UnityEngine.Component (.GetComponent obj2# t2#))))

(defmacro ^:private gc-default-body-mac [obj t]
  (gc-default-body obj t))

(defn- gc-rt [obj t env]
  (let [implref (known-implementer-reference? obj 'GetComponent env)
        t-tr (type-of-reference t env)
        bod  (cond
               (isa? t-tr Type)
               (if implref
                 `(.GetComponent ~obj (~'type-args ~t))
                 `(condcast-> ~obj obj#
                    UnityEngine.GameObject (.GetComponent obj# (~'type-args ~t))
                    UnityEngine.Component (.GetComponent obj# (~'type-args ~t))))

               (isa? t-tr String)
               (if implref
                 `(.GetComponent ~obj ~t)
                 `(condcast-> ~obj obj#
                    UnityEngine.GameObject (.GetComponent obj# ~t)
                    UnityEngine.Component (.GetComponent obj# ~t)))
               
               :else
               (if implref
                 `(condcast-> ~t t#
                    Type (.GetComponent ~obj t#)
                    String (.GetComponent ~obj t#))
                 (gc-default-body obj t)))]
    `(let [retval# ~bod]
       (when (not (null-obj? retval#)) retval#))))

(defmacro get-component* [obj t]
  (if (not-every? symbol? [obj t])
    (raise-non-symbol-args
      (list 'arcadia.core/get-component* obj t))
    (gc-rt obj t &env)))

(defn get-component
  "Returns the component of Type type if the game object has one attached, nil if it doesn't.
  
  * obj - the object to query, a GameObject or Component
  * type - the type of the component to get, a Type or String"
  {:inline (fn [gameobject type]
             (list 'arcadia.core/get-component* gameobject type))
   :inline-arities #{2}}
  [obj t]
  (let [retval (gc-default-body-mac obj t)]
    (when (not (null-obj? retval))
      retval)))

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
;; (this is obsolete now)

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

(defwrapper object-named GameObject Find
  "Finds a game object by name and returns it.
  
  * name - The name of the object to find, a String")

;; type-hinting of condcast isn't needed here, but seems a good habit to get into
(defn objects-named
  "Finds game objects by name.
  
  * name - the name of the objects to find, can be A String or regex"
  [name]
  (condcast-> name name
    System.String
    (for [^GameObject obj (objects-typed GameObject)
          :when (= (.name obj) name)]
      obj)
    
    System.Text.RegularExpressions.Regex
    (for [^GameObject obj (objects-typed GameObject)
          :when (re-matches name (.name obj))]
      obj)
    
   ; (throw (Exception. (str "Expects String or Regex, instead got " (type name))))
    ))

(defwrapper object-tagged GameObject FindWithTag
  "Returns one active GameObject tagged tag. Returns null if no GameObject was found.
  
  * tag - the tag to seach for, a String")

(defwrapper objects-tagged GameObject FindGameObjectsWithTag
  "Returns a list of active GameObjects tagged tag. Returns empty array if no GameObject was found.
  
  * tag - the tag to seach for, a String")


;; ============================================================
;; protocols are fast now, so this:


;; ------------------------------------------------------------
;; IEntityComponent

(defprotocol IEntityComponent
  (cmpt [this t])
  (cmpts [this t])
  (cmpt+ [this t])
  (cmpt- [this t]))

(defmacro ^:private do-reduce [[x coll] & body]
  `(do
     (reduce
       (fn [_# ~x]
         ~@body
         nil)
       ~coll)
     nil))

(defmacro ^:private do-components [[x access] & body]
  `(let [^|UnityEngine.Component[]| ar# ~access
         c# (int (count ar#))]
     (loop [i# (int 0)]
       (when (< i# c#)
         (let [^Component ~x (aget ar# i#)]
           (do ~@body)
           (recur (inc i#)))))))

(extend-protocol IEntityComponent
  GameObject
  (cmpt [this t]
    (obj-nil (.GetComponent this t)))
  (cmpts [this t]
    (into [] (.GetComponents this t)))
  (cmpt+ [this t]
    (.AddComponent this t))
  (cmpt- [this t]
    (do-components [x (.GetComponents this t)]
      (destroy x)))

  ;; exactly the same:
  Component
  (cmpt [this t]
    (obj-nil (.GetComponent this t)))
  (cmpts [this t]
    (into [] (.GetComponents this t)))
  (cmpt+ [this t]
    (.AddComponent this t))
  (cmpt- [this t]
    (do-components [x (.GetComponents this t)]
      (destroy x))) 
  
  clojure.lang.Var
  (cmpt [this t]
    (cmpt (var-get this) t))
  (cmpts [this t]
    (cmpts (var-get this) t))
  (cmpt+ [this t]
    (cmpt+ (var-get this) t))
  (cmpt- [this t]
    (cmpt- (var-get this) t)))

;; ------------------------------------------------------------
;; repercussions

(defn ensure-cmpt ^Component [x ^Type t]
  (or (cmpt x t) (cmpt+ x t)))

;; ------------------------------------------------------------
;; ISceneGraph

(defprotocol ISceneGraph
  (gobj ^GameObject [this])
  (children [this])
  (parent ^GameObject [this])
  (child+ ^GameObject
    [this child]
    [this child transform-to])
  (child- ^GameObject [this child]))

(extend-protocol ISceneGraph
  GameObject
  (gobj [this]
    this)
  (children [this]
    (into [] (.transform this)))
  (parent [this]
    (.. this parent GameObject))
  (child+ [this child]
    (child+ this child false))
  (child+ [this child transform-to]
    (let [^GameObject c (gobj child)]
      (.SetParent (.transform c) (.transform this) transform-to)
      this))
  (child- [this child]
    (let [^GameObject c (gobj child)]
      (.SetParent (.transform c) nil false)
      this))

  Component
  (gobj [^Component this]
    (.gameObject this))
  (children [^Component this]
    (into [] (.. this gameObject transform)))
  (parent [^Component this]
    (.. this gameObject parent))
  (child+ [this child]
    (child+ (.gameObject this) child))
  (child+ [this child transform-to]
    (child+ (.gameObject this) child transform-to))
  (child- [this child]
    (child- (.gameObject this) child))

  clojure.lang.Var
  (gobj [this]
    (gobj (var-get this)))
  (children [this]
    (children (var-get this)))
  (parent [this]
    (parent (var-get this)))
  (child+ [this child]
    (child+ (var-get this) child))
  (child+ [this child transform-to]
    (child+ (var-get this) child transform-to))
  (child- [this child]
    (child- (var-get this) child)))

;; ------------------------------------------------------------
;; happy macros

(defn- meta-tag [x t]
  (vary-meta x assoc :tag t))

(defn- gentagged
  ([t]
   (meta-tag (gensym) t))
  ([s t]
   (meta-tag (gensym s) t)))

(defmacro with-gobj [[gob-name x] & body]
  `(let [~gob-name (gobj ~x)]
     ~@body))

(defmacro with-cmpt
  ([gob cmpt-name-types & body]
   (assert (vector? cmpt-name-types))
   (assert (even? (count cmpt-name-types)))
   (let [gobsym (gentagged "gob__" 'GameObject)
         dcls  (->> cmpt-name-types
                 (partition 2)
                 (mapcat (fn [[n t]]
                           [(meta-tag n t) `(cmpt ~gobsym ~t)])))]
     `(with-gob [~gobsym ~gob]
        (let [~@dcls]
          ~body)))))

(defmacro if-cmpt
  ([gob [cmpt-name cmpt-type] then]
   `(with-cmpt ~gob [~cmpt-name ~cmpt-type]
      (when ~cmpt-name
        ~then)))
  ([gob [cmpt-name cmpt-type] then else]
   `(with-cmpt ~gob [~cmpt-name ~cmpt-type]
      (if ~cmpt-name
        ~then
        ~else))))

;; ============================================================
;; traversal

(defn gobj-seq [x]
  (tree-seq identity children (gobj x)))
