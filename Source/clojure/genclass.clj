;   Copyright (c) David Miller. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;   DM: This is one of the few bootstrap *.clj files where I did not even try to do a line-by-line
;       modification of the JVM version.  Too many differences.
;       I put more of the support into C# rather than in Clojure, just so I could bang out the code quicker.
;       This could be redone eventually. 


 (in-ns 'clojure.core)
 
 (import '(System.Reflection ConstructorInfo))

 ;;; The options-handling code here is taken from the JVM version.  
 
 
 (defn- ctor-sigs [^Type super]
  (for [^ConstructorInfo ctor (.GetConstructors super)
        :when (not (.IsPrivate ctor))]
    (apply vector (map #(.ParameterType %) (.GetParameters ctor)))))
 
 
 (def ^{:private true} prim->class
     {'int Int32
	  'ints (Type/GetType "System.Int32[]")
      'long Int64
	  'longs (Type/GetType "System.Int64[]")
      'float Single
	  'floats (Type/GetType "System.Single[]")
      'double Double
	  'doubles (Type/GetType "System.Double[]")
      'void  System.Void
      'short Int16 
	  'shorts (Type/GetType "System.Int16[]")
      'boolean Boolean
	  'booleans (Type/GetType "System.Boolean[]")
      'byte Byte
	  'bytes (Type/GetType "System.Byte[]")
	  'sbyte SByte
	  'sbytes (Type/GetType "System.SByte[]")
	  'ushort UInt16
	  'ushorts (Type/GetType "System.UInt16[]")
	  'uint  UInt32
	  'uints (Type/GetType "System.UInt32[]")
	  'ulong UInt64
	  'ulongs (Type/GetType "System.UInt64[]")
      'char Char
	  'chars (Type/GetType "System.Char[]")})

 
 (defn- ^Type the-class [x]					;;; ^Class
  (cond 
   (class? x) x
   (contains? prim->class x) (prim->class x)
   :else (let [strx (str x)]
           (clojure.lang.RT/classForName 
            (if (some #{\. \[} strx)           
              strx
              (str "System." strx))))))         ;;;(str "java.lang." strx))))))
 
 (defn- the-class-maybe-by-ref [x]
   (cond
      (seq? x) (list (first x) (the-class (second x)))    ; (by-ref v)
      :else (the-class x)))
  
;; someday this can be made codepoint aware
(defn- valid-java-method-name
  [^String s]
  (= s (clojure.lang.Compiler/munge s)))

(defn- validate-generate-class-options
  [{:keys [methods]}]
  (let [[mname] (remove valid-java-method-name (map (comp str first) methods))]
    (when mname (throw (ArgumentException. (str "Not a valid method name: " mname))))))     ;;; IllegalArgumentException.
  
 (defn- generate-class [options-map]
   (validate-generate-class-options options-map)
   (let [default-options {:prefix "-" :load-impl-ns true :impl-ns (ns-name *ns*)}
        {:keys [name extends implements constructors methods main factory state init exposes 
                exposes-methods prefix load-impl-ns impl-ns post-init class-attributes]}    ;;; DM: Added class-attributes
          (merge default-options options-map)
        name (str name)
        super (if extends (the-class extends) Object)
        interfaces (map the-class implements)
        supers (cons super interfaces)
        ctor-sig-map (doall (or constructors (zipmap (ctor-sigs super) (ctor-sigs super))))
        class-mapper (fn [coll] (with-meta (doall (map the-class coll)) (meta coll)))
        ctor-sig-type-map (doall (zipmap (doall (map class-mapper (keys ctor-sig-map))) (doall (map class-mapper (vals ctor-sig-map)))))
        cname (. name (Replace "." "/"))
        pkg-name name
        impl-pkg-name (str impl-ns)
        impl-cname (.. impl-pkg-name (Replace "." "/") (Replace \- \_))
        init-name (str init)
        post-init-name (str post-init)
        factory-name (str factory)
        state-name (str state)
        main-name "main"
		class-attributes (extract-attributes class-attributes)
        methods (map (fn [x] [(nth x 0) 
                              (map the-class (nth x 1)) 
                              (the-class (nth x 2)) 
                              (:static (meta x))]) 
                         methods)
      ]  
	(clojure.lang.GenClass/GenerateClass
		name super (seq interfaces)
		(seq ctor-sig-map) (seq ctor-sig-type-map) (seq methods)
		exposes exposes-methods  
		prefix  (. clojure.lang.RT booleanCast main) 
		factory-name state-name 
		init-name post-init-name 
		impl-cname impl-pkg-name 
		(. clojure.lang.RT booleanCast  load-impl-ns)
		class-attributes)))
		
	 
 (defmacro gen-class 
  "When compiling, generates compiled bytecode for a class with the
  given package-qualified :name (which, as all names in these
  parameters, can be a string or symbol), and writes the .class file
  to the *compile-path* directory.  When not compiling, does
  nothing. The gen-class construct contains no implementation, as the
  implementation will be dynamically sought by the generated class in
  functions in an implementing Clojure namespace. Given a generated
  class org.mydomain.MyClass with a method named mymethod, gen-class
  will generate an implementation that looks for a function named by 
  (str prefix mymethod) (default prefix: \"-\") in a
  Clojure namespace specified by :impl-ns
  (defaults to the current namespace). All inherited methods,
  generated methods, and init and main functions (see :methods, :init,
  and :main below) will be found similarly prefixed. By default, the
  static initializer for the generated class will attempt to load the
  Clojure support code for the class as a resource from the classpath,
  e.g. in the example case, ``org/mydomain/MyClass__init.class``. This
  behavior can be controlled by :load-impl-ns

  Note that methods with a maximum of 18 parameters are supported.

  In all subsequent sections taking types, the primitive types can be
  referred to by their Java names (int, float etc), and classes in the
  java.lang package can be used without a package qualifier. All other
  classes must be fully qualified.

  Options should be a set of key/value pairs, all except for :name are optional:

  :name aname

  The package-qualified name of the class to be generated

  :extends aclass

  Specifies the superclass, the non-private methods of which will be
  overridden by the class. If not provided, defaults to Object.

  :implements [interface ...]

  One or more interfaces, the methods of which will be implemented by the class.

  :init name

  If supplied, names a function that will be called with the arguments
  to the constructor. Must return [ [superclass-constructor-args] state] 
  If not supplied, the constructor args are passed directly to
  the superclass constructor and the state will be nil

  :constructors {[param-types] [super-param-types], ...}

  By default, constructors are created for the generated class which
  match the signature(s) of the constructors for the superclass. This
  parameter may be used to explicitly specify constructors, each entry
  providing a mapping from a constructor signature to a superclass
  constructor signature. When you supply this, you must supply an :init
  specifier. 

  :post-init name

  If supplied, names a function that will be called with the object as
  the first argument, followed by the arguments to the constructor.
  It will be called every time an object of this class is created,
  immediately after all the inherited constructors have completed.
  Its return value is ignored.

  :methods [ [name [param-types] return-type], ...]

  The generated class automatically defines all of the non-private
  methods of its superclasses/interfaces. This parameter can be used
  to specify the signatures of additional methods of the generated
  class. Static methods can be specified with ^{:static true} in the
  signature's metadata. Do not repeat superclass/interface signatures
  here.

  :main boolean

  If supplied and true, a static public main function will be generated. It will
  pass each string of the String[] argument as a separate argument to
  a function called (str prefix main).

  :factory name

  If supplied, a (set of) public static factory function(s) will be
  created with the given name, and the same signature(s) as the
  constructor(s).
  
  :state name

  If supplied, a public final instance field with the given name will be
  created. You must supply an :init function in order to provide a
  value for the state. Note that, though final, the state can be a ref
  or agent, supporting the creation of Java objects with transactional
  or asynchronous mutation semantics.

  :exposes {protected-field-name {:get name :set name}, ...}

  Since the implementations of the methods of the generated class
  occur in Clojure functions, they have no access to the inherited
  protected fields of the superclass. This parameter can be used to
  generate public getter/setter methods exposing the protected field(s)
  for use in the implementation.

  :exposes-methods {super-method-name exposed-name, ...}

  It is sometimes necessary to call the superclass' implementation of an
  overridden method.  Those methods may be exposed and referred in 
  the new method implementation by a local name.

  :prefix string

  Default: \"-\" Methods called e.g. Foo will be looked up in vars called
  prefixFoo in the implementing ns.

  :impl-ns name

  Default: the name of the current ns. Implementations of methods will be 
  looked up in this namespace.

  :load-impl-ns boolean

  Default: true. Causes the static initializer for the generated class
  to reference the load code for the implementing namespace. Should be
  true when implementing-ns is the default, false if you intend to
  load the code via some other method."
  {:added "1.0"}

  [& options]
    (let [x *compile-files*]
      (when *compile-files*
        (let [options-map (into1 {} (map vec (partition 2 options)))]
          `'~(generate-class options-map)))))
          
          
          
;;;;;;;;;;;;;;;;;;;; gen-interface ;;;;;;;;;;;;;;;;;;;;;;
;; based on original contribution by Chris Houser

(defn- generate-interface
  [{:keys [name extends methods]}]
  (let [extendTypes (map the-class extends)
        methodSigs (map (fn [[mname pclasses rclass pmetas]] [mname (map the-class-maybe-by-ref pclasses) (the-class rclass) pmetas]) methods)]
	(clojure.lang.GenInterface/GenerateInterface (str name) (extract-attributes (meta name)) extendTypes methodSigs)))


(defmacro gen-interface
  "When compiling, generates compiled bytecode for an interface with
  the given package-qualified :name (which, as all names in these
  parameters, can be a string or symbol), and writes the .class file
  to the *compile-path* directory.  When not compiling, does nothing.
 
  In all subsequent sections taking types, the primitive types can be
  referred to by their Java names (int, float etc), and classes in the
  java.lang package can be used without a package qualifier. All other
  classes must be fully qualified.
 
  Options should be a set of key/value pairs, all except for :name are
  optional:

  :name aname

  The package-qualified name of the class to be generated

  :extends [interface ...]

  One or more interfaces, which will be extended by this interface.

  :methods [ [name [param-types] return-type], ...]

  This parameter is used to specify the signatures of the methods of
  the generated interface.  Do not repeat superinterface signatures
  here."
  {:added "1.0"}

  [& options]
  (let [options-map (into1 {} (map vec (partition 2 options))) ]
          `'~(generate-interface options-map)))