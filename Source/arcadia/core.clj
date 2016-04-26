(ns arcadia.core
  (:require [clojure.string :as string]
            [arcadia.internal.messages :refer [messages interface-messages]]
            [arcadia.internal.editor-interop :refer [camels-to-hyphens]]
            arcadia.literals
            [arcadia.internal.macro :as im])
  (:import ArcadiaBehaviour
           ArcadiaState
           [UnityEngine
            Vector3
            Quaternion
            Application
            MonoBehaviour
            GameObject
            Component
            PrimitiveType
            Debug]))

;; ============================================================
;; application
;; ============================================================

(defn log
  "Log message to the Unity console. Arguments are combined into a string."
  [& args]
  (Debug/Log (apply str args)))

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

(definline null-obj?
  "Is `x` nil?
  
  This test is complicated by the fact that Unity uses
  a custom null object that evaluates to `true` in normal circumstances.
  `null-obj?` will return `true` if `x` is nil or Unity's null object."
  [^UnityEngine.Object x]
  `(UnityEngine.Object/op_Equality ~x nil))


;; TODO better name
(definline obj-nil [x]
  `(let [x# ~x]
     (when-not (null-obj? x#) x#)))

;; ============================================================
;; wrappers
;; ============================================================

;; definline does not support arity overloaded functions... 
(defn instantiate
  "Clones the object original and returns the clone."
  {:inline (fn
             ([^UnityEngine.Object original]
              `(UnityEngine.Object/Instantiate ~original))
             ([^UnityEngine.Object original ^Vector3 position]
              `(UnityEngine.Object/Instantiate ~original ~position Quaternion/identity))
             ([^UnityEngine.Object original ^Vector3 position ^Quaternion rotation]
              `(UnityEngine.Object/Instantiate ~original ~position ~rotation)))}
  ([^UnityEngine.Object original]
   (UnityEngine.Object/Instantiate original))
  ([^UnityEngine.Object original ^Vector3 position]
   (UnityEngine.Object/Instantiate original position Quaternion/identity))
  ([^UnityEngine.Object original ^Vector3 position ^Quaternion rotation]
   (UnityEngine.Object/Instantiate original position rotation)))

(defn create-primitive
  "Creates a game object with a primitive mesh renderer and appropriate
  collider. `prim` can be a PrimitiveType or one of :sphere :capsule
  :cylinder :cube :plane :quad"
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
  "Removes a gameobject, component or asset. When called with `t`, the removal
  happens after `t` seconds."
  ([^UnityEngine.Object obj]
   (if (editor?)
    (UnityEngine.Object/DestroyImmediate obj)
    (UnityEngine.Object/Destroy obj)))
  ([^UnityEngine.Object obj ^double t]
   (UnityEngine.Object/Destroy obj t)))

(definline object-typed
  "Returns the first active loaded object of Type `type`."
  [^Type t] `(UnityEngine.Object/FindObjectOfType ~t))

(definline objects-typed
  "Returns an array of all active loaded objects of Type `type`."
  [^Type t] `(UnityEngine.Object/FindObjectsOfType ~t))

(definline object-named
  "Returns one GameObject named `name`."
  [^String name] `(GameObject/Find ~n))

(defn objects-named
  "Returns a sequence of all GameObjects named `name`."
  [name]
  (cond (= (type name) System.String)
        (for [^GameObject obj (objects-typed GameObject)
              :when (= (.name obj) name)]
          obj)
        
        (= (type name) System.Text.RegularExpressions.Regex)
        (for [^GameObject obj (objects-typed GameObject)
              :when (re-matches name (.name obj))]
          obj)))

(definline object-tagged
  "Returns one active GameObject tagged tag."
  [^String t] `(GameObject/FindWithTag ~t))

(definline objects-tagged
  "Returns an array of active GameObjects tagged tag. Returns empty array if no GameObject was found."
  [^String t] `(GameObject/FindGameObjectsWithTag ~t))

;; ------------------------------------------------------------
;; IEntityComponent

(defprotocol IEntityComponent
  "Common protocol for everything in Unity that supports attached
  components."
  (cmpt [this t]
    "Returns the first component typed `t` attached to the object")
  (cmpts [this t]
    "Returns a vector of all components typed `t` attached to the object")
  (cmpt+ [this t]
    "Adds a component of type `t` to the object and returns the new instance")
  (cmpt- [this t]
    "Removes all components of type `t` from the object and returns the object"))

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

(defn ensure-cmpt
  "If `obj` has a component of type `t`, returns is. Otherwise, adds
  a component of type `t` and returns the new instance."
  ^Component [obj ^Type t]
  (or (cmpt x t) (cmpt+ x t)))

;; ------------------------------------------------------------
;; ISceneGraph

(defprotocol ISceneGraph
  "Common protocol for everything in Unity that is part of the scene
  graph hierarchy."
  (gobj ^GameObject [this]
        "")
  (children [this]
    "Returns all objects under `this` object in the hierarchy.")
  (parent ^GameObject [this]
          "Returns the object that contains `this` object, or `nil` if it is
          at the top of the hierarchy.")
  (child+ ^GameObject
    [this child]
    [this child transform-to]
    "Moves `child` to under `this` object in the hierarchy, optionally
    recalculating its local transform.")
  (child- ^GameObject [this child]
          "Move `child` from under `this` object ti the top of the hierarchy"))

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
  "Execute body of code if `gob` has a component of type `cmpt-type`"
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


;; ============================================================
;; hooks

(defn- message-keyword [m]
  (-> m str camels-to-hyphens string/lower-case keyword))

(def hook-types
  "Map of keywords to hook component types"
  (->> (merge messages
              interface-messages)
       keys
       (map name)
       (mapcat #(vector (message-keyword %)
                        (RT/classForName (str % "Hook"))))
       (apply hash-map)))

(defn- ensure-hook-type [hook]
  (or (hook-types hook)
      (throw (ArgumentException. (str hook " is not a valid Arcadia hook")))))

(defn hook+
  "Attach hook a Clojure function to a Unity message on `obj`. The funciton `f`
  will be invoked every time the message identified by `hook` is sent by Unity. `f`
  must have the same arity as the expected Unity message."
  [obj hook f]
  (let [hook-type (ensure-hook-type hook)
        hook* (cmpt+ obj hook-type)]
    (set! (.fn hook*) f)
    obj))

(defn hook-
  "Remove all `hook` components attached to `obj`"
  [obj hook]
  (let [hook-type (ensure-hook-type hook)]
    (cmpt- obj hook-type)
    obj))

(defn hook
  "Return the `hook` component attached to `obj`. If there is more one component,
  then behavior is the same as `cmpt`."
  [obj hook]
  (let [hook-type (ensure-hook-type hook)]
    (cmpt obj hook-type)))

(defn hooks [obj hook]
  "Return all components for `hook` attached to `obj`"
  (let [hook-type (ensure-hook-type hook)]
    (cmpts obj hook-type)))

(defn hook?
  ([t hook] (= (type t)
               (ensure-hook-type hook)))
  ([t] (isa? (type t)
             ArcadiaBehaviour)))

;; ============================================================
;; state

(defn- initialize-state [go]
  (cmpt- go ArcadiaState)
  (let [c (cmpt+ go ArcadiaState)]
    (set! (.state c) (atom {}))
    c))

(defn- ensure-state [go]
  (or (cmpt go ArcadiaState)
      (initialize-state go)))

(defn state
  "Returns the state of object `go`."
  ([go] (state go ::anonymous))
  ([go kw]
   (if-let [c (cmpt go ArcadiaState)]
     (get (deref (.state c)) kw))))

(defn set-state
  "Sets the state of object `go`."
  ([go v] (set-state go ::anonymous v))
  ([go kw v]
   (let [c (ensure-state go)]
     (reset! (.state c) (assoc (deref (.state c)) kw v))
     v)))

(defn swap-state
  "Updates the state of object `go` with funciton `f`."
  ([go f] (swap-state go ::anonymous f))
  ([go kw f]
   (let [c (ensure-state go)
         s (state go kw)]
     (swap! (.state c)
            update-in [kw] f)
     (state go kw))))