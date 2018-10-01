(ns arcadia.core
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            clojure.set
            [arcadia.internal.messages :as messages]
            [arcadia.internal.macro :as mac]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.name-utils :refer [camels-to-hyphens]]
            [arcadia.internal.state-help :as sh]
            arcadia.literals)
  (:import ArcadiaBehaviour
           ArcadiaBehaviour+IFnInfo
           ArcadiaState
           [Arcadia UnityStatusHelper
            Util
            HookStateSystem JumpMap
            JumpMap+KeyVal JumpMap+PartialArrayMapView
            DefmutableDictionary]
           [clojure.lang RT]
           [UnityEngine
            Vector3
            Quaternion
            Application
            MonoBehaviour
            GameObject
            Transform
            Component
            PrimitiveType
            Debug]
           [System Type]))

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


(defn obj-nil
  "Same as `identity`, except if x is a null UnityEngine.Object, will return nil."
  [x]
  (Util/TrueNil x))

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
  (^GameObject [prim] (create-primitive prim nil))
  (^GameObject [prim name]
   (let [prim' (if (instance? PrimitiveType prim)
                 prim
                 (case prim
                   :sphere   PrimitiveType/Sphere
                   :capsule  PrimitiveType/Capsule
                   :cylinder PrimitiveType/Cylinder
                   :cube     PrimitiveType/Cube
                   :plane    PrimitiveType/Plane
                   :quad     PrimitiveType/Quad))
         obj (GameObject/CreatePrimitive prim')]
     (when name
       (set! (.name obj) name))
     obj)))

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

(defn retire
  "If in Play mode, calls Destroy, otherwise calls DestroyImmediate."
  ([^UnityEngine.Object obj]
   (if UnityStatusHelper/IsInPlayMode
     (UnityEngine.Object/Destroy obj)
     (UnityEngine.Object/DestroyImmediate obj))))

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
  [^String name] `(UnityEngine.GameObject/Find ~name))

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
  [^String t] `(UnityEngine.GameObject/FindWithTag ~t))

(definline objects-tagged
  "Returns an array of active GameObjects tagged tag. Returns empty
  array if no GameObject was found. Tags are managed from the
  [Unity Tag Manager](https://docs.unity3d.com/Manual/class-TagManager.html).
  Wraps UnityEngine.GameObject/FindGameObjectsWithTag."
  [^String t] `(UnityEngine.GameObject/FindGameObjectsWithTag ~t))

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
    (when-let [p (.. this transform parent)]
      (.gameObject p)))
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
    ([^Component this child]
     (child+ (.gameObject this) child))
    ([^Component this, child, transform-to]
     (child+ (.gameObject this) child transform-to)))
  (child- [^Component this, child]
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
  ^UnityEngine.Component [obj ^Type t]
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
   (let [gobsym (gentagged "gob__" 'UnityEngine.GameObject)
         dcls  (->> cmpt-name-types
                 (partition 2)
                 (mapcat (fn [[n t]]
                           [(meta-tag n t) `(ensure-cmpt ~gobsym ~t)])))]
     `(with-gobj [~gobsym ~gob]
        (let [~@dcls]
          ~@body)))))

(defmacro if-cmpt
  "Execute body of code if `gob` has a component of type `cmpt-type`"
  [gob [cmpt-name cmpt-type] then & else]
  (let [gobsym (gentagged "gob__" 'UnityEngine.GameObject)]
    `(let [obj# ~gob]
       (if (obj-nil obj#)
         (with-gobj [~gobsym obj#]
           (if-let [~(meta-tag cmpt-name cmpt-type) (cmpt ~gobsym ~cmpt-type)]
             ~then
             ~@else))
         ~@else))))

;; ============================================================
;; traversal

(defn gobj-seq [x]
  (tree-seq identity children (gobj x)))


;; ============================================================
;; hooks

(defn- clojurized-keyword [m]
  (-> m str camels-to-hyphens string/lower-case keyword))

(defn- message-keyword [m]
  (clojurized-keyword m))

(def hook-types
  "Map of keywords to hook component types"
  (->> messages/all-messages
       keys
       (map name)
       (mapcat #(vector (message-keyword %)
                        (RT/classForName (str % "Hook"))))
       (apply hash-map)))

