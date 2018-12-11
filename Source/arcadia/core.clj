(ns arcadia.core
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            clojure.set
            [arcadia.internal.events :as events]
            [arcadia.internal.macro :as mac]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.name-utils :refer [camels-to-hyphens]]
            [arcadia.internal.state-help :as sh]
            arcadia.data)
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

(defn null->nil
  "Same as `identity`, except if `x` is a null `UnityEngine.Object`,
  will return `nil`.

  More details and rationale are available in [the wiki](https://github.com/arcadia-unity/Arcadia/wiki/Null,-Nil,-and-UnityEngine.Object)."
  [x]
  (Util/TrueNil x))

(defn null?
  "Should `x` be considered `nil`? `(null? x)` will evalute to `true`
  if `x` is in fact `nil`, or if `x` is a `UnityEngine.Object` instance
  such that `(UnityEngine.Object/op_Equality x nil)` returns `true`.

  More details and rationale are available in [the wiki](https://github.com/arcadia-unity/Arcadia/wiki/Null,-Nil,-and-UnityEngine.Object)."
  [x]
  (Util/IsNull x))

;; ============================================================
;; wrappers
;; ============================================================

;; definline does not support arity overloaded functions...
(defn instantiate
  "Clones the original object and returns the clone. The clone can
  optionally be given a new position or rotation as well.

 Wraps `Object/Instantiate`."
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
  collider. `prim` can be a `PrimitiveType` or one of `:sphere` `:capsule`
  `:cylinder` `:cube` `:plane` `:quad`. Wraps `GameObject/CreatePrimitive`."
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
  "Removes a GameObject, component or asset. When called with `t`, the removal
  happens after `t` seconds. Wraps `Object/Destroy`."
  ([^UnityEngine.Object obj]
   (UnityEngine.Object/Destroy obj))
  ([^UnityEngine.Object obj ^double t]
   (UnityEngine.Object/Destroy obj t)))

(defn destroy-immediate
  "Removes a GameObject, component or asset immediately.
   Wraps `Object/DestroyImmediate`."
  [^UnityEngine.Object obj]
  (UnityEngine.Object/DestroyImmediate obj))

(defn retire
  "If in Play mode, calls `Object/Destroy`, otherwise calls `Object/DestroyImmediate`."
  ([^UnityEngine.Object obj]
   (if UnityStatusHelper/IsInPlayMode
     (UnityEngine.Object/Destroy obj)
     (UnityEngine.Object/DestroyImmediate obj))))

(definline object-typed
  "Returns one object of Type `t`. The object selected seems to be
  the first object in the array returned by `objects-typed`.
  Wraps `Object/FindObjectOfType`."
  [^Type t] `(UnityEngine.Object/FindObjectOfType ~t))

(definline objects-typed
  "Returns an array of all active loaded objects of Type `t`. The order is consistent
  but undefined. Wraps `Object/FindObjectsOfType`."
  [^Type t] `(UnityEngine.Object/FindObjectsOfType ~t))

(definline object-named
  "Returns one `GameObject` named `name`. Wraps `GameObject/Find`."
  [^String name] `(UnityEngine.GameObject/Find ~name))

(defn objects-named
  "Returns a sequence of all `GameObject`s named `name`. `name` can be a string or a regular expression."
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
  "Returns one active `GameObject` tagged `t`. Tags are managed from the
  [Unity Tag Manager](https://docs.unity3d.com/Manual/class-TagManager.html).
  Wraps `GameObject/FindWithTag`."
  [^String t] `(UnityEngine.GameObject/FindWithTag ~t))

(definline objects-tagged
  "Returns an array of active `GameObject`s tagged tag. Returns empty
  array if no `GameObject` was found. Tags are managed from the
  [Unity Tag Manager](https://docs.unity3d.com/Manual/class-TagManager.html).
  Wraps `GameObject/FindGameObjectsWithTag`."
  [^String t] `(UnityEngine.GameObject/FindGameObjectsWithTag ~t))

;; ------------------------------------------------------------
;; Scene graph traversal and manipulation

(extend-protocol clojure.core.protocols/CollReduce
  UnityEngine.GameObject
  (coll-reduce [coll f]
    (coll-reduce coll f (f)))
  (coll-reduce [coll f val]
    (let [^Transform tr (.transform ^GameObject coll)
          e (.GetEnumerator tr)]      
      (loop [ret val]
        (if (.MoveNext e)
          (let [^Transform tr2 (.Current e)
                ret (f ret (.gameObject tr2))]
            (if (reduced? ret)
              @ret
              (recur ret)))
          ret)))))

(defn gobj
  "Coerces `x`, expected to be a `GameObject` or `Component`, to a
  corresponding live (non-destroyed) `GameObject` instance or to nil by
  the following policy:

  - If `x` is a live GameObject, returns it.
  - If `x` is a destroyed GameObject, returns nil.
  - If `x` is a live Component instance, returns its containing GameObject.
  - If `x` is a destroyed Component instance, returns nil.
  - If `x` is nil, returns `nil`.
  - Otherwise throws an ArgumentException."
  ^GameObject [x]
  (Util/ToGameObject x))

(defmacro ^:private gobj-arg-fail-exception [param]
  (let [param-str (name param)]
    `(if (some? ~param)
       (throw
         (ArgumentException.
           (str
             "Expects non-destroyed instance of UnityEngine.GameObject or UnityEngine.Component, instead received destroyed instance of "
             (class ~param))
           ~param-str))
       (throw
         (ArgumentNullException.
           ~param-str ; the message and the param are backwards in this subclass for some reason
           "Expects instance of UnityEngine.GameObject or UnityEngine.Component, instead received nil")))))

;; Should this return the parent or the child?
;; child- should return either the gameobject or nil,
;; more consistent with that to return the parent,
;; unless we want to change child- to return nil
;; just to keep the api consistent.
;; otoh cmpt+ returns the component (and HAS to).
;; assoc returns the new map, of course.
;; aset returns the val. and method chaining
;; isn't a strong idiom in Clojure.
(defn child+
  "Makes `x` the new parent of `child`. `x` and `child` can be `GameObject`s or
  `Component`s.

  If `world-position-stays` is true then `child` retains its world position after
  being reparented."
  (^GameObject [x child]
   (child+ x child false))
  (^GameObject [x child world-position-stays]
   (if-let [x (gobj x)]
     (if-let [child (gobj child)]
       (.SetParent
         (.transform (gobj child))
         (.transform (gobj x))
         ^Boolean world-position-stays)
       (gobj-arg-fail-exception child))
     (gobj-arg-fail-exception x))
   child))

(defn child-
  "Removes `x` as the parent of `child`. `x` and `child` can be `GameObject`s or
  `Component`s.

  The new parent of `child` becomes  `nil` and it is moved to the top level of
  the scene hierarchy.

  If `world-position-stays` is true then `child` retains its world position after
  being reparented."
  ([x child]
   (child- x child false))
  ([x child world-position-stays]
   (if-let [^GameObject x (gobj x)]
     (if-let [^GameObject child (gobj child)]
       (when (= (.parent child) x)
         (.SetParent (.transform child) nil ^Boolean world-position-stays))
       (gobj-arg-fail-exception child))
     (gobj-arg-fail-exception x))
   x))

;; `nil` semantics of this one is a little tricky.
;; It seems like a query function, which normally
;; suggests nil should be supported, but we can't
;; traverse the children of nulled game objects in
;; unity, and in a sense it's incorrect to offer an
;; empty vector for them either, since that asserts
;; the nulled game object in fact has no children,
;; rather than that the children are inaccessible.
;; We could return nil for that and vectors for other things
;; I suppose.
(defn children
  "Gets the children of `x` as a persistent vector. `x` can be a `GameObject` or
  a `Component`."
  [x]
  (if-let [^GameObject x (gobj x)]
    (persistent!
      (reduce
        (fn [acc ^UnityEngine.Transform x]
          (conj! acc (.gameObject x)))
        (transient [])
        (.transform x)))
    (gobj-arg-fail-exception x)))

;; ------------------------------------------------------------
;; IEntityComponent

;; TODO: get rid of this forward declaration by promoting ISceneGraph functions
;; above this

(defn cmpt
  "Returns the first `Component` of type `t` attached to `x`. Returns `nil` if no
  such component is attached. `x` can be a `GameObject` or `Component`."
  ^UnityEngine.Component [x ^Type t]
  (if-let [x (gobj x)]
    (null->nil (.GetComponent x t))
    (gobj-arg-fail-exception x)))

(defn cmpts
  "Returns all `Component`s of type `t` attached to `x`
  as a (possibly empty) array. `x` can be a `GameObject` or `Component`."
  ^|UnityEngine.Component[]| [x ^Type t]
  (if-let [x (gobj x)]
    (.GetComponents x t)
    (gobj-arg-fail-exception x)))

(defn cmpt+
  "Adds a new `Component` of type `t` from `x`. `x` can be a `GameObject` or
  Component. Returns the new `Component`."
  ^UnityEngine.Component [x ^Type t]
  (if-let [x (gobj x)]
    (.AddComponent x t)
    (gobj-arg-fail-exception x)))

;; returns nil because returning x would be inconsistent with cmpt+,
;; which must return the new component
(defn cmpt-
  "Removes *every* `Component` of type `t` from `x`. `x` can be a `GameObject` or
  Component. Returns `nil`."
  [x ^Type t]
  (if-let [x (gobj x)]
    (let [^|UnityEngine.Component[]| a (.GetComponents (gobj x) t)]
      (loop [i (int 0)]
        (when (< i (count a))
          (retire (aget a i))
          (recur (inc i)))))
    (gobj-arg-fail-exception x)))

;; ------------------------------------------------------------
;; repercussions

(defn ensure-cmpt
  "If `GameObject` `x` has a component of type `t`, returns it. Otherwise, adds
  a component of type `t` and returns the new instance."
  ^UnityEngine.Component [x ^Type t]
  (if-let [x (gobj x)]
    (or (cmpt x t) (cmpt+ x t))
    (gobj-arg-fail-exception x)))

;; ------------------------------------------------------------
;; sugar macros

(defn- meta-tag [x t]
  (vary-meta x assoc :tag t))

(defn- gentagged
  ([t]
   (meta-tag (gensym) t))
  ([s t]
   (meta-tag (gensym s) t)))

(defmacro with-gobj
  "Bind the `GameObject` `x` to the name `gob-name` in `body`. If `x` is a
  Component its attached `GameObject` is used."
  [[gob-name x] & body]
  `(let [~gob-name (gobj ~x)]
     ~@body))

(defmacro with-cmpt
  "`binding => name component-type`

  For each binding, looks up `component-type` on `gob` and binds it to `name`. If
  Component does not exist, it is created and bound to `name`. Evalutes `body`
  in the lexical context of all `name`s."
  ([gob bindings & body]
   (assert (vector? bindings))
   (assert (even? (count bindings)))
   (let [gobsym (gentagged "gob__" 'UnityEngine.GameObject)
         dcls  (->> bindings
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
       (if (null->nil obj#)
         (with-gobj [~gobsym obj#]
           (if-let [~(meta-tag cmpt-name cmpt-type) (cmpt ~gobsym ~cmpt-type)]
             ~then
             ~@else))
         ~@else))))

;; ============================================================
;; traversal

(defn descendents
  "Returns a sequence of `x`'s children. `x` can be a `GameObject` or a `Component`."
  [x]
  (tree-seq identity children (gobj x)))

;; ============================================================
;; hooks

(defn- clojurized-keyword [m]
  (-> m str camels-to-hyphens string/lower-case keyword))

(def ^:private hook-types
  "Map of keywords to hook component types. Unstable."
  (->> events/all-events
       keys
       (map name)
       (map #(do [(clojurized-keyword %), (RT/classForName (str % "Hook"))]))
       (into {})))

(defn available-hooks
  "Returns a sorted seq of all permissible hook keywords."
  []
  (sort (keys hook-types)))

(defn- ensure-hook-type [hook]
  (or (get hook-types hook)
      (throw (ArgumentException. (str hook " is not a valid Arcadia hook")))))

(s/def ::scenegraphable
  #(or (instance? GameObject %)
       (instance? Component %)))

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
  "Attach a Clojure function to a Unity event on `obj`. The function `f`
  will be invoked every time the event identified by `event-kw` is sent by Unity. `f`
  must have the same arity as the expected Unity event. When called with a key `k`
  this key can be passed to `event-kw-` to remove the function."
  ([obj event-kw k f]
   (let [hook-type (ensure-hook-type event-kw)
         ^ArcadiaBehaviour hook-cmpt (ensure-cmpt obj hook-type)]
     (.AddFunction hook-cmpt k f)
     obj)))

(defn hook-
  "Removes callback from `GameObject` `obj` on the Unity event
  corresponding to `event-kw` at `key`, if it exists. Reverse of

```clj
(hook+ obj event-kw key)
```

  Returns `nil`."
  ([obj event-kw key]
   (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
     (.RemoveFunction hook-cmpt key))
   nil))

(defn clear-hooks
  "Removes all callbacks on the Unity event corresponding to
  `event-kw`, regardless of their keys."
  [obj event-kw]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
    (.RemoveAllFunctions hook-cmpt))
  nil)

(defn hook
  "Retrieves a callback from a `GameObject` `obj`. `event-kw` is a
  keyword specifying the Unity event of the callback, and `key` is
  the key of the callback.

  In other words, retrieves any callback function attached via

```clj
(hook+ obj event-kw key callback)
```

  or the equivalent.

  If `key` is not supplied, `hook` uses `:default` as key. This is the same as

(hook obj event-kw :default)"
  [obj event-kw key]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
    (.CallbackForKey hook-cmpt key)))

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

(defn state
  "Returns the state of object `go` at key `k`."
  ([gobj]
   (when-let [^ArcadiaState s (cmpt gobj ArcadiaState)]
     (let [m (persistent!
               (reduce (fn [m, ^Arcadia.JumpMap+KeyVal kv]
                         (assoc! m (.key kv) (.val kv)))
                 (transient {})
                 (.. s state KeyVals)))]
       (when-not (zero? (count m))
         m))))
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
  "Removes all state from the `GameObject` `go`."
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
  :args (s/cat :obj any? ;; for now #(satisfies? ISceneGraph %)
               :k any?
               :spec ::role)
  :ret any?
  :fn (fn [{:keys [obj]} ret]
        (= obj ret)))

(defn role+ [obj k spec]
  ;; unfortunately we need to blow away previous hooks
  ;; in addition to overriding existing things
  (role- obj k)
  (reduce-kv
    (fn [_ k2 v]
      (cond
        (hook-types k2)
        (hook+ obj k2 k v)

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
  :args (s/cat :obj any? ;; for now ;; #(satisfies? ISceneGraph %)
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
  (assoc bldg hook-type-key (.fn inf)))

(defn role
  "Returns a hashmap of the state and hooks associates with role `k` on
  `GameObject` `obj`."
  [obj k]
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
    (let [m (reduce step init (cmpts obj ArcadiaBehaviour))]
      (if (zero? (count m))
        nil
        m))))

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
(defn roles
  "Returns a map of roke keys to role specification maps that include state and
  hook keys."
  [obj]
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
  (-> events/all-events
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
(defmacro ^:doc/no-syntax
  defrole
  "`(defrole name entry*)`

Macro for defining roles quickly.
Each entry can be either a key-value pair with a keyword key, such as would normally occur
  in a map intended as an Arcadia role, or an inlined function definition.

Normal key-value pairs get inserted into the generated map. For example,

```clj
(defrole movement
  :state {:speed 3}
  :update #'movement-update)
```

will expand into

```clj
(def movement
  {:state {:speed 3}
   :update #'movement-update})
```

Inlined function definitions have the following syntax:

`(name [args*] body)`

name must be the symbol form of an Arcadia hook keyword. A function
  intended for the `:update` hook, for example, should have the name
  `update`:

```clj
(defrole movement
  :state {:speed 3}
  (update [obj k] ...))
```

Each inlined function definition will *generate a var*, with a name
  constructed as follows: `<name of role>-<name of hook>`

For example, the `movement` role above will generate a var named
  `movement-update` bound to a function with the provided arguments
  and body, and include that var in the generated role map, expanding
  into something like:

```clj
(do
  (defn movement-update [obj k] ...)
  (def movement
    {:state {:speed 3}
     :update #'movement-update}))
```

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

(s/def ::element-process
  (s/cat
    :args (s/and vector? #(= 3 (count %)))
    :body (s/* any?)))

(s/def ::snapshot-elements
  (s/map-of keyword? ::element-process))

(s/def ::snapshot ::element-process)

(s/def ::mutable-elements
  (s/map-of keyword? ::element-process))

(s/def ::mutable ::element-process)

(s/def ::defmutable-args
  (s/cat
    :name symbol?
    :fields (s/coll-of symbol?, :kind vector?)
    :protocol-impls (s/* ::protocol-impl)
    :more-opts (s/keys*
                 :opt-un [::snapshot
                          ::snapshot-elements
                          ::mutable-elements
                          ::mutable])))

;; the symbol
(defn mutable-dispatch [{t :arcadia.data/type}]
  t)

;; constructor (no instance), so this has to be a multimethod
;; if we're sticking to clojure stuff
(defmulti mutable
  "Given a persistent representation of a mutable datatype defined via
  `defmutable`, constructs and returns a matching instance of that
  datatype.

Roundtrips with `snapshot`; that is, for any instance `x` of a type defined via `defmutable`,

```clj
(= (snapshot x) (snapshot (mutable (snapshot x))))
```"
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


;; consider expanding this to more variadic protocol
(defprotocol IDeleteableElements
  (delete! [this key]))

(defn- expand-type-sym [type-sym]
  (symbol
    (str
      (-> (name (ns-name *ns*))
          (clojure.string/replace "-" "_"))
      "."
      (name type-sym))))

(defn- apply-user-form [user-form tkv]
  (let [{:keys [args body]} user-form]
    `(let ~(vec (interleave args tkv))
       ~@body)))

(defn- snapshot-dictionary-form [{:keys [this-sym fields field-kws type-name],
                                  {:keys [snapshot-elements]
                                   default-element-snapshots :snapshot} :more-opts}]
  (let [k-sym (gensym "k_")
        v-sym (gensym "v_")
        tkv [this-sym k-sym v-sym]
        els (->> (interleave field-kws fields)
                 (partition 2)
                 (reduce (fn [acc [k v]]
                           (assoc acc k
                             (if default-element-snapshots
                               {:k k,
                                :field v,
                                :body default-element-snapshots}
                               {:k k :field v})))
                   {}))
        els (reduce-kv (fn [acc k body]
                         (update acc k (fnil assoc {:k k}) :body body))
              els
              snapshot-elements)
        els (mu/map-vals els
              (fn [rec]
                (if (and (:field rec) (:body rec))
                  (assoc rec :sym (gensym (str (name (:k rec)) "_")))
                  rec)))
        has-dynamic-element-process (boolean
                                      (or default-element-snapshots
                                          (some #(and (:body %) (not (:field %))) (vals els))))
        processed-field-bindings (apply concat
                                   (for [{:keys [body field sym k]} (vals els)
                                         :when sym]
                                     [sym (apply-user-form body [this-sym k field])]))
        dynamic-element-process-sym (gensym "dynamic-element-process_")
        dynamic-element-process (when has-dynamic-element-process
                                  (let [element-cases (apply concat
                                                        (for [{:keys [k body field]} (vals els)
                                                              :when (and body (not field))]
                                                          [k (apply-user-form body tkv)]))]
                                    `(fn ~dynamic-element-process-sym ~tkv
                                       (case ~k-sym ~@element-cases
                                         ~(if default-element-snapshots
                                            (apply-user-form default-element-snapshots tkv)
                                            v-sym)))))
        maybe-processed-dict-map (if has-dynamic-element-process
                                   `(.ToPersistentMap ~'defmutable-internal-dictionary ~this-sym ~dynamic-element-process)
                                   `(.ToPersistentMap ~'defmutable-internal-dictionary))
        maybe-processed-field-kvs (-> (select-keys els field-kws)
                                      (mu/filter-vals :field)
                                      (mu/map-vals #(or (:sym %) (:field %))))]
    `(let [~@processed-field-bindings]
       (if (zero? (.Count ~'defmutable-internal-dictionary))
         ~(-> maybe-processed-field-kvs (assoc :arcadia.data/type `(quote ~type-name)))
         (-> ~maybe-processed-dict-map
             (mu/massoc
               ~@(apply concat maybe-processed-field-kvs)
               :arcadia.data/type (quote ~type-name)))))))

(defn- mutable-impl-form [{:keys [fields field-kws type-name data-param]
                           {:keys [mutable-elements]
                            default-element-to-mutable :mutable} :more-opts}]
  (let [k-sym (gensym "k_")
        v-sym (gensym "v_")
        els (reduce (fn [acc k]
                      (assoc acc k
                        (if default-element-to-mutable
                          {:k k, :is-field true, :body default-element-to-mutable}
                          {:k k, :is-field true})))
              {}
              field-kws)
        els (reduce-kv (fn [acc k body]
                         (update acc k (fnil assoc {:k k}) :body body))
              els
              mutable-elements)
        has-dynamic-element-process (boolean
                                      (or default-element-to-mutable
                                          (some #(and (:body %) (not (:is-field %))) (vals els))))
        processed-field-vals (->> (map els field-kws)
                                  (map (fn [{:keys [k body]}]
                                         (if body
                                           (apply-user-form body [data-param k `(get ~data-param ~k)])
                                           `(get ~data-param ~k)))))
        dict-form (if has-dynamic-element-process
                    (let [element-cases (apply concat
                                          (for [{:keys [k is-field body]} els
                                                :when (and body (not is-field))]
                                            [k (apply-user-form body [data-param k-sym v-sym])]))]
                      `(new Arcadia.DefmutableDictionary ;; can make a constructor that takes arrays instead for more speed
                         (persistent!
                           (reduce-kv
                             (fn ~(gensym "dynamic-element-process_") [acc# ~k-sym ~v-sym]
                               (if (~(conj (set field-kws) :arcadia.data/type) ~k-sym)
                                 acc#
                                 (assoc! acc# ~k-sym
                                   (case ~k-sym
                                     ~@element-cases
                                     ~(if default-element-to-mutable
                                        (apply-user-form default-element-to-mutable [data-param k-sym v-sym])
                                        v-sym)))))
                             (transient {})
                             ~data-param))))
                    `(new Arcadia.DefmutableDictionary))]
    `(new ~type-name ~@processed-field-vals ~dict-form)))


(defmacro ^:doc/no-syntax
  defmutable
  "`(defmutable [name [fields*] other*])`

  Defines a new serializable, type-hinted, mutable datatype, intended
  for particularly performance or allocation sensitive operations on a
  single thread (such as Unity's main game thread). These datatypes
  support snapshotting to persistent data via `snapshot`, and
  reconstruction from snapshots via `mutable`; snapshotting and
  reconstructing are also integrated into `role+`, `state+`,
  `role`, and `roles`.

  Instances of these types may be converted into persistent
  representations and back via `snapshot` and `mutable`. This
  roundtrips, so if `x` is such an instance:

```clj
(= (snapshot x) (snapshot (mutable (snapshot x))))
```

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
      (let [{:keys [name fields protocol-impls]} parse
            type-name (expand-type-sym name)
            param-sym (-> (gensym (str name "_"))
                          (with-meta {:tag type-name}))
            field-kws (map clojurized-keyword fields)
            datavec [type-name (zipmap field-kws fields)]
            dict-param (gensym "dict_")
            data-param (gensym "data_")            
            mut-cases-form-fn (fn [this-sym [k v]]
                                `(case ~k
                                   ~@(mapcat
                                       (fn [field-kw, field]

                                         `[~field-kw (set! (. ~this-sym ~field) ~v)])
                                       field-kws
                                       fields)
                                   (.Add ~'defmutable-internal-dictionary ~k ~v)))
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
        `(do (deftype ~name ~(-> (->> fields (mapv #(vary-meta % assoc :unsynchronized-mutable true)))
                                 (conj
                                   (with-meta
                                     'defmutable-internal-dictionary
                                     {:unsynchronized-mutable true,
                                      :tag 'Arcadia.DefmutableDictionary})))

               ;; ------------------------------------------------------------
               clojure.lang.ILookup
               
               (valAt [this#, key#]
                 (case key#
                   ~@lookup-cases
                   (.GetValue ~'defmutable-internal-dictionary key#)))

               (valAt [this#, key#, not-found#]
                 (case key#
                   ~@lookup-cases
                   (if (.ContainsKey ~'defmutable-internal-dictionary)
                     (.GetValue ~'defmutable-internal-dictionary key#)
                     not-found#)))

               ;; ------------------------------------------------------------
               ISnapshotable

               (snapshot [~this-sym]
                 ~(snapshot-dictionary-form (mu/lit-assoc parse field-kws type-name this-sym)))

               ;; ------------------------------------------------------------
               IMutable
               
               ~@mut-impl

               IDeleteableElements
               ~(let [key-sym (gensym "key_")
                      key-cases (apply concat
                                  (for [k field-kws]
                                    [k `(throw
                                          (System.NotSupportedException.
                                            (str "Attempting to delete field key " ~k ". Deleting fields on types defined via `arcadia.core/defmutable` is currently not supported.")))]))]
                  `(delete! [this# ~key-sym]
                     (case ~key-sym
                       ~@key-cases
                       (.Remove ~'defmutable-internal-dictionary ~key-sym))))

               ;; ------------------------------------------------------------
               System.Collections.IDictionary
               
               (get_IsFixedSize [this#]
                 false)

               (get_IsReadOnly [this#]
                 false)

               (get_Item [this# k#]
                 (get this# k#))

               (set_Item [this# k# v#]
                 (mut! this# k# v#))

               (get_Keys [this#]
                 (into [~@field-kws] (.Keys ~'defmutable-internal-dictionary)))

               (get_Values [this#]
                 (into [~@fields] (.Values ~'defmutable-internal-dictionary)))

               (Add [this# k# v#]
                 (mut! this# k# v#))

               (System.Collections.IDictionary.Clear [this#]
                 (throw
                   (System.NotSupportedException.
                     "`Clear` is not currently supported for types defined via `arcadia.core/defmutable`")))

               (Contains [this# k#]
                 (or (#{~@field-kws} k#) (.Contains ~'defmutable-internal-dictionary k#)))

               (System.Collections.IDictionary.GetEnumerator [this#]
                 (throw
                   (System.NotSupportedException.
                     "`System.Collections.IDictionary.GetEnumerator` is not currently supported for types defined via `arcadia.core/defmutable`")))

               (Remove [this# k#]
                 (if (#{~@field-kws} k#)
                   (throw
                     (System.NotSupportedException.
                       "`Remove` on fields is not currently supported for types defined via `arcadia.core/defmutable`"))
                   (.Remove ~'defmutable-internal-dictionary k#)))

               ;; ------------------------------------------------------------
               ;; User-provided interface and protocol implementations
               
               ~@protocol-impl-forms)
             
             ;; ------------------------------------------------------------
             ;; Multimethod extensions and generated vars
             
             ;; Overwrite the generated constructor
             ~(let [naked-fields (map #(vary-meta % dissoc :tag) fields)
                    docs (str "Positional factory function for class " type-name)]
                `(defn ~ctr ~docs
                   ([~@naked-fields]
                    (new ~type-name ~@naked-fields (new Arcadia.DefmutableDictionary)))))
             
             ;; like defrecord:
             ~(let [map-sym (gensym "data-map_")
                    field-vals (for [kw field-kws] `(get ~map-sym ~kw))]
                `(defn ~(symbol (str "map->" name)) [~map-sym]
                   (new ~type-name ~@field-vals
                     (new Arcadia.DefmutableDictionary
                       (mu/mdissoc ~map-sym :arcadia.data/type ~@field-kws)))))
             
             ;; convert from generic persistent map to mutable type instance
             (defmethod mutable (quote ~type-name) [~data-param]
               ~(mutable-impl-form (mu/lit-assoc parse field-kws type-name data-param)))

             ;; register with our general deserialization
             (defmethod arcadia.data/parse-user-type (quote ~type-name) [~dict-param]
               (mutable ~dict-param))

             ;; serialize via print-method
             (defmethod print-method ~type-name [~this-sym ^System.IO.TextWriter stream#]
               (.Write stream#
                 (str "#arcadia.data/data " (pr-str (snapshot ~this-sym)))))
             
             ~type-name)))))

(defmacro defmutable-once
  "Like `defmutable`, but will only evaluate if no type with the same name has been defined."
  [& [name :as args]]
  (when-not (resolve name)
    `(defmutable ~@args)))
