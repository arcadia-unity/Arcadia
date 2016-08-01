(ns arcadia.literals
  (:import [System.Reflection
            ConstructorInfo
            ParameterInfo]))

(defn type-name [sym]
  (.Name ^Type (resolve sym)))

(defn longest-constructor-signature
  "Returns the longest parameter list for all constructors in type t"
  [^System.Type t]
  (->> (.GetConstructors t)
       (map #(.GetParameters ^ConstructorInfo %))
       (sort (fn [a b] (compare (count a)
                                (count b))))
       last))

(def irregular-constructor-arguments
  {UnityEngine.Plane                    '[normal distance]
   UnityEngine.GradientColorKey         '[color time]
   UnityEngine.Rect                     '[x y width height]
   UnityEngine.NetworkPlayer            '[ipAddress port]
   UnityEngine.ClothSphereColliderPair  '[first second]})

(defn constructor-arguments
  "A sequence of field names for t in the order of one of t's constructors"
  [^Type t]
  (or (irregular-constructor-arguments t)
      (map #(.Name ^ParameterInfo %)
           (longest-constructor-signature t))))

(defmacro vector-constructor
  "Generates a fn that takes a vector and constructs an instance of t. Vector
  must have enough entries to fill the longest constructor arity in t."
  [t]
  (let [ctor (constructor-arguments (resolve t))
        params (map #(gensym %) ctor)]
    (list 'fn [(vec params)]
       (list* 'new t params))))

(defmacro install-type-parser [t]
  `(def ~(symbol (str "parse-" (type-name t)))
     (vector-constructor ~t)))

(defmacro reconstructor
  "A vector of field access forms that mirrors a constructor's arguments"
  [targetsym t]
  (mapv
    (fn [arg]
      `(. ~targetsym
          ~(symbol arg)))
    (constructor-arguments (resolve t))))

(defmacro type-printer
  "Generates a fn that takes an object and a writer and prints
  the object out as a reader literal. Meant for print-method."
  [t]
  (let [v (with-meta (gensym "value")
                     {:tag t})
        w (with-meta (gensym "writer")
                     {:tag 'System.IO.TextWriter})]
    `(fn [~v ~w]
       (.Write ~w
               (str "#unity/" ~(type-name t) " "
                    (reconstructor ~v ~t))))))

(defmacro install-type-printer
  "Register a printer for t with clojure's print-method"
  [t]
  `(. ~(with-meta 'print-method
                  {:tag 'clojure.lang.MultiFn})
      ~'addMethod ~t (type-printer ~t)))

(defmacro install-printers-and-parsers [types]
  `(do
     ~@(mapcat
         (fn [t]
           [`(install-type-printer ~t)
            `(install-type-parser ~t)])
         types)))

(install-printers-and-parsers
  [UnityEngine.Vector2
   UnityEngine.Vector3
   UnityEngine.Vector4
   UnityEngine.Quaternion
   UnityEngine.Color
   UnityEngine.Color32
   UnityEngine.Ray
   UnityEngine.Ray2D
   UnityEngine.Bounds
   UnityEngine.LOD
   UnityEngine.Plane
   UnityEngine.GradientColorKey
   UnityEngine.GradientAlphaKey
   UnityEngine.Rect
   UnityEngine.NetworkPlayer
   UnityEngine.Keyframe
   UnityEngine.MatchTargetWeightMask])

;; AnimationCurve is a little different
(.addMethod print-method
            UnityEngine.AnimationCurve
            (fn [v w]
              (.Write w
                      (str "#unity/AnimationCurve "
                           [(into [] (.keys v))]))))

(defn parse-AnimationCurve [v]
  (new UnityEngine.AnimationCurve (into-array (map eval (first v)))))


;; TODO where should this lift? 
;; TODO is the atom needed?
(def ^:dynamic *object-db* (atom {}))

(defn db-put [obj]
  (let [id (.GetInstanceID obj)]
    (swap! *object-db* assoc id obj)
    id))

;; TODO handle nil
;; clojure errors out if this returns nil
;; considers the dispatch to have failed... 
(defn db-get [id]
  (get @*object-db* id))

(.addMethod
  print-method
  UnityEngine.Object
  (fn [obj w]
    (.Write w (str "#unity/Object " (db-put obj)))))

(defn parse-Object [id]
  (or (db-get id)
      (do
        (UnityEngine.Debug/Log (str "Cant find object with ID " id))
        (UnityEngine.Object.))))

(alter-var-root
  #'clojure.core/*data-readers*
  (constantly
    {'unity/GradientAlphaKey #'arcadia.literals/parse-GradientAlphaKey
     'unity/Bounds #'arcadia.literals/parse-Bounds
     'unity/Rect #'arcadia.literals/parse-Rect
     'unity/Vector2 #'arcadia.literals/parse-Vector2
     'unity/Keyframe #'arcadia.literals/parse-Keyframe
     'unity/GradientColorKey #'arcadia.literals/parse-GradientColorKey
     'unity/NetworkPlayer #'arcadia.literals/parse-NetworkPlayer
     'unity/LOD #'arcadia.literals/parse-LOD
     'unity/MatchTargetWeightMask #'arcadia.literals/parse-MatchTargetWeightMask
     'unity/Vector4 #'arcadia.literals/parse-Vector4
     'unity/Quaternion #'arcadia.literals/parse-Quaternion
     'unity/Color #'arcadia.literals/parse-Color
     'unity/Ray #'arcadia.literals/parse-Ray
     'unity/Object #'arcadia.literals/parse-Object
     'unity/Ray2D #'arcadia.literals/parse-Ray2D
     'unity/Plane #'arcadia.literals/parse-Plane
     'unity/Color32 #'arcadia.literals/parse-Color32
     'unity/AnimationCurve #'arcadia.literals/parse-AnimationCurve
     'unity/Vector3 #'arcadia.literals/parse-Vector3}))