(defn- ensure-hook-type [hook]
  (or (hook-types hook)
      (throw (ArgumentException. (str hook " is not a valid Arcadia hook")))))

(s/def ::scenegraphable
  #(satisfies? ISceneGraph %))

(s/def ::hook-kw
  #(contains? hook-types %))

(s/fdef hook+
  :args (s/cat
          :obj ::scenegraphable
          :hook ::hook-kw
          :rest (s/alt
                  :n3 (s/cat
                        :f ifn?)
                  :n4+ (s/cat
                         :k any?
                         :f ifn?
                         :keys (s/? (s/nilable sequential?))))))

(defn hook+
  "Attach a Clojure function to a Unity message on `obj`. The function `f`
  will be invoked every time the message identified by `message-kw` is sent by Unity. `f`
  must have the same arity as the expected Unity message. When called with a key `k`
  this key can be passed to `message-kw-` to remove the function."
  ([obj message-kw f] (hook+ obj message-kw :default f))
  ([obj message-kw k f]
   (hook+ obj message-kw k f nil))
  ([obj message-kw k f {:keys [fast-keys]}]
   (let [fast-keys (or fast-keys
                       (when (instance? clojure.lang.IMeta f)
                         (::keys (meta f))))]
     ;; need to actually do something here
     ;; (when-not (empty? fast-keys) (println "fast-keys: " fast-keys))
     (let [hook-type (ensure-hook-type message-kw)
           ^ArcadiaBehaviour hook-cmpt (ensure-cmpt obj hook-type)]
       (.AddFunction hook-cmpt f k (into-array System.Object (cons k fast-keys)))
       obj))))

(defn hook-var [obj message-kw var]
  (if (var? var)
    (hook+ obj message-kw var var)
    (throw
      (clojure.lang.ExceptionInfo.
        (str "Expects var, instead got: " (class var))
        {:obj obj
         :message-kw message-kw
         :var var}))))

(defn hook-
  "Removes callback from GameObject `obj` on the Unity message
  corresponding to `message-kw` at `key`, if it exists. Reverse of

(hook+ obj message-kw key)

  If `key` is not supplied, `hook-` will use `:default` as the key.
  This is the same as

(hook- obj message-kw :default)."
  ([obj message-kw]
   (hook- obj message-kw :default))
  ([obj message-kw key]
   (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type message-kw))]
     (.RemoveFunction hook-cmpt key))
   nil))

(defn clear-hook
  "Removes all callbacks on the Unity message corresponding to
  `message-kw`, regardless of their keys."
  [obj message-kw]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type message-kw))]
    (.RemoveAllFunctions hook-cmpt))
  nil)

(defn hook
  "Retrieves a callback from a GameObject `obj`. `message-kw` is a
  keyword specifying the Unity message of the callback, and `key` is
  the key of the callback.
  
  In other words, retrieves any callback function attached via

(hook+ obj message-kw key callback)

  or the equivalent.

  If `key` is not supplied, `hook` uses `:default` as key. This is the same as

(hook obj message-kw :default)"
  [obj message-kw key]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type message-kw))]
    (get (.indexes hook-cmpt) key)))

;; are these necessary?

;; (defn hook-fns
;;   "Return the functions associated with `hook` on `obj`."
;;   [obj message-kw]
;;   (.fns (hook obj message-kw)))

;; (defn hooks 
;;   "Return all components for `hook` attached to `obj`"
;;   [obj hook]
;;   (let [hook-type (ensure-hook-type hook)]
;;     (cmpts obj hook-type)))

