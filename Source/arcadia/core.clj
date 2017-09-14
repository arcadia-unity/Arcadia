(ns arcadia.core
  (:require [clojure.string :as string]
            [clojure.spec :as s]
            [arcadia.internal.messages :refer [messages interface-messages]]
            [arcadia.internal.name-utils :refer [camels-to-hyphens]]
            [arcadia.internal.state-help :as sh]
            arcadia.literals)
  (:import ArcadiaBehaviour
           ArcadiaBehaviour+IFnInfo
           ArcadiaState
           [Arcadia UnityStatusHelper
            HookStateSystem JumpMap
            JumpMap+KeyVal JumpMap+PartialArrayMapView]
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
  "Attach hook a Clojure function to a Unity message on `obj`. The funciton `f`
  will be invoked every time the message identified by `hook` is sent by Unity. `f`
  must have the same arity as the expected Unity message. When called with a key `k`
  this key can be passed to `hook-` to remove the function."
  ([obj hook f] (hook+ obj hook hook f))
  ([obj hook k f]
   (hook+ obj hook k f nil))
  ([obj hook k f {:keys [fast-keys]}]
   (let [fast-keys (or fast-keys
                       (when (instance? clojure.lang.IMeta f)
                         (::keys (meta f))))]
     ;; need to actually do something here
     (when-not (empty? fast-keys) (println "fast-keys: " fast-keys))
     (let [hook-type (ensure-hook-type hook)
           ^ArcadiaBehaviour hook-cmpt (ensure-cmpt obj hook-type)]
       (.AddFunction hook-cmpt f k (into-array System.Object fast-keys))
       obj))))

(defn hook-var [obj hook var]
  (if (var? var)
    (hook+ obj hook var var)
    (throw
      (clojure.lang.ExceptionInfo.
        (str "Expects var, instead got: " (class var))
        {:obj obj
         :hook hook
         :var var}))))

(defn hook-
  "Remove all `hook` components attached to `obj`"
  ([obj hook]
   (hook- obj hook hook))
  ([obj hook k]
   (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type hook))]
     (.RemoveFunction hook-cmpt k))
   nil))

(defn hook-clear
  "Remove all functions hooked to `hook` on `obj`"
  [obj hook]
  (when-let [^ArcadiaBehaviour hook-cmpt (cmpt obj (ensure-hook-type hook))]
    (.RemoveAllFunctions hook-cmpt))
  nil)

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
   (with-cmpt gobj [s ArcadiaState]
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

(defn set-state!
  "Sets the state of object `go` to value `v` at key `k`."
  ([go k v]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs k (maybe-mutable v))
     v)))

(defn remove-state!
  "Removes the state object `go` at key `k`. Returns `nil`."
  ([go k]
   (with-cmpt go [arcs ArcadiaState]
     (.Remove arcs k))))

(defn update-state!
  "Updates the state of object `go` with function `f` and additional
  arguments `args` at key `k`. Args are applied in the same order as
  `clojure.core/update`."
  ([go k f x]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs (f (.ValueAtKey arcs k) x))))
  ([go k f x y]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs (f (.ValueAtKey arcs k) x y))))
  ([go k f x y z]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs (f (.ValueAtKey arcs k) x y z))))
  ([go k f x y z & args]
   (with-cmpt go [arcs ArcadiaState]
     (.Add arcs (apply f (.ValueAtKey arcs k) x y z args)))))

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
  (remove-state! obj k)
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
          (get spec (get hook-ks k2)))
        
        (= :state k2)
        (set-state! obj k (maybe-mutable v))))
    nil
    spec)
  obj)

;; (defn role [obj k]
;;   (into (if-let [s (state obj k)] {:state s} {})
;;     cat
;;     (for [^ArcadiaBehaviour ab (cmpts obj ArcadiaBehaviour)
;;           ^ArcadiaBehaviour+IFnInfo inf (.ifnInfos ab)
;;           :when (= (.key inf) k)
;;           :let [htk (hook->hook-type-key ab)
;;                 kv1 [htk (.fn inf)]
;;                 fks (.fastKeys inf)]]
;;       (if-not (empty? fks)
;;         [kv1 [(hook-fastkeys-key hook-type-key) (vec kfs)]]
;;         [kv1]))))

;; (s/def ::role
;;   (s/map-of ))


;; (s/fdef role
;;   :args (s/cat :obj #(satisfies? ISceneGraph %)
;;                :k any?)
;;   :ret ::role)

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

(defn role [obj k]
  (reduce
    (fn [bldg ^ArcadiaBehaviour ab]
      (reduce
        (fn [bldg ^ArcadiaBehaviour+IFnInfo inf]
          (if (= (.key inf) k)
            (let [hook-type-key (hook->hook-type-key ab)]
              (reduced
                (as-> bldg bldg
                      (assoc bldg hook-type-key (.fn inf))
                      (if-not (empty? (.fastKeys inf))
                        (assoc bldg (hook-type-key->fastkeys-key hook-type-key)
                          (vec (.fastKeys inf)))
                        bldg))))
            bldg))
        bldg
        (.ifnInfos ab)))
    (if-let [s (state obj k)]
      {:state (maybe-snapshot s)}
      {})
    (cmpts obj ArcadiaBehaviour)))

