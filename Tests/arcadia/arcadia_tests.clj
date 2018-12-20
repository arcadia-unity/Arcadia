(ns arcadia.arcadia-tests
  (:require [arcadia.core :as ac]
            [arcadia.linear :as al]
            [arcadia.internal.test :as at]
            [arcadia.internal.editor-callbacks :as ec]
            [arcadia.internal.player-callbacks :as pc]
            [clojure.spec.alpha :as s]
            [arcadia.debug :refer [break]])
  (:import [UnityEngine
            GameObject Component Vector2 Vector3 Transform]
           [Arcadia UnityStatusHelper]))

;; ============================================================
;; utils

(defonce ^:private retirement-key-counter (atom 0))

(defn schedule-retirement [timing-type timeout & xs]
  (let [k (keyword (str  (ns-name *ns*)) (str "test-" (swap! retirement-key-counter inc)))
        f (fn retirement-callback []
            (doseq [x xs]
              (try
                (ac/retire x)
                (catch Exception e
                  (ac/log "Exception encountered in retirement phase of schedule-retirement:")
                  (UnityEngine.Debug/Log e)))))]
    (case timing-type
      :ms
      (ec/add-timeout k f timeout)

      :frames
      (pc/add-update-frame-timeout k f timeout)
      
      :eframes
      (ec/add-editor-frame-timeout k f timeout))))

(s/def ::eframes any?)

(s/def ::frames any?)

(s/def ::ms any?)

(s/def ::lit (s/every symbol? :kind vector))