(defn hook?
  ([t hook] (= (type t)
               (ensure-hook-type hook)))
  ([t] (isa? (type t)
         ArcadiaBehaviour)))

;; ============================================================
;; ISnapShotable

;; see `defmutable` below
;; This protocol should be considered an internal, unstable
;; implementation detail of `snapshot` for now. Please refrain from
;; extending it.
(defprotocol ISnapshotable
  (snapshot [self]))

;; ============================================================
;; state

(defn- ensure-state ^ArcadiaState [go]
  (ensure-cmpt go ArcadiaState))

(defn state
  "Returns the state of object `go` at key `k`."
  ([gobj]
   (when-let [^ArcadiaState s (cmpt gobj ArcadiaState)]
     (persistent!
       (reduce (fn [m, ^Arcadia.JumpMap+KeyVal kv]
                 (assoc! m (.key kv) (.val kv)))
         (transient {})
         (.. s state KeyVals)))))
  ([gobj k]
   (Arcadia.HookStateSystem/Lookup gobj k)))

(declare mutable)

(defn- maybe-mutable [x]
  (if (and (map? x)
           (contains? x ::mutable-type))
    (mutable x)
    x))

(defn state+
  "Sets the state of object `go` to value `v` at key `k`. If no key is provided, "
  ([go v]
   (state+ go :default v))
  ([go k v]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (maybe-mutable v))
     go)))

(defn state-
  "Removes the state of object `go` at key `k`. If no key is provided,
  removes state at key `default`."
  ([go]
   (state- go :default))
  ([go k]
   (with-cmpt go [arcs ArcadiaState]
     (.Remove arcs k)
     go)))

(defn clear-state
  "Removes all state from the GameObject `go`."
  [go]
  (with-cmpt go [arcs ArcadiaState]
    (.Clear arcs)
    go))

(defn update-state
  "Updates the state of object `go` with function `f` and additional
  arguments `args` at key `k`. Args are applied in the same order as
  `clojure.core/update`."
  ([go k f]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (f (.ValueAtKey arcs k)))
     go))
  ([go k f x]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (f (.ValueAtKey arcs k) x))
     go))
  ([go k f x y]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (f (.ValueAtKey arcs k) x y))
     go))
  ([go k f x y z]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (f (.ValueAtKey arcs k) x y z))
     go))
  ([go k f x y z & args]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (apply f (.ValueAtKey arcs k) x y z args))
     go)))

;; ============================================================
;; roles 

(def ^:private hook-type->hook-type-key
  (clojure.set/map-invert hook-types))

(defn- hook->hook-type-key [hook]
  (get hook-type->hook-type-key (class hook)))

