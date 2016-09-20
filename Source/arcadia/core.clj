(ns arcadia.core
  (:require [clojure.string :as string]
            [arcadia.internal.messages :refer [messages interface-messages]]
            [arcadia.internal.name-utils :refer [camels-to-hyphens]]
            arcadia.literals)
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
  "Log message to the [Unity console](https://docs.unity3d.com/Manual/Console.html). Arguments are combined into a string."
  [& args]
  (Debug/Log (clojure.string/join " " args)))

; (defonce ^:private editor-available
;   (boolean
;     (try
;       (import 'UnityEditor.EditorApplication)
;       (catch NullReferenceException e
;         nil))))

;; can't use the obvious macro, because we want this logic to avoid
;; being expanded away at AOT
;; however we end up dealing with eval will have to at least allow it
;; to show up in code
; (def ^:private in-editor
;   (if editor-available
;     (eval `(UnityEditor.EditorApplication/isPlaying))
;     false))

; (defn editor? 
;   "Returns true if called from within the editor. Notably, calls
;   from the REPL are considered to be form within the editor"
;   []
;   in-editor)

;; ============================================================
;; null obj stuff

(definline null-obj?
  "Is `x` nil?
  
  This test is complicated by the fact that Unity uses
  a custom null object that evaluates to `true` in normal circumstances.
  `null-obj?` will return `true` if `x` is nil *or* Unity's null object."
  [^UnityEngine.Object x]
  `(UnityEngine.Object/op_Equality ~x nil))


;; TODO better name
(definline obj-nil
  "Inlined version of null-obj? Could be merged in the future."
  [x]
  `(let [x# ~x]
     (when-not (null-obj? x#) x#)))

;; ============================================================
;; wrappers
;; ============================================================

;; definline does not support arity overloaded functions... 
(defn instantiate
  "Clones the original object and returns the clone. The clone can
  optionally be given a new position or rotation as well. Wraps
  UnityEngine.Object/Instantiate."
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
  :cylinder :cube :plane :quad. Wraps GameObject/CreatePrimitive."
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
  happens after `t` seconds. Wraps UnityEngine.Object/Destroy.
  
  The difference between destroy and destroy-immediate is still being worked out."
  ([^UnityEngine.Object obj]
   (UnityEngine.Object/Destroy obj))
  ([^UnityEngine.Object obj ^double t]
   (UnityEngine.Object/Destroy obj t)))

(defn destroy-immediate
  "Wraps UnityEngine.Object/DestroyImmediate.
  
  The difference between destroy and destroy-immediate is still being worked out."
  [^UnityEngine.Object obj]
  (UnityEngine.Object/DestroyImmediate obj))

(definline object-typed
  "Returns one object of Type `type`. The object selected seems to be
  the first object in the array returned by objects-typed.
  Wraps UnityEngine.Object/FindObjectOfType."
  [^Type t] `(UnityEngine.Object/FindObjectOfType ~t))

(definline objects-typed
  "Returns an array of all active loaded objects of Type `type`. The order is consistent
  but undefined. UnityEngine.Object/FindObjectsOfType."
  [^Type t] `(UnityEngine.Object/FindObjectsOfType ~t))

(definline object-named
  "Returns one GameObject named `name`. Wraps UnityEngine.GameObject/Find."
  [^String name] `(GameObject/Find ~name))

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
  "Returns one active GameObject tagged `tag`. Tags are managed from the
  [Unity Tag Manager](https://docs.unity3d.com/Manual/class-TagManager.html).
  Wraps UnityEngine.GameObject/FindWithTag."
  [^String t] `(GameObject/FindWithTag ~t))

(definline objects-tagged
  "Returns an array of active GameObjects tagged tag. Returns empty
  array if no GameObject was found. Tags are managed from the
  [Unity Tag Manager](https://docs.unity3d.com/Manual/class-TagManager.html).
  Wraps UnityEngine.GameObject/FindGameObjectsWithTag."
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
  (child+ 
    ^GameObject [this child]
    ^GameObject [this child transform-to]
    "Moves `child` to under `this` object in the hierarchy, optionally
    recalculating its local transform.")
  (child- ^GameObject [this child]
          "Move `child` from under `this` object ti the top of the hierarchy"))

(extend-protocol ISceneGraph
  GameObject
  (gobj [this]
    this)
  (children [this]
    (into []
      (map (fn [^Transform tr] (.gameObject tr)))
      (.transform this)))
  (parent [this]
    (.. this transform parent gameObject))
  (child+
    ([this child]
     (child+ this child false))
    ([this child transform-to]
     (let [^GameObject c (gobj child)]
       (.SetParent (.transform c) (.transform this) transform-to)
       this)))
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
  (child+
    ([this child]
     (child+ (.gameObject this) child))
    ([this child transform-to]
     (child+ (.gameObject this) child transform-to)))
  (child- [this child]
    (child- (.gameObject this) child))

  clojure.lang.Var
  (gobj [this]
    (gobj (var-get this)))
  (children [this]
    (children (var-get this)))
  (parent [this]
    (parent (var-get this)))
  (child+
    ([this child]
     (child+ (var-get this) child))
    ([this child transform-to]
     (child+ (var-get this) child transform-to)))
  (child- [this child]
    (child- (var-get this) child)))

;; ------------------------------------------------------------
;; repercussions

(defn ensure-cmpt
  "If `obj` has a component of type `t`, returns is. Otherwise, adds
  a component of type `t` and returns the new instance."
  ^Component [obj ^Type t]
  (let [obj (gobj obj)]
    (or (cmpt obj t) (cmpt+ obj t))))

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
     `(with-gobj [~gobsym ~gob]
        (let [~@dcls]
          ~@body)))))

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
  must have the same arity as the expected Unity message. When called with a key `k`
  this key can be passed to `hook-` to remove the function."
  ([obj hook f] (hook+ obj hook f f))
  ([obj hook k f]
   (let [hook-type (ensure-hook-type hook)
         ^ArcadiaBehaviour hook-cmpt (ensure-cmpt obj hook-type)]
     (.AddFunction hook-cmpt f k)
     obj)))

(defn hook-
  "Remove all `hook` components attached to `obj`"
  [obj hook k]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type hook))]
    (.RemoveFunction hook-cmpt k))
  obj)

(defn hook-clear
  "Remove all functions hooked to `hook` on `obj`"
  [obj hook]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type hook))]
    (.RemoveAllFunctions hook-cmpt))
  obj)

(defn hook
  "Return the `hook` component attached to `obj`. If there is more one component,
  then behavior is the same as `cmpt`."
  [obj hook]
  (let [hook-type (ensure-hook-type hook)]
    (cmpt obj hook-type)))

(defn hook-fns
  "Return the functions associated with `hook` on `obj`."
  [obj h]
  (.fns (hook obj h)))

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
  ([go kw]
   (kw (state go)))
  ([go] (if-let [c (ensure-state go)]
          (deref (.state c)))))

(defn set-state!
  "Updates the state of object `go` with funciton `f`."
  ([go kw v]
   (let [c (ensure-state go)]
     (swap! (.state c) assoc kw v))))

(defn remove-state!
  "Updates the state of object `go` with funciton `f`."
  ([go kw]
   (let [c (ensure-state go)]
     (swap! (.state c) dissoc kw))))

(defn update-state!
  "Updates the state of object `go` with funciton `f`."
  ([go kw f & args]
   (let [c (ensure-state go)]
     (apply swap! (.state c) update kw f args))))
