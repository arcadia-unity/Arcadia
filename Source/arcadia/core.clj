(ns arcadia.core
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            arcadia.internal.protocols
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
           System.Text.RegularExpressions.Regex
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

(defn
  ^{:doc/see-also {"Unity Console" "https://docs.unity3d.com/Manual/Console.html"}}
  log
  "Log message to the Unity console. Arguments are combined into a string."
  [& args]
  (Debug/Log (clojure.string/join " " args)))

;; ============================================================
;; null obj stuff

(defn null->nil
  "Same as `identity`, except if `x` is a null `UnityEngine.Object`,
  will return `nil`.

  More details and rationale are available in [the wiki](https://github.com/arcadia-unity/Arcadia/wiki/Null,-Nil,-and-UnityEngine.Object)."
  [x]
  (Util/TrueNil x))

(defn null?
  "Should `x` be considered `nil`? `(null? x)` will evalute to `true` if
  `x` is in fact `nil`, or if `x` is a `UnityEngine.Object` instance
  such that `(UnityEngine.Object/op_Equality x nil)` returns
  `true`. Otherwise will return `false`.

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
  ([^UnityEngine.Object original]
   (UnityEngine.Object/Instantiate original))
  ([^UnityEngine.Object original ^Vector3 position]
   (UnityEngine.Object/Instantiate original position Quaternion/identity))
  ([^UnityEngine.Object original ^Vector3 position ^Quaternion rotation]
   (UnityEngine.Object/Instantiate original position rotation)))

(defn create-primitive
  "Creates a game object with a primitive mesh renderer and appropriate
  collider. `prim` can be a `PrimitiveType` or one of `:sphere`
  `:capsule` `:cylinder` `:cube` `:plane` `:quad`. If supplied, the
  third argument should be a string, and will be set as the name of
  the newly created GameObject. Wraps `GameObject/CreatePrimitive`."
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

(defn object-typed
  "Returns one live instance of UnityEngine.Object subclass type `t`
  from the scene graph, or `nil` if no such object can be found. Wraps
  `Object/FindObjectOfType`."
  [^Type t]
  (null->nil (UnityEngine.Object/FindObjectOfType t)))

(defn objects-typed
  "Returns a sequence of all live instances of UnityEngine.Object subclass
  type `t` in the scene graph. Wraps `Object/FindObjectsOfType`."
  [^Type t]
  (remove null? (UnityEngine.Object/FindObjectsOfType t)))

(defn object-named
  "Returns one live GameObject from the scene graph, the name of which
  matches `name-or-regex`. `name-or-regex` may be a string or a
  regular expression object."
  ^GameObject [name-or-regex]
  (cond
    (string? name-or-regex)
    (UnityEngine.GameObject/Find ^String name-or-regex)

    (instance? Regex name-or-regex)
    (let [objs (UnityEngine.Object/FindObjectsOfType GameObject)]
      (loop [i (int 0)]
        (when (< i (count objs))
          (let [obj (aget objs i)]
            (if (and (not (null? obj))
                     (re-matches name-or-regex (.name obj)))
              obj
              (recur (inc i)))))))

    :else
    (throw
      (ArgumentException.
        (str "Expects string or Regex, instead got instance of "
             (class name-or-regex))
        "name-or-regex"))))

(defn objects-named
  "Returns a sequence of all live `GameObject`s in the scene graph, the
  name of which match `name-or-regex`. `name-or-regex` may be a string
  or a regular expression object."
  [name-or-regex]
  (cond
    (string? name-or-regex)
    (for [^GameObject obj (objects-typed GameObject)
          :when (= (.name obj) name-or-regex)]
      obj)

    (instance? Regex name-or-regex)
    (for [^GameObject obj (objects-typed GameObject)
          :when (re-matches name-or-regex (.name obj))]
      obj)

    :else
    (throw
      (ArgumentException.
        (str "Expects string or Regex, instead got instance of "
             (class name-or-regex))
        "name-or-regex"))))

(defn object-tagged
  "Returns one live `GameObject` tagged `t` from the scene graph,
  or `nil` if no such GameObjects exist.

  Wraps `GameObject/FindWithTag`."
  ^GameObject [^String t]
  (null->nil (UnityEngine.GameObject/FindWithTag t)))

(defn objects-tagged
  "Returns a sequence of live `GameObject`s tagged tag. Returns empty
  array if no `GameObject` was found.
  Wraps `GameObject/FindGameObjectsWithTag`."
  [^String t]
  (remove null? (UnityEngine.GameObject/FindGameObjectsWithTag t)))

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
  "Coerces `x`, expected to be a GameObject or Component, to a
  corresponding live (non-destroyed) GameObject instance or to `nil` by
  the following policy:

  - If `x` is a live GameObject, returns it.
  - If `x` is a destroyed GameObject, returns `nil`.
  - If `x` is a live Component, returns its containing GameObject.
  - If `x` is a destroyed Component, returns `nil`.
  - If `x` is `nil`, returns `nil`.
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
  "Makes GameObject `x` the new parent of GameObject `child`. Returns `child`.

  If `world-position-stays` is true, `child` retains its world position after
  being reparented."
  (^GameObject [x child]
   (child+ x child false))
  (^GameObject [x child world-position-stays]
   (let [x (Util/CastToGameObject x)
         child (Util/CastToGameObject child)]
     (.SetParent
       (.transform child)
       (.transform x)
       ^Boolean world-position-stays)
     child)))

(defn child-
  "Removes GameObject `x` as the parent of GameObject `child`, 
  moving `child` to the top of the scene graph hierarchy. Returns `nil`.

  If `world-position-stays` is `true`, `child` retains its world
  position after being reparented."
  ([x child]
   (child- x child false))
  ([x child world-position-stays]
   (let [x (Util/CastToGameObject x)
         child (Util/CastToGameObject child)]
     (when (= (.. child transform parent) (.transform x))
       (.SetParent (.transform child) nil ^Boolean world-position-stays)))))

(defn children
  "Gets the live children of GameObject `x` as a persistent vector of
  GameObjects."
  [x]
  (let [x (Util/CastToGameObject x)]
    (persistent!
      (reduce
        (fn [acc ^UnityEngine.Transform x]
          (if-let [g (null->nil (.gameObject x))]
            (conj! acc g)
            acc))
        (transient [])
        (.transform x)))))

(defn parent
  "Returns the live parent of GameObject `x` or `nil` if it has none.

  GameObjects at the top of the hierarchy do not have parents."
  [x]
  (when-let [^Transform parent (-> x
                                   Util/CastToGameObject
                                   (.. transform parent)
                                   null->nil)]
    (.gameObject parent)))

;; ------------------------------------------------------------
;; IEntityComponent

;; TODO: get rid of this forward declaration by promoting ISceneGraph functions
;; above this

(defn cmpt
  "Returns the first live Component of type `t` attached to GameObject
  `x`. Returns `nil` if no such Component is attached."
  ^UnityEngine.Component [^GameObject x ^Type t]
  (null->nil (.GetComponent (Util/CastToGameObject x) t)))

(defn cmpts
  "Returns all live Components of type `t` attached to GameObject `x`
  as a (possibly empty) array."
  ^|UnityEngine.Component[]| [x ^Type t]
  (Util/WithoutNullObjects (.GetComponents (Util/CastToGameObject x) t)))

(defn cmpt+
  "Adds a new Component of type `t` to GameObject `x`. Returns the new Component."
  ^UnityEngine.Component [x ^Type t]
  (.AddComponent (Util/CastToGameObject x) t))

;; returns nil because returning x would be inconsistent with cmpt+,
;; which must return the new component
(defn cmpt-
  "Removes *every* Component of type `t` from GameObject `x`. Returns `nil`."
  [x ^Type t]  
  (let [^|UnityEngine.Component[]| a (.GetComponents (Util/CastToGameObject x) t)]
    (loop [i (int 0)]
      (when (< i (count a))
        (retire (aget a i))
        (recur (inc i))))))

;; ------------------------------------------------------------
;; repercussions

(defn ensure-cmpt
  "If GameObject `x` has a component of type `t`, returns it. Otherwise, adds
  a component of type `t` and returns the new instance."
  ^UnityEngine.Component [x ^Type t]
  (let [x (Util/CastToGameObject x)]
    (or (cmpt x t) (cmpt+ x t))))

;; ------------------------------------------------------------
;; sugar macros

(defn- meta-tag [x t]
  (vary-meta x assoc :tag t))

(defn- gentagged
  ([t]
   (meta-tag (gensym) t))
  ([s t]
   (meta-tag (gensym s) t)))

(defmacro with-cmpt
  "`binding => name component-type`

  For each binding, binds `name` to an instance of class
  `component-type` attached to GameObject `gob`. If no such instance
  is currently attached to `x`, a new instance of `component-type`
  will be created, attached to `x`, and bound to `name`. `body` is
  then evaluated in the lexical context of all bindings."
  ([gob bindings & body]
   (assert (vector? bindings))
   (assert (even? (count bindings)))
   (let [gobsym (gentagged "gob__" 'UnityEngine.GameObject)
         dcls  (->> bindings
                 (partition 2)
                 (mapcat (fn [[n t]]
                           [(meta-tag n t) `(ensure-cmpt ~gobsym ~t)])))]
     `(let [~gobsym (Arcadia.Util/CastToGameObject ~gob)]
        (let [~@dcls]
          ~@body)))))

(defmacro if-cmpt
  "If a component of type `cmpt-type` is attached to GameObject `gob`,
  binds it to `cmpt-name`, then evaluates and returns `then` in the
  lexical scope of that binding. Otherwise evaluates and returns
  `else`, if provided, or returns `nil` if `else` is not provided."
  [gob [cmpt-name cmpt-type] then & else]
  (let [gobsym (gentagged "gob__" 'UnityEngine.GameObject)]
    `(let [~gobsym ~gob]
       (if-let [~cmpt-name (cmpt ~gobsym ~cmpt-type)]
         ~then
         ~@else))))

;; ============================================================
;; sugar for imperative programming

(defmacro sets!
  "Set multiple fields or properties on an object instance `o` simultaneously.

  assignment => field-name value 

  For each assignment, field-name is the name of a field or property
  of `o`, and `value` is the new value it will be set to.

  Returns the final set value.

  ```clj
(sets! (.transform some-game-object)
  position (arcadia.linear/v3 1 2 3)
  localScale (arcadia.linear/v3 1 2 3))
  ```"
  [o & assignments]
  (let [osym (gensym "obj__")
        asgns (->> (partition 2 assignments)
                   (map (fn [[lhs rhs]]
                          `(set! (. ~osym ~lhs) ~rhs))))]
    `(let [~osym ~o]
       ~@asgns)))

(defmacro set-with!
  "Access and set a field or property `prop` on object instance
  `obj`. The new value at `(. obj prop)` will be set to the value of
  `body`, evaluated in an implicit `do`, with `name` bound to the
  preexisting value of `(. obj prop)`.  This operation is not atomic,
  and should be used with caution in concurrent contexts.

  As an example,

  ```clj
(set-with! (.transform some-game-object) [pos position]
  (arcadia.linear/v3+ pos (arcadia.linear/v3 1 0 0)))
  ```

  is equivalent to

  ```
(let [tr (.transform some-game-object)
      pos (.position tr)]
  (set!
    (.position tr)
    (arcadia.linear/v3+ (.position tr) (arcadia.linear/v3 1 0 0))))
  ```

  Since the object is the first argument, multiple such assignments on
  an object may be chained using `doto`. Returns the new value of the
  field or property."
  [obj [name prop :as bindings] & body]
  (assert (vector? bindings))
  (assert (symbol? name))
  (assert (symbol? prop))
  `(let [obj# ~obj
         ~name (. obj# ~prop)]
     (set! (. obj# ~prop) (do ~@body))))

;; ============================================================
;; traversal

(defn descendents
  "Returns a sequence containing all descendents of GameObject `x` in
  depth-first order. The descendents of `x` are all GameObjects
  attached as children to `x` in the Unity hierarchy; all of those
  GameObject's children; and so on."
  [x]
  (tree-seq identity children x))

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
  "Returns a sorted seq of all permissible hook event keywords."
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

(defn
  ^{:doc/see-also {"Unity Event Functions" "https://docs.unity3d.com/Manual/EventFunctions.html"}}
  hook+
  "Attach a Clojure function, preferrably a Var instance, to GameObject
  `obj` on key `k`. The function `f` will be invoked every time the event
  identified by `event-kw` is triggered by Unity.

  `f` must be a function of 2 arguments, plus however many arguments
  the corresponding Unity event function takes. The first argument is
  the GameObject `obj` that `f` is attached to. The second argument is
  the key `k` it was attached with. The remaining arguments are the
  arguments normally passed to the corresponding Unity event function.

  Returns `f`."
  ([obj event-kw k f]
   (let [hook-type (ensure-hook-type event-kw)
         ^ArcadiaBehaviour hook-cmpt (ensure-cmpt obj hook-type)]
     (.AddFunction hook-cmpt k f)
     f)))

(defn hook-
  "Removes hook function from GameObject `obj` on the Unity event
  corresponding to `event-kw` at `key`, if it exists. Reverse of

```clj
  (hook+ obj event-kw key hook-function)
```

  Returns `nil`."
  ([obj event-kw key]
   (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
     (.RemoveFunction hook-cmpt key))
   nil))

(defn clear-hooks
  "Removes all hook functions on the Unity event corresponding to
  `event-kw`, regardless of their keys."
  [obj event-kw]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
    (.RemoveAllFunctions hook-cmpt))
  nil)

(defn hook
  "Retrieves an attached hook function from GameObject
  `obj`. `event-kw` is a keyword specifying the Unity event of the
  hook function, and `key` is the key of the hook function.

  In other words, retrieves any hook function attached via

```clj
  (hook+ obj event-kw key hook-function)
```

  or the equivalent."
  [obj event-kw key]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type event-kw))]
    (.CallbackForKey hook-cmpt key)))


;; ============================================================
;; defmutable support methods

(defn snapshot
  "Converts `defmutable` instance `x` to a persistent representation."
  [x]
  (arcadia.internal.protocols/snapshot x))

;; public for macros
(defn maybe-snapshot
  "Unstable implementation detail, please don't use."
  [x]
  (if (arcadia.internal.protocols/snapshotable? x)
    (snapshot x)
    x))

(defn mutable
  "Given a persistent representation of a mutable datatype defined via
  `defmutable`, constructs and returns a matching instance of that
  datatype.

  Roundtrips with `snapshot`; that is, for any instance `x` of a type defined via `defmutable`,

  ```clj
  (= (snapshot x) (snapshot (mutable (snapshot x))))
  ```"
  [x]
  (arcadia.internal.protocols/mutable x))

;; public for macros
(defn maybe-mutable
  "Unstable implementation detail, please don't use."
  [x]
  (if (and (map? x)
           (contains? x ::mutable-type))
    (mutable x)
    x))

;; ============================================================
;; state

(defn lookup
  "Returns the state of GameObject `go` at key `k`. Does not convert
  defmutable instances to persistent representations."
  [go k]
  (Arcadia.HookStateSystem/Lookup go k))

(defn state
  "With one argument, returns the state of GameObject `go` on all keys
  as a map. With two arguments, returns the state of GameObject `go`
  at key `k`. If this state is a `defmutable` instance, will return a
  persistent representation instead. To avoid this behavior use
  `lookup`."
  ([go]
   (when-let [^ArcadiaState s (cmpt go ArcadiaState)]
     (let [m (persistent!
               (reduce (fn [m, ^Arcadia.JumpMap+KeyVal kv]
                         (assoc! m (.key kv) (maybe-snapshot (.val kv))))
                 (transient {})
                 (.. s state KeyVals)))]
       (when-not (zero? (count m))
         m))))
  ([go k]
   (maybe-snapshot
     (Arcadia.HookStateSystem/Lookup go k))))

(defn state+
  "Sets the state of GameObject `go` to value `v` at key `k`. Returns
  `v`. If `v` is a persistent representation of a `defmutable`
  instance, will convert it to a mutable instance before inserting in
  the scene graph."
  ([go k v]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (maybe-mutable v))
     v)))

(defn state-
  "Removes the state of object `go` at key `k`."
  ([go k]
   (with-cmpt go [arcs ArcadiaState]
     (.Remove arcs k)
     nil)))

(defn clear-state
  "Removes all state from the GameObject `go`."
  [go]
  (with-cmpt go [arcs ArcadiaState]
    (.Clear arcs)
    nil))

(defmacro ^:private update-state-impl-form [go k f & args]
  `(with-cmpt ~go [arcs# ArcadiaState]
     (let [v# (~f (maybe-snapshot (.ValueAtKey arcs# ~k)) ~@args)]
       (.Add arcs# ~k (maybe-mutable v#))
       v#)))

(defn update-state
  "Updates the state of GameObject `go` at key `k` with function `f` and
  additional arguments `args`. Args are applied in the same order as
  `clojure.core/update`. Returns the new value of the state at `k`.

  In the special case that the value in state is a defmutable
  instance, `f` will be applied to the persistent representation of
  that value, which will then be converted to a mutable instance
  again, and inserted into state at `k`. The returned value will be
  `f` applied to the persistent representation."
  ([go k f]
   (update-state-impl-form go k f))
  ([go k f x]
   (update-state-impl-form go k f x))
  ([go k f x y]
   (update-state-impl-form go k f x y))
  ([go k f x y z]
   (update-state-impl-form go k f x y z))
  ([go k f x y z & args]
   (with-cmpt go [arcs ArcadiaState]
     (let [v (apply f (snapshot (.ValueAtKey arcs k)) x y z args)]
       (.Add arcs k (maybe-mutable v))
       v))))

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

(defn role-
  "Removes a role from GameObject `obj` on key `k`. Any hook or state
  attached to `obj` on key `k` will be removed. Returns `nil`."
  [obj k]
  (let [abs (cmpts obj ArcadiaBehaviour)]
    (loop [i (int 0)]
      (when (< i (count abs))
        (let [^ArcadiaBehaviour ab (aget abs i)]
          (.RemoveFunction ab k)
          (recur (inc i))))))
  (state- obj k)
  nil)

(s/fdef role+
  :args (s/cat :obj any? ;; for now #(satisfies? ISceneGraph %)
               :k any?
               :spec ::role)
  :ret any?
  :fn (fn [{:keys [obj]} ret]
        (= obj ret)))

(defn role+
  "Adds a role `r` to GameObject `obj` on key `k`, replacing any
  previous role on `k`. Keys in `r` corresponding to Unity event
  functions, such as `:update`, `:on-collision-enter`, etc, are
  expected to have values meeting the criteria for hook functions
  described in the docstring for `hook+`. For such a key `event-kw`,
  values will be attached to `obj` as though by `(hook+ obj event-kw
  k (get r event-kw))`.

  If present, the value of the key `:state` in `r` will be attached to
  `obj` as though by `(state+ obj k (get r :state))`.

  For example,

  ```clj
  (role+
  obj,
  :example-role,
  {:state 45, {:update #'on-update, :on-collision-enter #'on-collision-enter}})
  ```

  has the same effect as

  ```clj
  (role- obj :example-role)
  (state+ obj :example-role 45)
  (hook+ obj :update :example-role #'on-update)
  (hook+ obj :on-collision-enter :example-role #'on-collision-enter)
  ```

  As with `state+`, persistent reprsentations `defmutable` data as
  values in `:state` will be converted to mutable instances.

  Returns `r`."
  [obj k r]
  (role- obj k)
  (reduce-kv
    (fn [_ k2 v]
      (cond
        (hook-types k2)
        (hook+ obj k2 k v)

        (= :state k2)
        (state+ obj k (maybe-mutable v))))
    nil
    r)
  r)

(defn roles+
  "Takes a GameObject `obj` and map `rs` containing role keys and role
  maps as entries.  For each entry in `rs` with key `k` and value `r`,
  adds `r` to `obj` on key `k` as though calling
  
  ```clj
  (role+ obj k r)
  ```
  
  Returns `rs`."
  [obj rs]  
  (reduce-kv #(role+ obj %2 %3) nil rs)
  rs)

(defn roles-
  "Takes a GameObject `obj` and collection of keys `ks`. For each key
  `k` in `ks`, will remove `k` from `obj`, as if calling

  ```clj
  (role- obj k)
  ```

  Returns `nil`."
  [obj ks]
  (reduce role- obj ks)
  nil)

(s/fdef role
  :args (s/cat :obj any? ;; for now ;; #(satisfies? ISceneGraph %)
               :k any?)
  :ret ::role)

;; TODO: better name
;; also instance vs satisfies, here
;; maybe this should be a definterface or something
;; yeah definitely should be a definterface, this is just here for `defmutable`

(defn- inner-role-step [bldg, ^ArcadiaBehaviour+IFnInfo inf, hook-type-key]
  (assoc bldg hook-type-key (.fn inf)))

(defn role
  "Returns a map of all hooks and state attached to GameObject `obj` on
  key `k`. Within the returned map, keys will be either hook event
  keywords such as `:update`, `:on-collision-enter`, etc, or `:state`.

  ```clj
  (hook+ obj :update :test #'on-update)
  (state+ obj :test {:speed 3, :mass 4})

  (role obj :test)
  ;; returns:
  ;; {:state {:speed 3, :mass 4},
  ;;  :update #'on-update}
  ```"
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
  "Returns a map containing all the roles attached to GameObject
  `obj`. For each entry in this map, the key is the key of some hooks
  or state attached to `obj`, and the value is the map one would get
  by calling `(role obj k)` for that key `k`. For example:

  ```clj
  (hook+ obj :update :key-a #'on-update)
  (state+ obj :key-a {:speed 3, :mass 4})

  (hook+ obj :update :key-b #'other-on-update)
  (state+ obj :key-b {:name \"bob\", :health 5})

  (roles obj)
  ;; returns:
  ;; {:key-a {:state {:speed 3, :mass 4},
  ;;          :update #'on-update},
  ;;  :key-b {:state {:name \"bob\", :health 5},
  ;;          :update #'other-on-update}}
  ```
  Roundtrips with `roles+`."
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

(defn mut!
  "Dynamically sets field keyword `kw` of `defmutable` instance `x` to
  new value `v`. Returns `v`."
  [x kw v]
  (arcadia.internal.protocols/mut! x kw v))

(defn delete!
  "Removes dynamic entry `k` from `defmutable` instance `x`."
  [x k]
  (arcadia.internal.protocols/delete! x k))

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
         ~(-> maybe-processed-field-kvs (assoc ::mutable-type `(quote ~type-name)))
         (-> ~maybe-processed-dict-map
             (mu/massoc
               ~@(apply concat maybe-processed-field-kvs)
               ::mutable-type (quote ~type-name)))))))

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
                               (if (~(conj (set field-kws) ::mutable-type) ~k-sym)
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
  reconstructing are also integrated into `state`, `state+`,
  `update-state`, `role`, `role+`, and `roles`.

  `defmutable` instances may be mutated in two ways. Their fields may
  be mutated directly using `set!` and dot syntax. Fields may also be
  dynamically set using `(mut! obj k v)`. Here, `obj` is the
  `defmutable` instance, `k` is the keyword key for an entry, and `v`
  is the new value of that entry to set on the defmutable instance.

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

  `defmutable` supports four special options to help define custom
  `snapshot` and `mutable` implementations:

  - `:snapshot`
  - `:mutable`
  - `:snapshot-elements`
  - `:mutable-elements`

  `:snapshot` and `:mutable` expect their values to be in the
  following form:

  `([this-param key-param value-param] body*)`

  When calling `snapshot` or `mutable`, the function defined by
  `:snapshot` or `:mutable` will be called on each entry in the
  `defmutable` instance (in the case of `snapshot`) or the persistent
  map representation (in the case of `mutable`). When these functions
  run, `this-param` will be assigned to the original `defmutable`
  instance for `snapshot`, or to the original persistent map
  representation for `mutable`; `key-param` will be assigned to the
  keyword key of this entry; and `val-param` will be assigned to its
  incoming value. For `:snapshot`, the return will be the value of the
  corresponding entry in the persistent map representation. For
  `:mutable`, the return will be the value of the corresponding entry
  in the `defmutable` instance representation. `:snapshot` and
  `:mutable` should invert each other.

  `:snapshot-elements` and `:mutable-elements` support finer
  specialization of `snapshot` and `mutable` behavior. They expect
  their values to be maps from keyword names of possible entries, to
  the same sort of function specifications taken by `:snapshot` and
  `:mutable`.  Specifications made with `:snapshot-elements` or
  `:mutable-elements` take priority over those made with `:snapshot`
  or `:mutable`.

  See the online documentation for examples.

  `defmutable` will automatically generate a constructor function. As
  with `deftype`, the name of its var will be `->` followed by the
  name of the type, and its expected arguments will be the initial
  values of `fields`, in order.

  For example, given the following `defmutable` definition:

  ```clj
  (defmutable Sheep [wooliness bouyancy])
  ```

  an instance of `Sheep` could be constructed using

  ```clj
  (->Sheep 3 4)
  ```
  
  `defmutable` serialization, via either `snapshot` or Unity
  scene-graph serialization, does *not* currently preserve reference
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
                                 `(arcadia.internal.protocols/mut! ~args
                                    (throw (Exception. "requires odd number of arguments"))))
            mut-impl (mac/arities-forms
                       (fn [[this-sym & kvs]]
                         (let [val-sym (gensym "val_")
                               pairs (partition 2 kvs)]
                           `(arcadia.internal.protocols/mut! [~this-sym ~@kvs]
                              ~(when-let [lp (last pairs)]
                                 (mut-cases-form-fn this-sym (last pairs)))
                              (arcadia.internal.protocols/mut! ~this-sym ~@(apply concat (butlast pairs))))))
                       {::mac/arg-fn #(gensym (str "arg_" % "_"))
                        ::mac/min-args 1
                        ::mac/max-args 5
                        ::mac/cases (merge
                                      {1 (fn [& stuff]
                                           `(arcadia.internal.protocols/mut! [this#] this#))
                                       3 (fn [[_ [this-sym k v :as args]]]
                                           `(arcadia.internal.protocols/mut! ~args
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
                                        (list* name args body)))))
            stream-sym (with-meta (gensym "stream__") {:tag 'System.IO.TextWriter})]
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
               arcadia.internal.protocols/ISnapshotable

               (arcadia.internal.protocols/snapshot [~this-sym]
                 ~(snapshot-dictionary-form (mu/lit-assoc parse field-kws type-name this-sym)))
               
               (arcadia.internal.protocols/snapshotable? [~this-sym] true)

               ;; ------------------------------------------------------------
               arcadia.internal.protocols/IMutable
               
               ~@mut-impl

               ;; ------------------------------------------------------------
               arcadia.internal.protocols/IDeleteableElements
               
               ~(let [key-sym (gensym "key_")
                      key-cases (apply concat
                                  (for [k field-kws]
                                    [k `(throw
                                          (System.NotSupportedException.
                                            (str "Attempting to delete field key " ~k ". Deleting fields on types defined via `arcadia.core/defmutable` is currently not supported.")))]))]
                  `(arcadia.internal.protocols/delete! [this# ~key-sym]
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
                       (mu/mdissoc ~map-sym :arcadia.data/type ::mutable-type ~@field-kws)))))
             
             ;; convert from generic persistent map to mutable type instance
             (defmethod arcadia.internal.protocols/mutable (quote ~type-name) [~data-param]
               ~(mutable-impl-form (mu/lit-assoc parse field-kws type-name data-param)))

             ;; register with our general deserialization
             (defmethod arcadia.data/read-user-type (quote ~type-name) [~dict-param]
               ~(let [field-access (for [field-kw field-kws]
                                     `(get ~dict-param ~field-kw))]
                  `(let [inst# (~ctr ~@field-access)]
                     (reduce-kv
                       (fn [_# k# v#]
                         (when-not (#{~@field-kws :arcadia.data/type} k#)
                           (mut! inst# k# v#)))
                       nil
                       ~dict-param)
                     inst#)))

             ;; Serialize via print-method; arcadia.data/*serialize* should be `true` for this to
             ;; work recursively.
             (defmethod print-method ~type-name [~this-sym ~stream-sym]
               ~(let [dict-sym (gensym "dict__")
                      field-writes (interpose
                                     `(.Write ~stream-sym ", ")
                                     (for [[field field-kw] (map list fields field-kws)]
                                       `(do (print-method ~field-kw ~stream-sym)
                                            (.Write ~stream-sym " ")
                                            (print-method (. ~this-sym ~field) ~stream-sym))))
                      dynamic-writes `(.PrintEntries ~dict-sym print-method ~stream-sym)]
                  `(let [~dict-sym (. ~this-sym ~'defmutable-internal-dictionary)]
                     (.Write ~stream-sym "#arcadia.data/data{:arcadia.data/type ")
                     (.Write ~stream-sym ~(str type-name))
                     ~(when (seq fields)
                        `(.Write ~stream-sym ", "))
                     ~@field-writes
                     (when (< 0 (.Count ~dict-sym))
                       (.Write ~stream-sym ", "))
                     ~dynamic-writes
                     (.Write ~stream-sym "}"))))
             
             ~type-name)))))

(defmacro defmutable-once
  "Like `defmutable`, but will only evaluate if no type with the same
  name has been defined."
  [& [name :as args]]
  (when-not (resolve name)
    `(defmutable ~@args)))