(def ^:private hook-type-key->fastkeys-key
  (let [ks (keys hook-types)]
    (zipmap ks (map #(keyword (str (name %) "-ks")) ks))))

;; sketched this while not connected to repl, check it all
(def ^:private hook-ks
  (let [ks (keys hook-types)]
    (zipmap ks
      (map #(keyword (str (name %) "-ks"))
        ks))))

(def ^:private hook-fastkeys
  (set (vals hook-type-key->fastkeys-key)))

;; spec isn't very informative for our role system, but the following
;; is at least true of it.
;; Note that ::role's also support :state, which can't really be
;; spec'd (could be anything)

(s/def ::role
  (s/and
    map?
    (s/coll-of
      (fn hook-type-keys-to-ifns [[k v]]
        (if (contains? hook-types k)
          (ifn? v)
          true)))
    (s/coll-of
      (fn hook-fastkeys-to-sequentials [[k v]]
        (if (contains? hook-fastkeys k)
          (sequential? v)
          true)))))

(defn role- [obj k]
  (reduce-kv
    (fn [_ ht _]
      (hook- obj ht k))
    nil
    hook-types)
  (state- obj k)
  obj)

(s/fdef role+
  :args (s/cat :obj #(satisfies? ISceneGraph %)
               :k any?
               :spec ::role)
  :ret any?
  :fn (fn [{:keys [obj]} ret]
        (= obj ret)))

(defn role+ [obj k spec]
  (role- obj k)
  (reduce-kv
    (fn [_ k2 v]
      (cond
        (hook-types k2)
        (hook+ obj k2 k v
          (when-let [ks (get spec (get hook-ks k2))]
            {:fast-keys ks}))

        (= :state k2)
        (state+ obj k (maybe-mutable v))))
    nil
    spec)
  obj)

(defn roles+ [obj spec]
  (reduce-kv role+ obj spec))

(defn roles- [obj ks]
  (reduce role- obj ks))

(s/fdef role
  :args (s/cat :obj #(satisfies? ISceneGraph %)
               :k any?)
  :ret ::role)

;; TODO: better name
;; also instance vs satisfies, here
;; maybe this should be a definterface or something
;; yeah definitely should be a definterface, this is just here for `defmutable`
(defn- maybe-snapshot [x]
  (if (instance? arcadia.core.ISnapshotable x)
    (snapshot x)
    x))

(defn- inner-role-step [bldg, ^ArcadiaBehaviour+IFnInfo inf, hook-type-key]
  (as-> bldg bldg
        (assoc bldg hook-type-key (.fn inf))
        (let [fk (.fastKeys inf)]
          (if (< 1 (count fk))
            (assoc bldg (hook-type-key->fastkeys-key hook-type-key)
              (vec (rest fk)))
            bldg))))

(defn role [obj k]
  (let [step (fn [bldg ^ArcadiaBehaviour ab]
               (let [hook-type-key (hook->hook-type-key ab)]
                 (reduce
                   (fn [bldg ^ArcadiaBehaviour+IFnInfo inf]
                     (if (= (.key inf) k)
                       (reduced
                         (inner-role-step bldg, inf, hook-type-key))
                       bldg))
                   bldg
                   (.ifnInfos ab))))
        init (if-let [s (state obj k)]
               {:state (maybe-snapshot s)}
               {})]
    (reduce step init (cmpts obj ArcadiaBehaviour))))

(defn- roles-step [bldg ^ArcadiaBehaviour ab]
  (let [hook-type-key (hook->hook-type-key ab)]
    (reduce
      (fn [bldg ^ArcadiaBehaviour+IFnInfo inf]
        (update bldg (.key inf)
          (fn [m]
            (inner-role-step m, inf, hook-type-key))))
      bldg
      (.ifnInfos ab))))

;; map from hook, state keys to role specs
(defn roles [obj]
  (let [init (if-let [^ArcadiaState arcs (cmpt obj ArcadiaState)]
               (reduce-kv
                 (fn [bldg k v]
                   (assoc-in bldg [k :state] (maybe-snapshot v)))
                 {}
                 (.ToPersistentMap arcs))
               {})]
    (reduce roles-step init (cmpts obj ArcadiaBehaviour))))

;; ------------------------------------------------------------
;; defrole

(def ^:private hook->args
  (-> messages/all-messages
      (mu/map-keys clojurized-keyword)))

(s/def ::defrole-impl
  (s/and
    (s/cat
      :name (s/and symbol? #(contains? hook-types (keyword (name %))))
      :args (s/coll-of any? :kind vector?)
      :body (s/* any?))
    (fn [{:keys [args], nm :name}]
      (= (count args) (+ 2 (count (hook->args (keyword (name nm)))))))))

(s/def ::defrole-args
  (s/cat
    :name symbol?
    :body (s/*
            (s/alt
              :impl ::defrole-impl
              :literal-impl (s/cat
                              :key #(contains? hook-types %)
                              :val any?)
              :state (s/cat :state-kw #{:state} :state any?)))))

(defn- defrole-map-entries [role-name body]
  (for [[kind entry] body]
    (case kind
      :state (let [{state :state} entry]
               {:kind kind
                :key :state
                :value state})
      :impl (let [{nm :name, :keys [args body]} entry
                  nm' (symbol (str role-name "-" (name nm)))]
              {:kind kind
               :key (clojurized-keyword nm)
               :value `(var ~nm')
               :def `(defn ~nm' "Role function generated by arcadia.core/defrole." ~args ~@body)})
      :literal-impl (let [{:keys [key val]} entry]
                      {:kind kind
                       :key key
                       :value val}))))

;; add documentation string 
(defmacro defrole
  "Macro for defining roles quickly.
Syntax:
(defrole name entry*)

Each entry can be either a key-value pair with a keyword key, such as would normally occur
  in a map intended as an Arcadia role, or an inlined function definition.

Normal key-value pairs get inserted into the generated map. For example,

(defrole movement
  :state {:speed 3}
  :update #'movement-update)

will expand into

(def movement
  {:state {:speed 3}
   :update #'movement-update})

Inlined function definitions have the following syntax:

(name [args*] body)

name must be the symbol form of an Arcadia hook keyword. A function
  intended for the `:update` hook, for example, should have the name
  `update`:

(defrole movement
  :state {:speed 3}
  (update [obj k] ...))

Each inlined function definition will **generate a var**, with a name
  constructed as follows:

<name of role>-<name of hook>

For example, the `movement` role above will generate a var named
  `movement-update` bound to a function with the provided arguments
  and body, and include that var in the generated role map, expanding
  into something like:

(do
  (defn movement-update [obj k] ...)
  (def movement
    {:state {:speed 3}
     :update #'movement-update}))

Note that generating vars is usually a bad idea because it messes with
  tooling and legibility. This macro does it anyway because the hook
  functions should serialize in the Unity scene graph, and that requires
  vars."
  [& defrole-args]
  (let [parse (s/conform ::defrole-args
                defrole-args)]
    (if (= ::s/invalid parse)
      (throw (Exception.
               (str "Invalid arguments to defrole. Spec explanation: "
                    (with-out-str (clojure.spec.alpha/explain ::defrole-args defrole-args)))))
      (let [{nm :name,
             body :body} parse
            entries (defrole-map-entries nm body)
            impl-defs (->> entries (filter #(= :impl (:kind %))) (map :def))
            role-map (into {}
                       (for [{:keys [key value]} entries]
                         [key value]))]
        `(do
           ~@impl-defs
           (def ~nm ~role-map))))))

;; ============================================================
;; defmutable

(s/def ::protocol-impl
  (s/cat
    :protocol-name symbol?
    :method-impls (s/*
                    (s/spec
                      (s/cat
                        :name symbol?
                        :args vector?
                        :body (s/* any?))))))

(s/def ::element-snapshots-impl
  (s/cat
    :args (s/and vector? #(= 2 (count %)))
    :body (s/* any?)))

(s/def ::element-snapshots-map
  (s/map-of
    #(or (symbol? %) (keyword? %))
    ::element-snapshots-impl))

(s/def ::default-element-snapshots-impl
  (s/spec
    (s/cat
      :args (s/and vector? #(= 3 (count %)))
      :body (s/* any?))))

(s/def ::more-opts
  (s/*
    (s/alt
      :element-snapshots-map (s/cat
                               :key #{:element-snapshots}
                               :element-snapshots ::element-snapshots-map)
      :default-element-snapshots (s/cat
                                   :key #{:default-element-snapshots}
                                   :default-element-snapshots-impl ::default-element-snapshots-impl))))

(s/def ::defmutable-args
  (s/cat
    :name symbol?
    :fields (s/coll-of symbol?, :kind vector?)
    :protocol-impls (s/* ::protocol-impl)
    :more-opts ::more-opts))

;; the symbol
(defn mutable-dispatch [{t ::mutable-type}]
  (cond (instance? System.Type t) (symbol (pr-str t))
        (symbol? t) t
        :else (throw
                (Exception.
                  (str "Expects type or symbol, instead got instance of " (class t))))))

;; constructor (no instance), so this has to be a multimethod
;; if we're sticking to clojure stuff
(defmulti mutable
  "Given a persistent representation of a mutable datatype defined via
  `defmutable`, constructs and returns a matching instance of that
  datatype. 

Roundtrips with `snapshot`; that is, for any instance `x` of a type defined via `defmutable`,
(= (snapshot x) (snapshot (mutable (snapshot x))))"
  #'mutable-dispatch)

(defprotocol IMutable
  (mut!
    [_]
    [_ _]
    [_ _ _]
    [_ _ _ _]
    [_ _ _ _ _]
    [_ _ _ _ _ _]
    [_ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]))

(defn- expand-type-sym [type-sym]
  (symbol
    (str
      (-> (name (ns-name *ns*))
          (clojure.string/replace "-" "_"))
      "."
      (name type-sym))))

(defn- snapshot-dictionary-form [this-sym fields element-snapshots-map default-element-snapshots]
  (let [dict-sym (gensym "dictionary_")
        key-sym (gensym "key_")
        val-sym (gensym "val_")
        this-sym-2 (gensym "this_")
        process-element-sym (gensym "process-element_")
        processed-field-kvs (apply concat
                              (for [field fields
                                    :let [cljf (clojurized-keyword field)]]
                                [cljf `(~process-element-sym
                                        ~this-sym
                                        ~cljf
                                        ~field)]))]
    `(let [~process-element-sym  (fn [~this-sym-2 ~key-sym ~val-sym] ; this function will be handed off to 
                                   (case ~key-sym ; .ToPersistentMap on the internal dictionary
                                     ~@(apply concat
                                         (for [[k, {[this-sym-3 val-sym-2] :args,
                                                    body :body}] element-snapshots-map]
                                           (let [rform `(let [~this-sym-3 ~this-sym-2
                                                              ~val-sym-2 ~val-sym]
                                                          ~@body)
                                                 k2 (if (keyword? k) k (clojurized-keyword k))]
                                             [k2 rform])))
                                     ~(if default-element-snapshots
                                        (let [{[this-sym-3 key-sym-2 val-sym-2] :args
                                               body :body} default-element-snapshots]
                                          `(let [~this-sym-3 ~this-sym-2
                                                 ~key-sym-2 ~key-sym
                                                 ~val-sym-2 ~val-sym]
                                             ~@body))
                                        val-sym)))
           ~dict-sym (.ToPersistentMap ~'defmutable-internal-dictionary ~this-sym ~process-element-sym)]       
       ~(if (seq fields)
          `(assoc ~dict-sym
             ~@processed-field-kvs)
          dict-sym))))

;; takes a symbol representing a type
(defmulti concrete-fields identity)

(defmulti concrete-field-kws identity)

(defn dynamic-element-map [type-symbol element-map]
  (apply dissoc element-map (concrete-field-kws type-symbol)))

(defn dynamic-element-dict [type-symbol element-map]
  (new Arcadia.DefmutableDictionary
    (dynamic-element-map type-symbol element-map)))

;; (defn field-snapshot-forms [this-sym fields element-snapshots-map default-element-snapshots]
;;   (for [field fields]
;;     (if-let [[_ {[arg-sym] :args
;;                  body :body}] (find element-snapshots-map field)]
;;       `(let [~arg-sym (cond
;;                         (symbol? field) field
;;                         (keyword? field) `(valAt ~this-sym field)
;;                         :else (throw (Exception. "Field must be keyword or symbol")))]
;;          ~@body)
;;       (if-let [[[this-sym-2 ]] default-element-snapshots]))))

;; note: there are 2 serialization formats for defmutable currently,
;; snapshot and what it prints as. one of those *could* preserve reference
;; information, maybe? risk of contradictory data though
(defmacro  ^{:arglists '([name [fields*]])}
  defmutable
  "Defines a new serializable, type-hinted, mutable datatype, intended
  for particularly performance or allocation sensitive operations on a
  single thread (such as Unity's main game thread). These datatypes
  support snapshotting to persistent data via `snapshot`, and
  reconstruction from snapshots via `mutable`; snapshotting and
  reconstructing are also integrated into `role+`, `state+`,
  `role`, and `roles`.

  Instances of these types may be converted into persistent
  representations and back via `snapshot` and `mutable`. This
  roundtrips, so if `x` is such an instance:

  (= (snapshot x) (snapshot (mutable (snapshot x))))

  If a persistent snapshot is specified as the state argument of
  `set-state`, or as the `:state` value in the map argument of
  `role+`, the `ArcadiaState` component will be populated at the
  appropriate key by the result of calling `mutable` on that
  snapshot. Conversely, `role` and `roles` will automatically convert
  any mutable instances that would otherwise be the values of `:state`
  in the returned map(s) to persistent snapshots.

  `defmutable` serialization, via either `snapshot` or Unity scene-graph
  serialization, does *not* currently preserve reference
  identity. Calling `mutable` on the same snapshot twice will result
  in two distinct instances. It is therefore important to store any
  given `defmutable` instance in just one place in the scene graph.

  Since they define new types, reevaluating `defmutable` forms will
  require also reevaluating all forms that refer to them via type
  hints (otherwise they'll fall back to dynamic
  lookups). `defmutable-once` is like `defmutable`, but will not
  redefine the type if it has already been defined (similar to
  `defonce`).

  As low-level, potentially non-boxing constructs, instances of
  `defmutable` types work particularly well with the `magic` library."
  [& args]
  (let [parse (s/conform ::defmutable-args args)]
    (if (= ::s/invalid parse)
      (throw (Exception.
               (str "Invalid arguments to defmutable. Spec explanation: "
                    (with-out-str (clojure.spec.alpha/explain ::defmutable-args args)))))
      (let [{:keys [name fields protocol-impls more-opts]} parse
            {{element-snapshots-map :element-snapshots} :element-snapshots-map
             default-element-snapshots :default-element-snapshots} (into {} more-opts)
            type-name (expand-type-sym name)
            param-sym (-> (gensym (str name "_"))
                          (with-meta {:tag type-name}))
            field-kws (map clojurized-keyword fields)
            datavec [type-name (zipmap field-kws fields)]
            dict-param (gensym "dict_")
            data-param (gensym "data_")
            ensure-internal-dictionary-form `(when (nil? ~'defmutable-internal-dictionary)
                                               (set! ~'defmutable-internal-dictionary
                                                 (new Arcadia.DefmutableDictionary)))
            
            mut-cases-form-fn (fn [this-sym [k v]]
                                `(case ~k
                                   ~@(mapcat
                                       (fn [field-kw, field]
                                         `[~field-kw (set! (. ~this-sym ~field) ~v)])
                                       field-kws
                                       fields)
                                   (do ~ensure-internal-dictionary-form
                                       (.Add ~'defmutable-internal-dictionary ~k ~v))))
            even-args-mut-impl (fn [[_ args]]
                                `(mut! ~args (throw (Exception. "requires odd number of arguments"))))
            mut-impl (mac/arities-forms
                       (fn [[this-sym & kvs]]
                         (let [val-sym (gensym "val_")
                               pairs (partition 2 kvs)]
                           `(mut! [~this-sym ~@kvs]
                              ~(when-let [lp (last pairs)]
                                 (mut-cases-form-fn this-sym (last pairs)))
                              (mut! ~this-sym ~@(apply concat (butlast pairs))))))
                       {::mac/arg-fn #(gensym (str "arg_" % "_"))
                        ::mac/min-args 1
                        ::mac/max-args 5
                        ::mac/cases (merge
                                      {1 (fn [& stuff]
                                           `(mut! [this#] this#))
                                       3 (fn [[_ [this-sym k v :as args]]]
                                           `(mut! ~args
                                              ~(mut-cases-form-fn this-sym [k v])
                                              ~this-sym))}
                                      (into {}
                                        (for [n (range 0 20 2)]
                                          [n even-args-mut-impl])))})
            ctr (symbol (str  "->" name))
            lookup-cases (mapcat list field-kws fields)
            this-sym (gensym "this_")
            protocol-impl-forms (apply concat
                                  (for [{:keys [protocol-name method-impls]} protocol-impls]
                                    (cons protocol-name
                                      (for [{:keys [name args body]} method-impls]
                                        (list* name args body)))))]
        `(do (declare ~ctr)
             (let [t# (deftype ~name ~(-> (->> fields (mapv #(vary-meta % assoc :unsynchronized-mutable true)))
                                          (conj
                                            (with-meta
                                              'defmutable-internal-dictionary
                                              {:unsynchronized-mutable true,
                                               :tag 'Arcadia.DefmutableDictionary})))
                        clojure.lang.ILookup
                        (valAt [this#, key#]
                          (case key#
                            ~@lookup-cases
                            (do ~ensure-internal-dictionary-form ;; macro
                                (.GetValue ~'defmutable-internal-dictionary key#))))
                        System.ICloneable
                        (Clone [_#]
                          (do ~ensure-internal-dictionary-form
                              (~ctr ~@fields (.Clone ~'defmutable-internal-dictionary))))
                        ISnapshotable
                        (snapshot [~this-sym]
                          ~ensure-internal-dictionary-form
                          {::mutable-type (quote ~type-name)
                           ::dictionary ~(snapshot-dictionary-form this-sym fields
                                           element-snapshots-map default-element-snapshots)})
                        IMutable
                        ~@mut-impl
                        ;; splice in any other protocol implementations, including overwrites for these defaults
                        ~@protocol-impl-forms)]
               ;; add an arity to the generated constructor
               ~(let [naked-fields (map #(vary-meta % dissoc :tag) fields)]
                  `(let [prev-fn# (var-get (var ~ctr))]
                     (defn ~ctr
                       ([~@naked-fields]
                        (~ctr ~@naked-fields (new Arcadia.DefmutableDictionary)))
                       ([~@naked-fields dict#]
                        (prev-fn# ~@naked-fields dict#)))))
               ;; like defrecord:
               ~(let [map-sym (gensym "data-map_")
                      field-vals (for [kw field-kws] `(get ~map-sym ~kw))]
                  `(defn ~(symbol (str "map->" name)) [~map-sym]
                     (~ctr ~@field-vals (new Arcadia.DefmutableDictionary (dissoc ~map-sym ~@field-kws)))))
               ;; serialize as dictionary
               (defmethod mutable (quote ~type-name) [~data-param]
                 ~(let [dict-sym  (gensym "dict_")
                        field-vals (for [kw field-kws] `(get ~dict-sym ~kw))]
                    `(let [~dict-sym (get ~data-param ::dictionary)]
                       (~ctr ~@field-vals (new Arcadia.DefmutableDictionary (dissoc ~dict-sym ~@field-kws))))))
               (defmethod concrete-fields (quote ~type-name) [_#]
                 (quote ~fields))
               (defmethod concrete-field-kws (quote ~type-name) [_#]
                 ~(mapv clojurized-keyword fields))
               (defmethod arcadia.literals/parse-user-type (quote ~type-name) [~dict-param]
                 (mutable ~dict-param))
               ~(let [field-map (zipmap field-kws
                                  (map (fn [field] `(. ~this-sym ~field)) fields))]
                 `(defmethod print-method ~type-name [~this-sym ^System.IO.TextWriter stream#]
                    (.Write stream#
                      (str "#arcadia.core/mutable " (pr-str (snapshot ~this-sym))))))
               t#))))))

(defmacro defmutable-once
  "Like `defmutable`, but will only evaluate if no type with the same name has been defined."
  [& [name :as args]]
  (when-not (resolve name)
    `(defmutable ~@args)))
