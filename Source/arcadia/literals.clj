(ns arcadia.literals
  (:import [System.Reflection
            ConstructorInfo
            ParameterInfo]
           [System TimeSpan]
           [UnityEngine Debug]
           [System.Diagnostics Stopwatch]))

;; ============================================================
;; object database 
;; ============================================================

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


;; ============================================================
;; constructor wrangling
;; ============================================================

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

;; ============================================================
;; value types
;; ============================================================

(def value-types
  (->> (Assembly/Load "UnityEngine")
       .GetTypes
       (filter #(.IsValueType %))
       (remove #(.IsEnum %))))

(defn parser-for-value-type [t]
  (let [ctor (constructor-arguments t)
        params (map #(gensym %) ctor)]
    `(defn ~(symbol (str "parse-" (.Name t))) [~(vec params)]
       (new ~(-> t .FullName symbol)
            ~@params))))

(defn print-dup-for-value-type [type]
  (let [param (gensym "value_1")
        type-name (symbol (.FullName type))
        ctor-arg-names (constructor-arguments type)
        ctor-params (map #(list* '. param (-> % symbol list))
                         ctor-arg-names)]
    `(defmethod print-dup ~type [~(with-meta param {:tag type}) stream#]
       (.Write stream#
               (str ~(str "#=(" type-name ". ")
                    ~@(drop-last (interleave ctor-params (repeat " ")))
                    ")")))))

(defn reconstructor
  "A vector of field access forms that mirrors a constructor's arguments"
  [targetsym t]
  (mapv
    (fn [arg]
      `(. ~targetsym
          ~(symbol arg)))
    (constructor-arguments t)))

(defn print-method-for-value-type [type]
  (let [v (with-meta (gensym "value")
                     {:tag type})
        w (with-meta (gensym "writer")
                     {:tag 'System.IO.TextWriter})]
    `(defmethod print-method ~type [~v ~w]
       (.Write ~w
               (str ~(str "#unity/" (.Name type) " ")
                    ~(reconstructor v type))))))

(defn install-parser-for-value-type [type]
  `(alter-var-root
     (var clojure.core/*data-readers*)
     assoc
     (quote ~(symbol (str "unity/" (.Name type))))
     (var ~(symbol (str "arcadia.literals/parse-" (.Name type))))))

;; (def ^Stopwatch sw (Stopwatch.))
;; (.Start sw)

;; (doseq [t value-types]
;;   (eval (parser-for-value-type t))
;;   (eval (install-parser-for-value-type t))
;;   (eval (print-method-for-value-type t))
;;   (eval (print-dup-for-value-type t)))

;; (.Stop sw)

;; (Debug/Log
;;   (str "Milliseconds to value type parser eval stuff: "
;;        (.TotalMilliseconds (.Elapsed sw))))
;; results:
;; 1502.21
;; 1524.607
;; 1527.86

;; results after aot:
;; 1278.692


(defmacro ^:private value-type-stuff []
  (cons `do
    (for [t value-types]
      (list `do
        (parser-for-value-type t)
        (install-parser-for-value-type t)
        (print-method-for-value-type t)
        (print-dup-for-value-type t)))))

(def ^Stopwatch sw (Stopwatch.))
(.Start sw)

(value-type-stuff)

(.Stop sw)
(Debug/Log
  (str "Milliseconds to value type parser eval stuff: "
       (.TotalMilliseconds (.Elapsed sw))))

;; results after AOT:
;; ~44 ms




;; ============================================================
;; object types
;; ============================================================

(def object-types
  (->> (Assembly/Load "UnityEngine")
       .GetTypes
       (filter #(isa? % UnityEngine.Object))))

(defn parse-object [id]
  (or (db-get id)
      (do
        (UnityEngine.Debug/Log (str "Cant find object with ID " id))
        (UnityEngine.Object.))))

(defn print-dup-for-object-type [type]
  (let [param (gensym "obj_a_")
        type-name (symbol (.FullName type))]
    `(defmethod print-dup
       ~type [~(with-meta param {:tag type}) stream#]
       (.Write stream#
               (str "#=(arcadia.literals/db-get "
                    (.GetInstanceID ~param)
                    ")")))))

(defn print-method-for-object-type [type]
  (let [v (with-meta (gensym "obj_b_")
                     {:tag type})
        w (with-meta (gensym "writer")
                     {:tag 'System.IO.TextWriter})]
    `(defmethod print-method ~type [~v ~w]
       (.Write ~w
               (str ~(str "#unity/" (.Name type) " ")
                    (arcadia.literals/db-put ~v))))))

(defn install-parser-for-object-type [type]
  `(alter-var-root
     (var clojure.core/*data-readers*)
     assoc
     (quote ~(symbol (str "unity/" (.Name type))))
     (var ~(symbol (str "arcadia.literals/parse-object")))))

(defmacro ^:private object-type-stuff []
  (cons `do
    (for [t object-types]
      (list `do
        ;; object types share the same parser
        (install-parser-for-object-type t)
        (print-method-for-object-type t)
        (print-dup-for-object-type t)))))

(object-type-stuff)

;; AnimationCurves are different
;; finish
(comment 
  (defmethod print-dup
    UnityEngine.AnimationCurve [ac stream]
    (.Write stream
            (str "#=(UnityEngine.AnimationCurve. "
                 "(into-array ["
                 (apply str 
                        (->> ac
                             .keys
                             (map #(str "(UnityEngine.Keyframe. "
                                        (.time %)
                                        (.value %)
                                        (.inTangent %)
                                        (.outTangent %)
                                        ")"))
                             (interleave (repeat " "))))
                 ")")))
  (defmethod print-method
    UnityEngine.AnimationCurve [ac w]
    (.Write w
            (str "#unity/AnimationCurve"
                 (.GetInstanceID ~v))))
  
  (defn parse-AnimationCurve [v]
    (new UnityEngine.AnimationCurve (into-array (map eval (first v)))))
  
  (alter-var-root
    #'clojure.core/*data-readers*
    assoc
    'unity/AnimationCurve
    #'arcadia.literals/parse-AnimationCurve))