(defn- roles-step [bldg ^ArcadiaBehaviour hook]
  (let [hook-type-key (hook->hook-type-key hook)]
    (reduce
      (fn [bldg ^ArcadiaBehaviour+IFnInfo info]
        (update bldg (.key info)
          (fn [m]
            (as-> m m
                  (assoc m hook-type-key (.fn info))
                  (if-not (empty? (.fastKeys info))
                    (assoc m (hook-type-key->fastkeys-key hook-type-key)
                      (vec (.fastKeys info)))
                    m)))))
      bldg
      (.ifnInfos hook))))

;; map from hook, state keys to role specs
(defn roles [obj]
  (reduce
    roles-step
    (reduce-kv
      (fn [bldg k v]
        (assoc-in bldg [k :state] (maybe-snapshot v)))
      {}
      (or (state obj) {}))
    (cmpts obj ArcadiaBehaviour)))

;; so, (reduce-kv give-role obj2 (roles obj)) gives obj2 same Arcadia
;; state and behavior as obj


;; ============================================================
;; defmutable

;; defn using Activator/CreateInstance sort of screws up
;; (defn parse-user-type [[type-sym & args :as input]]
;;   (reset! put-log input)
;;   (Activator/CreateInstance (resolve type-sym) (into-array Object args)))

(defn- parse-user-type-dispatch [[t]]
  (resolve t))

(defmulti parse-user-type
  "This multimethod should be considered an internal, unstable
  implementation detail for now. Please refrain from extending it."
  parse-user-type-dispatch)

;; (defn parse-user-type [[type-sym & args :as input]]
;;   (Activator/CreateInstance (resolve type-sym) (into-array Object args)))

(alter-var-root #'*data-readers* assoc 'arcadia.core/mutable #'parse-user-type)

;; and we also have to do this, for the repl:
(when (.getThreadBinding ^clojure.lang.Var #'*data-readers*)
  (set! *data-readers*
    (assoc *data-readers* 'arcadia.core/mutable #'parse-user-type)))

(s/def ::defmutable-args
  (s/cat
    :name symbol?
    :fields (s/coll-of symbol?, :kind vector?)))


(defn mutable-dispatch [{t ::mutable-type}]
  (cond (instance? System.Type t) t
        (symbol? t) (resolve t)
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

(defn- expand-type-sym [type-sym]
  (symbol
    (str
      (-> (name (ns-name *ns*))
          (clojure.string/replace "-" "_"))
      "."
      (name type-sym))))

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
  reconstructing are also integrated into `role+`, `set-state!`,
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
                    (with-out-str (clojure.spec/explain ::defmutable-args args)))))
      (let [{:keys [name fields]} parse
            type-name (expand-type-sym name)
            param-sym (-> (gensym (str name "_"))
                          (with-meta {:tag type-name}))
            datavec (->> fields
                         (map (fn [arg] `(. ~param-sym ~arg)))
                         (cons type-name)
                         vec)]
        ;; check the bytecode
        `(let [t# (deftype ~name ~(->> fields (mapv #(vary-meta % assoc :unsynchronized-mutable true)))
                    System.ICloneable
                    (Clone [_#]
                      (new ~name ~@fields))
                    ;; TODO: add clojure.lang.ILookup implementation backed by mutable dictionary
                    ;; provide some interface for setting values on this -- mut! or something
                    ISnapshotable
                    (snapshot [_#]
                      ;; if we try dropping the type itself in we get the stubclass instead
                      ;; and this is a bit more robust anyway (redefs)
                      ~(into {::mutable-type `(quote ~type-name)}
                         (for [field fields] [(keyword (str field)) field]))))]
           (defmethod mutable ~type-name [{:keys [~@fields]}]
             ~(list* (symbol (str type-name ".")) fields))
           (defmethod parse-user-type ~type-name [[_# ~@fields]]
             ~(list* (symbol (str type-name ".")) fields))
           (defmethod print-method ~type-name [~param-sym ^System.IO.TextWriter stream#]
             (.Write stream#
               (str "#arcadia.core/mutable " (pr-str ~datavec))))
           t#)))))

(comment
  (defmutable Mut [^float x, ^float y])

  (def cube (create-primitive :cube))

  (defn some-update [obj k]
    (let [^Mut s (state obj k)]
      (set! (.x s) (inc (.x s)))))

  (role+ cube :some-role {:state (Mut. 1 2),
                          :update #'some-update})

  ;; then:

  (role cube :some-role)
  ;; => 
  {:state {::mutable-type Mut, :x 1.0, :y 2.0}}

)

(defmacro defmutable-once
  "Like `defmutable`, but will only evaluate if no type with the same name has been defined."
  [& [name :as args]]
  (when-not (resolve name)
    `(defmutable ~@args)))