(s/def ::with-temp-objects-args
  (s/cat
    :opts (s/?
            (s/keys* :opt-un [::eframes ::ms ::lit ::frames]))
    :bindings (s/?
                (s/and
                  vector?
                  #(even? (count %))))
    :body (s/* any?)))

(defmacro ^{:arglists ([kv-opts* bindings? body*])} with-temp-objects 
  "Sets up temporary objects and schedules their retirement. Intended for testing.

  Syntax: (with-temp-objects kv-opts* bindings? body*)

  `kv-opts*` are optional key-value pairs. They can be the following:

  :ms      - evaluated value is milliseconds before objects are retired
  :frames  - evaluated value is player frames before objects are retired
  :eframes - evaluated value is editor frames before objects are retired
  :lit     - value should be a vector of symbols which will be bound to
             new GameObjects (see below)

  `bindings` is an optional vector binding form, and has the same
  syntax as `clojure.core/let`.  Every value bound to a symbol in it
  should be a GameObject instance, since `arcadia.core/retire` will
  get called on all of them.

  If the :lit key is provided, the symbols in its vector value will be
  bound to new GameObject instances named after their corresponding symbol.

  When GameObjects bound in `bindings` or through the `:lit` vector
  get retired depends on the supplied options `:ms`, `:frames`, or `:eframes`. If
  both are provided an error is thrown. If neither is provided the
  GameObjects are retired as soon as `body` is evaluated."
  [& args]  
  (let [parse (s/conform ::with-temp-objects-args args)]
    (when (= ::s/invalid parse)
      (throw (Exception. (str "invalid arguments. spec explanation:\n"
                              (with-out-str (s/explain ::with-temp-objects-args args))))))
    (let [{:keys [opts bindings body]} parse
          [timing-type timeout] (let [e (select-keys opts [:eframes :frames :ms])]
                                  (when (< 1 (count e))
                                    (throw (Exception. "Please supply at most one of :eframes, :frames, or :ms")))
                                  (or (first e) [:now]))
          lit-bindings (->> (get opts :lit)
                            (mapcat (fn [sym]
                                      [sym `(UnityEngine.GameObject. ~(str sym))])))
          bindings (vec (concat lit-bindings bindings))]
      (if (= timing-type :now)
        (let [teardown (for [x (take-nth 2 bindings)]
                         `(ac/retire ~x))]
          `(let ~bindings
             (try
               ~@body
               (finally
                 ~@teardown))))
        `(let ~bindings
           (schedule-retirement ~timing-type ~timeout ~@(take-nth 2 bindings))
           ~@body)))))

;; --------------------------------------------------
;; async testing thing
;; consider moving the following to arcadia.internal.tests



;; ============================================================
;;

(def unique-key-counter (atom 0))

(defn unique-key [] (swap! unique-key-counter inc))


;; move to internal test framework

;; as-sub. bit aggressive but here we are. Usually I wouldn't
;; go for this kind of trivial sugar but it's important that
;; tests are very easy to write.
(defmacro as-sub [t-or-t-and-label & body]
  (cond
    (vector? t-or-t-and-label)
    (do (assert (= 2 (count t-or-t-and-label)))
        (let [[t label] t-or-t-and-label]
          (assert (symbol? t))
          (assert (string? label))
          `(let [~t (at/sub ~t ~label)]
             ~@body)))

    (symbol? t-or-t-and-label)
    (let [t t-or-t-and-label]
      `(let [~t (at/sub ~t)]
         ~@body))

    :else (throw
            (InvalidOperationException.
              (str "`t-or-t-and-label` must be either symbol or vector, instead got "
                   (class t-or-t-and-label))))))

(defmacro as-sub-closing [t-or-t-and-label & body]
  (let [t (if (vector? t-or-t-and-label)
            (first t-or-t-and-label)
            t-or-t-and-label)]
    `(as-sub ~t-or-t-and-label
       ~@body
       (~t :close))))

(defn close-after-frames [t frames message]
  (assert (at/tester? t))
  (assert (number? frames))
  (assert (string? message))
  (pc/add-update-frame-timeout (unique-key)
    #(dosync
       (let [r (at/get-ref t)]
         (when-not (::at/closed @r)
           (t (at/is false message) :close))))
    frames))

;; ============================================================
;; the tests
;; ============================================================

(def state-1
  {:v2 (UnityEngine.Vector2. 1 2)
   :v3 (UnityEngine.Vector3. 1 2 3)
   :vec [:a :b :c :d]
   :set #{:a :b :c :d}})

(def state-2
  {:a :A
   :b :B})

;; This one requires some existing functionality to work.
;; Could even make its run conditional on some other tests
;; passing using a watch, if we want to be fancy.
(defn update-function-testable-example-1 [obj k]
  (let [tester-fn (ac/state obj :tester)]
    (tester-fn obj k)))

(defn runs-once [f]
  (let [ran (volatile! false)]
    (fn [& args]
      (locking ran
        (when (not @ran)
          (vreset! ran true)
          (apply f args))))))

(defn set-tester [obj f]
  (ac/state+ obj :tester (runs-once f)))

;; ============================================================
;; The Tests
;; ============================================================

(at/clear-registry)

;; ------------------------------------------------------------

(at/deftest scene-graph-system t
  (as-sub-closing [t "GameObject traversal"]
    (with-temp-objects :lit [x y z]
      (.SetParent (.transform y) (.transform x))
      (.SetParent (.transform z) (.transform x))
      (t
        (at/is (= (reduce (fn [acc x] (conj acc x)) [] x) [y z]) "Can reduce on GameObjects")
        (at/is (= (into [] x) [y z]) "Can transduce on GameObjects"))))  
  (as-sub-closing [t "gobj"]
    (with-temp-objects :lit [x]
      (t (at/is (= (ac/gobj x) x) "If argument is a live GameObject, returns it.")))
    (with-temp-objects :lit [x]
      (UnityEngine.Object/DestroyImmediate x)
      (t (at/is (= (ac/gobj x) nil) "If argument is a destroyed GameObject, returns nil.")))
    (with-temp-objects :lit [x]
      (let [mf (.AddComponent x UnityEngine.MeshFilter)]
        (t (at/is (= (ac/gobj mf) x) "If argument is a live Component, returns its containing GameObject."))))
    (with-temp-objects :lit [x]
      (let [mf (.AddComponent x UnityEngine.MeshFilter)]
        (UnityEngine.Object/DestroyImmediate mf)
        (t (at/is (= (ac/gobj mf) nil) "If argument is a destroyed Component, returns nil."))))
    (t
      (at/is (= (ac/gobj nil) nil) "If argument is nil, returns nil.")
      (at/throws (ac/gobj :not-a-unity-object)
        System.ArgumentException
        "If argument is neither a GameObject, Component, nor nil, throws System.ArgumentException."))))

;; ------------------------------------------------------------
;; cmpt system

(defmacro nil-behavior [t f]
  (assert (symbol? t))
  (assert (symbol? f))
  `(let [t# ~t]
     (t#
       (at/throws
         (~f nil UnityEngine.BoxCollider)
         ArgumentNullException
         "nil `x` throws ArgumentNullException.")
       (at/throws
         (~f :not-a-unity-object UnityEngine.BoxCollider)
         ArgumentException
         "Non-GameObject or Component `x` throws ArgumentException."))
     (with-temp-objects :lit [x#]
       (t# (at/throws
             (~f x# nil)
             ArgumentException
             "nil `t` throws ArgumentException.")))
     (with-temp-objects :lit [x#]
       (t# (at/throws
             (~f x# :not-a-type)
             InvalidCastException ;; unfortunately
             "Non-Type `t` throws InvalidCastException.")))
     (let [x# (GameObject. "test")]
       (UnityEngine.Object/DestroyImmediate x#)
       (t# (at/throws
             (~f x# UnityEngine.BoxCollider)
             ArgumentException
             "Destroyed `x` throws ArgumentException.")))))

(at/deftest cmpt-system t
  (as-sub-closing [t "cmpt"]
    ;; does cmpt work with nil x?
    ;; does cmpt work with null x?
    ;; does cmpt work with non-GameObject, non-Component x?
    ;; does cmpt work with GameObject x?
    ;; does cmpt work with Component x?
    ;; does cmpt throw the right error if t is a string?
    (nil-behavior t ac/cmpt)
    (with-temp-objects :lit [x]
      (let [bc (.AddComponent x UnityEngine.BoxCollider)]
        (t (at/is (= (ac/cmpt x UnityEngine.BoxCollider) bc)
             "Retrieves present component with GameObject `x`."))))
    (with-temp-objects :lit [x]
      (let [bc (.AddComponent x UnityEngine.BoxCollider)]
        (at/throws
          (ac/cmpt (.transform x) UnityEngine.BoxCollider)
          ArgumentException
          "Throws ArgumentException with Component `x`."))))
  (as-sub-closing [t "cmpts"] ;; should this return nil or an empty array under various conditions?
    ;; does cmpts work with nil x?
    ;; does cmpts work with null x?
    ;; does cmpts work with non-GameObject, non-Componenet x?
    ;; does cmpts work with GameObject x?
    ;; does cmpts work with Component x?
    (nil-behavior t ac/cmpt)
    (with-temp-objects :lit [x]
      (let [bc (.AddComponent x UnityEngine.BoxCollider)]
        (t
          (at/is (instance? |UnityEngine.Component[]| (ac/cmpts x UnityEngine.BoxCollider))
            "Retrieves Component array with GameObject `x` and present Component.")
          (at/is (= (first (ac/cmpts x UnityEngine.BoxCollider)) bc)
            "Retrieves correct Components with GameObject `x` and present Component."))))
    (with-temp-objects :lit [x]
      (let [bc (.AddComponent x UnityEngine.BoxCollider)]
        (t (at/throws
             (first (ac/cmpts (.transform x) UnityEngine.BoxCollider))
             ArgumentException
             "Throws ArgumentException with Component `x`.")))))
  (as-sub-closing [t "cmpt+"]
    ;; does cmpt+ work with nil x?
    ;; does cmpt+ work with null x?
    ;; does cmpt+ work with non-GameObject, non-Componenet x?
    ;; does cmpt+ work with GameObject x?
    ;; does cmpt+ work with Component x?

    ;; I guess it should work the same for nil and null x?
    (nil-behavior t ac/cmpt)
    (with-temp-objects :lit [x]
      (let [bc (ac/cmpt+ x UnityEngine.BoxCollider)]
        (t (at/is (= (.GetComponent x UnityEngine.BoxCollider) bc)
             "Attaches and returns new Component instance with GameObject `x`."))))
    (with-temp-objects :lit [x]
      (as-sub-closing [t "Attaching new Component instance with Component `x`."]
        (t (at/throws
             (ac/cmpt+ (.GetComponent x UnityEngine.Transform) UnityEngine.BoxCollider)
             ArgumentException
             "Throws ArgumentException.")))))
  (as-sub-closing [t "cmpt-"]
    ;; does cmpt- work with nil x?
    ;; does cmpt- work with null x?
    ;; does cmpt- work with non-GameObject, non-Componenet x?
    ;; does cmpt- work with GameObject x?
    ;; does cmpt- work with Component x?
    (nil-behavior t ac/cmpt-)
    (as-sub [t "Removes all attached Components of given type with GameObject `x`."]
      (with-temp-objects
        :frames 4
        :lit [x]
        (dotimes [_ 3]
          (.AddComponent x UnityEngine.BoxCollider))
        (ac/cmpt- x UnityEngine.BoxCollider)
        (pc/add-update-frame-timeout (unique-key)
          (fn []
            (t (at/is (= (seq (remove ac/null? (.GetComponents x UnityEngine.BoxCollider))) nil))
              :close))
          2)
        (close-after-frames t 5 "didn't complete"))))
  (as-sub-closing [t "if-cmpt"]
    (with-temp-objects
      :lit [x y]
      (let [bc (.AddComponent x UnityEngine.BoxCollider)]
        (t
          (at/is (= (ac/if-cmpt x [bc2 UnityEngine.BoxCollider]
                      bc2)
                    bc)
            "if-cmpt retrieves present component")
          (at/is (nil? (ac/if-cmpt y [bc2 UnityEngine.BoxCollider]
                         bc2))
            "if-cmpt returns nil for absent component and no else branch")
          (at/is (= (ac/if-cmpt y [bc2 UnityEngine.BoxCollider]
                      bc2
                      :else)
                    :else) 
            "if-cmpt returns else branch for absent component"))))))


;; ------------------------------------------------------------
;; hook-state-system

(defn multiarg-test [t]
  (as-sub [t "multiarg-test"]
    (with-temp-objects
      :frames 100
      [x (ac/create-primitive :cube "x")
       y (ac/create-primitive :cube "y")]
      (ac/with-cmpt x [tr-x Transform
                       rb-x UnityEngine.Rigidbody]
        (ac/with-cmpt y [tr-y Transform
                         rb-y UnityEngine.Rigidbody]
          (set! (.position tr-x) (al/v3 2 0 0))
          (set! (.position tr-y) (al/v3 -2 0 0))
          (doseq [rb [rb-x rb-y]] ;; template stuff would work here if you want to avoid the alloc
            (set! (.useGravity rb) false)
            (set! (.isKinematic rb) false))
          (.AddForce rb-x (al/v3 -100 0 0) UnityEngine.ForceMode/Force)
          (.AddForce rb-y (al/v3 100 0 0) UnityEngine.ForceMode/Force)
          (ac/hook+ x :on-collision-enter :test
            (fn [a b c]
              (t
                (at/is (= a x) "first argument is the GameObject")
                (at/is (= b :test) "second argument is the key")
                (at/is (instance? UnityEngine.Collision c) "third argument is additional event parameter")
                :close))))))
    (close-after-frames t 100 "failed to collide")
    t))

;; consider breaking this up
(at/deftest hook-state-system t
  (as-sub-closing [t "basic state tests"]
    (with-temp-objects :lit [obj]
      (t
        (at/is (= (ac/state obj) nil) "state is nil for fresh obj")
        (at/is (= (ac/state obj :absent-key) nil) "state (with key) is nil for fresh obj")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (ac/state+ obj :test-2 state-2)
      (t (at/is (= (ac/state obj)
                   {:test state-1
                    :test-2 state-2})
           "no-key state recovers all state entries")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (t (at/is (= (ac/state obj :test) state-1)
           "basic state roundtrip")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (at/is (= (ac/state obj :absent-key) nil) "state (with key) is nil if key is absent")))
  (as-sub-closing [t "state+"]
    (with-temp-objects :lit [obj-1]
      (ac/state+ obj-1 :test state-1)
      (ac/state+ obj-1 :test state-2)
      (t (at/is (= (ac/state obj-1 :test) state-2)
           "state+ overwrites"))))
  (as-sub-closing [t "state-"]
    (with-temp-objects :lit [obj]
      (t (at/is (do (ac/state- obj :absent-key) true) "state- runs for fresh objects")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (ac/state- obj :test)
      (t (at/is (= (ac/state obj) nil) "subtracted `state` is nil"))
      (t (at/is (= (ac/state obj :test) nil) "subtracted `state` (with key) is nil")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (ac/state+ obj :test-2 state-2)
      (ac/state- obj :test)
      (t (at/is (= (ac/state obj) {:test-2 state-2})
           "partial state retrieval after state-"))))
  (as-sub-closing [t "state serialization via instantiate"]
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (ac/state+ obj :test-2 state-2)
      (with-temp-objects [obj-2 (ac/instantiate obj)]
        (t (at/is (= (ac/state obj)
                     (ac/state obj-2))
             "round-trip serialization")))))
  (as-sub-closing [t "hook+"]
    ;; requires play mode
    ;; not throwing if not in play mode, though, because
    ;; we need to test both edit and play mode.
    (when (Arcadia.UnityStatusHelper/IsInPlayMode)
      (as-sub t
        (with-temp-objects
          :frames 2
          :lit [obj-a]
          (ac/state+ obj-a :test state-1)
          (ac/hook+ obj-a :update :test
            (fn [obj-a' k]
              (t 
                (at/is (= obj-a obj-a') "correct obj")
                (at/is (= k :test) "correct key")
                (at/is (= state-1 (ac/state obj-a' k)) "correct state")
                :close)))))
      (as-sub [t "var hook+"]
        (with-temp-objects
          :frames 5
          :lit [obj]
          (set-tester obj 
            (fn hook-tester [obj' k]
              (t
                (at/is true "var hook ran")
                (at/is (= k :test) "correct key for var hook")
                (at/is (= obj' obj) "correct obj for var hook")
                :close)))
          (ac/hook+ obj :update :test #'update-function-testable-example-1))
        ;; set timeout
        (close-after-frames t 5 "var hook+ test didn't complete"))))
  (as-sub t "hook-"
    (let [f (fn [_ _])]
      (with-temp-objects :lit [obj]
        (t (at/is (do (ac/hook- obj :update :absent-key) true) "hook- runs for fresh object"))
        (ac/hook+ obj :update :testing f)
        (t
          (at/is (do (ac/hook- obj :update :testing) true) "hook- with two keys runs for hooked object")
          (at/is (= (ac/hook obj :update :testing) nil) "hook- with two keys works for hooked object"))))
    (t :close))
  (multiarg-test t)
  (as-sub [t "role system"]
    (let [r1 {:update #'update-function-testable-example-1
              :state state-1}
          r2 {:update #'update-function-testable-example-1
              :state state-2}
          r3 {:update (fn [_ _])}]
      (as-sub [t "role+"]
        (with-temp-objects
          :frames 3
          :lit [obj]
          (set-tester obj
            (fn [obj k]
              (t
                (at/is true "`:update` ran for `role+`")
                (at/is (= k :test-role) "correct key in role `update`")
                (at/is (= state-1 (ac/state obj :test-role)) "correct state retrieval in role `update`")
                :close)))
          (ac/role+ obj :test-role r1))
        (close-after-frames t 4 "`role+` test didn't complete"))
      (as-sub-closing t
        (with-temp-objects :lit [obj]
          (ac/role+ obj :test-role-1 r1)
          (ac/role+ obj :test-role-2 r2)
          (t
            (at/is (= (ac/role obj :test-role-1) r1) "can retrive roles using `role`")
            (at/is (= (ac/role obj :not-there) nil) "`role` on absent key returns nil"))
          (ac/role- obj :test-role-2)
          (t (at/is (= (ac/role obj :test-role-2) nil) "`role-` subtracts roles"))
          (ac/role+ obj :test-role-1 r3)
          (t "`role+` overwrites"
            (at/is (= (ac/role obj :test-role-1) r3))
            (at/is (= (ac/state obj :test-role-1) nil) "`role+` overwrites rather than merges"))))
      (as-sub-closing [t "`roles`"]
        (with-temp-objects :lit [obj]
          (ac/role+ obj :test-role-1 r1)
          (ac/role+ obj :test-role-2 r2)
          (t (at/is (= (ac/roles obj) {:test-role-1 r1, :test-role-2 r2})
               "`roles` retrieves previous `role+` data"))
          (ac/role+ obj :test-role-1 r3)
          (t (at/is (= (ac/roles obj) {:test-role-1 r3, :test-role-2 r2})
               "`roles` reflects roles overwritten by `role+`")))
        (let [f1 (fn [_ _])
              s1 (System.Object.)]
          ;; does roles pick up on roles formed by calls to hook+ and state+?          
          (with-temp-objects :lit [obj]
            (ac/hook+ obj :update :test-role-1 f1)
            (ac/state+ obj :test-role-1 s1)
            (t (at/is (= (ac/roles obj) {:test-role-1 {:update f1, :state s1}})
                 "`roles` works for roles built with `hook+` and `state+`")))))
      (as-sub-closing [t "`roles+`"]
        ;; - does roles retrieve roles+?
        ;; - does roles+ work with nil for the roles map?
        ;; - does roles+ work with an empty map for the roles map?        
        (with-temp-objects :lit [obj]
          (let [the-roles {:test-role-1 r1, :test-role-2 r2}]
            (ac/roles+ obj the-roles)
            (t (at/is (= (ac/roles obj) the-roles) "`roles+` can be retrieved by `roles`"))
            (ac/roles+ obj {:test-role-2 r3})
            (t (at/is (= (ac/roles obj) (merge the-roles {:test-role-2 r3})) "roles+ does shallow merge")))))
      (as-sub-closing [t "`role-`"]
        ;; - does role- get reflected in roles?
        ;; - does role- work when the key is absent?
        ;; - does role- work when an object hasn't had any of this stuff done to it yet?
        (let [the-roles {:test-role-1 r1, :test-role-2 r2}]
          (with-temp-objects :lit [obj]
            (ac/roles+ obj the-roles)
            (ac/role- obj :test-role-1)
            (t
              (at/is (= (ac/roles obj) {:test-role-2 r2}) "`role-` reflected in `roles`")))
          (with-temp-objects :lit [obj]
            (ac/roles+ obj the-roles)
            (t
              (at/is ;; TODO: better setup for this sort of thing in tests, where Exceptions are the fail
                (do (ac/role- obj :absent-key)
                    (= (ac/roles obj) the-roles))
                "`role-` works for absent keys")))
          (with-temp-objects :lit [obj]
            (t
              (at/is
                (do (ac/role- obj :absent-key)
                    (= (ac/roles obj) {}))
                "`role-` works for fresh object")))))
      (t :close))
    (t :close))
  )

;; ------------------------------------------------------------
;; core-namespace

(at/deftest core-namespace t
  (as-sub-closing [t "instantiate"]
    (with-temp-objects :lit [original]
      (let [position (al/v3 (rand) (rand) (rand))
            rotation (al/euler (al/v3 (rand) (rand) (rand)))
            clone-0 (ac/instantiate original)
            clone-1 (ac/instantiate original position)
            clone-2 (ac/instantiate original position rotation)]
        (t (at/is (= (.. original transform position)
                     (.. clone-0 transform position))
                  "1-ary instantiate keeps original position"))
        (t (at/is (= (.. original transform rotation)
                     (.. clone-0 transform rotation))
                  "1-ary instantiate keeps original rotation"))
        (t (at/is (= (.. clone-1 transform position)
                     position)
                  "2-ary instantiate uses new position"))
        (t (at/is (= (.. original transform rotation)
                     (.. clone-1 transform rotation))
                  "2-ary instantiate keeps original rotation"))
        (t (at/is (= (.. clone-2 transform position)
                     position)
                  "3-ary instantiate uses new position"))
        (t (at/is (= (.. clone-2 transform rotation)
                     rotation)
                  "3-ary instantiate uses new rotation"))))))

;; ------------------------------------------------------------
;; linear-namespace

(at/deftest linear-namespace t
  (as-sub-closing [t "vector addition"]
    (t (at/is (= (al/v2+ (al/v2 1 2) (al/v2 1 2) (al/v2 1 2))
                 (al/v2 3.0, 6.0))
              "`v2+` works as expected"))
    (t (at/is (= (al/v3+ (al/v3 1 2 3) (al/v3 1 2 3) (al/v3 1 2 3))
                 (al/v3 3.0, 6.0, 9.0))
              "`v3+` works as expected"))
    (t (at/is (= (al/v4+ (al/v4 1 2 3 4) (al/v4 1 2 3 4) (al/v4 1 2 3 4))
                 (al/v4 3.0, 6.0, 9.0, 12.0))
              "`v4+` works as expected"))))
