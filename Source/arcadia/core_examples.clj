(ns arcadia.core-examples
  (:use arcadia.core
        arcadia.linear))

(defn ^{:example #'log}
  log-example-01 []
  ;; strings work
  (log "Hello, Arcadia!"))

(defn ^{:example #'log}
  log-example-02 []
  ;; arguments are combined as in str
  (let [n (rand)]
    (log "Want a number? " n ". Done!")))

(defn ^{:example #'log}
  log-example-03 []
  ;; log can also be used as quick debug hook
  (hook+ (object-named "Main Camera") :update log))

(defn ^{:example #'null-obj?}
  null-obj-example-01 []
  ;; Unity interop calls will return Unity's null object
  ;; The Main Camera has no Rigidbody by default
  (let [rb (.GetComponent
             (object-named "Main Camera")
             UnityEngine.Rigidbody)]
    rb              ;; => #unity/Object 0
    (boolean rb)    ;; => true
    (null-obj? rb)) ;; => true
  )

(defn ^{:example #'null-obj?}
  null-obj-example-02 []
  ;; arcadia functions use null-obj? internally to present
  ;; a more consistent view of the Unity scene graph
  (cmpt (object-named "Main Camera") UnityEngine.Rigidbody) ;; => nil
  )

(defn ^{:example #'instantiate}
  instantiate-example-01 []
  ;; create a new camera identical to the default camera   
  (let [new-camera (instantiate (object-named "Main Camera"))]
    new-camera          ;; => #unity/Object -6904
    (.name new-camera)) ;; "Main Camera(Clone)"
  )

(defn ^{:example #'instantiate}
  instantiate-example-02 []
  ;; create a new camera at a new position
  (instantiate (object-named "Main Camera")
               (v3 100 20 10)))

(defn ^{:example #'instantiate}
  instantiate-example-03 []
  ;; create a new camera at a new position
  ;; looking at the origin
  (instantiate (object-named "Main Camera")
               (v3 100 20 10)
               (qlookat (v3 100 20 10)
                        (v3 0))))

(defn ^{:example #'create-primitive}
  create-primitive-example-01 []
  ;; create the default Unity cube
  (create-primitive :cube))

(defn ^{:example #'destroy}
  destroy-example-01 []
  ;; remove the default camera
  (destroy (object-named "Main Camera")))

(defn ^{:example #'destroy}
  destroy-example-02 []
  ;; remove every object with player in its name
  (doseq [o (objects-named #".*player.*")]
    (destroy o)))

(defn ^{:example #'destroy}
  destroy-example-03 []
  ;; remember that map is lazy, so the following will
  ;; not work as expected
  (map destroy (objects-named #".*player.*"))
  ;; instead, force evaluation with dorun or do all
  (dorun (map destroy (objects-named #".*player.*")))
  ;; mapv returns a vector, and also forces evaluation
  (mapv destroy (objects-named #".*player.*")))

(defn ^{:example #'object-typed}
  object-typed-example-01 []
  ;; components are returned directly
  (object-typed UnityEngine.Camera))

(defn ^{:example #'object-typed}
  object-typed-example-01 []
  ;; any type that can be in the Unity scene graph works
  (object-typed UnityEngine.GameObject))

(defn ^{:example #'objects-typed}
  objects-typed-example-01 []
  ;; components are returned directly
  (objects-typed UnityEngine.Camera))

(defn ^{:example #'objects-typed}
  objects-typed-example-02 []
  ;; superclasses work too
  (count (objects-typed UnityEngine.Camera))    ;; => 1
  (count (objects-typed UnityEngine.Component)) ;; => 8
  (count (objects-typed UnityEngine.Object))    ;; => 20
  )

(defn ^{:example #'object-named}
  object-named-example-01 []
  ;; get the default camera object
  (object-named "Main Camera"))

(defn ^{:example #'objects-named}
  objects-named-example-01 []
  ;; cube primitives are named Cube by default
  (dotimes [_ 20]
    (create-primitive :cube))
  (count (objects-named "Cube")) ;; # => 20
  )

(defn ^{:example #'objects-named}
  objects-named-example-02 []
  ;; regular expressions work as well
  (dotimes [_ 20]
    (let [cube (create-primitive :cube)]
      (set! (.name cube) (str (gensym "cube")))))
  (count (objects-named #"cube.*")) ;; # => 20
  )
