;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; Java-specific parts of clojure.reflect
(in-ns 'clojure.reflect)

(require '[clojure.set :as set]
         '[clojure.string :as str])

(import '[System.Reflection TypeAttributes MethodAttributes FieldAttributes PropertyAttributes BindingFlags])
 
;(import '[clojure.asm ClassReader ClassVisitor Type]
;         '[java.lang.reflect Modifier]
;         java.io.InputStream)

(extend-protocol TypeReference
  clojure.lang.Symbol
  (typename [s]  (str s))    ;;;  (str/replace (str s) "<>" "[]")) -- we keep them as-is
    
  Type
  (typename
   [t]
    (or (.FullName t) (.Name t))))

(defn- typesym
  "Given a typeref, create a legal Clojure symbol version of the
   type's name."
  [t]
  (-> (typename t)
      ;;;(str/replace "[]" "<>")
      (symbol)))


(def class-flags
  [ [:public               TypeAttributes/VisibilityMask      TypeAttributes/Public]
	[:nested-public        TypeAttributes/VisibilityMask      TypeAttributes/NestedPublic]
	[:nested-private       TypeAttributes/VisibilityMask      TypeAttributes/NestedPrivate]
	[:nested-family        TypeAttributes/VisibilityMask      TypeAttributes/NestedFamily]
	[:nested-assembly      TypeAttributes/VisibilityMask      TypeAttributes/NestedAssembly]
	[:nested-famandassem   TypeAttributes/VisibilityMask      TypeAttributes/NestedFamANDAssem]
	[:nested-famorassem    TypeAttributes/VisibilityMask      TypeAttributes/NestedFamORAssem]
	[:auto-layout          TypeAttributes/LayoutMask          TypeAttributes/AutoLayout]
    [:sequential-layout    TypeAttributes/LayoutMask          TypeAttributes/SequentialLayout]
    [:explicit-layout      TypeAttributes/LayoutMask          TypeAttributes/ExplicitLayout]
	[:class                TypeAttributes/ClassSemanticsMask  TypeAttributes/Class]
	[:interface            TypeAttributes/ClassSemanticsMask  TypeAttributes/Interface]
	[:abstract             TypeAttributes/Abstract            TypeAttributes/Abstract]
	[:sealed               TypeAttributes/Sealed              TypeAttributes/Sealed]
	[:special-name         TypeAttributes/SpecialName         TypeAttributes/SpecialName]
	[:import               TypeAttributes/Import              TypeAttributes/Import]
	[:serializable         TypeAttributes/Serializable        TypeAttributes/Serializable]
	[:ansi-class           TypeAttributes/StringFormatMask    TypeAttributes/AnsiClass]
	[:unicode-class        TypeAttributes/StringFormatMask    TypeAttributes/UnicodeClass]
	[:auto-class           TypeAttributes/StringFormatMask    TypeAttributes/AutoClass]
	[:before-field-init    TypeAttributes/BeforeFieldInit     TypeAttributes/BeforeFieldInit]
	[:rt-special-name      TypeAttributes/ReservedMask        TypeAttributes/RTSpecialName]
	[:has-security         TypeAttributes/ReservedMask        TypeAttributes/HasSecurity]
	])
  
(def method-flags
  [[:privatescope         MethodAttributes/MemberAccessMask   MethodAttributes/PrivateScope]
   [:private              MethodAttributes/MemberAccessMask   MethodAttributes/Private]
   [:famandassem          MethodAttributes/MemberAccessMask   MethodAttributes/FamANDAssem]
   [:assembly             MethodAttributes/MemberAccessMask   MethodAttributes/Assembly]
   [:family               MethodAttributes/MemberAccessMask   MethodAttributes/Family]
   [:famorassem           MethodAttributes/MemberAccessMask   MethodAttributes/FamORAssem]
   [:public               MethodAttributes/MemberAccessMask   MethodAttributes/Public]
   [:static               MethodAttributes/Static             MethodAttributes/Static]
   [:final                MethodAttributes/Final              MethodAttributes/Final]
   [:virtual              MethodAttributes/Virtual            MethodAttributes/Virtual]
   [:hide-by-sig          MethodAttributes/HideBySig          MethodAttributes/HideBySig]
   [:reuse-slot           MethodAttributes/VtableLayoutMask   MethodAttributes/ReuseSlot]
   [:new-slot             MethodAttributes/VtableLayoutMask   MethodAttributes/NewSlot]
   [:abstract             MethodAttributes/Abstract           MethodAttributes/Abstract]
   [:special-name         MethodAttributes/SpecialName        MethodAttributes/SpecialName]
   [:pinvoke-impl         MethodAttributes/PinvokeImpl        MethodAttributes/PinvokeImpl]
   [:unmanaged-export     MethodAttributes/UnmanagedExport    MethodAttributes/UnmanagedExport]
   [:rt-special-name      MethodAttributes/ReservedMask       MethodAttributes/RTSpecialName]
   [:has-security         MethodAttributes/ReservedMask       MethodAttributes/HasSecurity]
   [:require-sec-object   MethodAttributes/ReservedMask       MethodAttributes/RequireSecObject]
  ])

(def field-flags
  [[:privatescope        FieldAttributes/FieldAccessMask      FieldAttributes/PrivateScope]
   [:private             FieldAttributes/FieldAccessMask      FieldAttributes/Private]
   [:famandassem         FieldAttributes/FieldAccessMask      FieldAttributes/FamANDAssem]
   [:assembly            FieldAttributes/FieldAccessMask      FieldAttributes/Assembly]
   [:family              FieldAttributes/FieldAccessMask      FieldAttributes/Family]
   [:famorassem          FieldAttributes/FieldAccessMask      FieldAttributes/FamORAssem]
   [:public              FieldAttributes/FieldAccessMask      FieldAttributes/Public]
   [:static              FieldAttributes/Static               FieldAttributes/Static]
   [:init-only           FieldAttributes/InitOnly             FieldAttributes/InitOnly]
   [:literal             FieldAttributes/Literal              FieldAttributes/Literal]
   [:not-serialized      FieldAttributes/NotSerialized        FieldAttributes/NotSerialized]
   [:special-name        FieldAttributes/SpecialName          FieldAttributes/SpecialName]
   [:pinvoke-impl        FieldAttributes/PinvokeImpl          FieldAttributes/PinvokeImpl]
   [:rt-special-name     FieldAttributes/ReservedMask         FieldAttributes/RTSpecialName]
   [:has-field-marshal   FieldAttributes/ReservedMask         FieldAttributes/HasFieldMarshal]
   [:has-default         FieldAttributes/ReservedMask         FieldAttributes/HasDefault]
   [:has-field-rva       FieldAttributes/ReservedMask         FieldAttributes/HasFieldRVA]
 ])

 (def property-flags
   [[:special-name        PropertyAttributes/SpecialName      PropertyAttributes/SpecialName]
    [:rt-special-name     PropertyAttributes/ReservedMask     PropertyAttributes/RTSpecialName]
    [:has-default         PropertyAttributes/ReservedMask     PropertyAttributes/HasDefault]
   ])



(defn- parse-attributes 
  "Convert attributes into a set of keywords"
  [attributes flags]
  (reduce
    (fn [result fd]
	  (if  (== (enum-and attributes (nth fd 1)) (nth fd 2))
	    (conj result (nth fd 0))
		result))
	#{}
	flags))

(defn- parameter->info [^System.Reflection.ParameterInfo p]
   (let [ t (.ParameterType p) ]
     (if (.IsByRef t)
	   (list :by-ref (typesym t))
	   (typesym t))))

(defrecord Constructor
  [name declaring-class parameter-types flags])

(defn- constructor->map
  [^System.Reflection.ConstructorInfo constructor]
  (Constructor.
   (symbol (.Name constructor))
   (typesym (.DeclaringType constructor))
   (vec (map parameter->info (.GetParameters constructor)))
   (parse-attributes (.Attributes constructor) method-flags)))

(def ^:private basic-binding-flags
  (enum-or BindingFlags/Public BindingFlags/NonPublic BindingFlags/DeclaredOnly BindingFlags/Instance BindingFlags/Static))

(defn- declared-constructors
  "Return a set of the declared constructors of class as a Clojure map."
  [^Type cls]
  (set (map
        constructor->map
        (.GetConstructors cls basic-binding-flags)))) 

(defrecord Method
  [name return-type declaring-class parameter-types flags])

(defn- method->map
  [^System.Reflection.MethodInfo method]
  (Method.
   (symbol (.Name method))
   (typesym (.ReturnType method))
   (typesym (.DeclaringType method))
   (vec (map parameter->info (.GetParameters method)))
   (parse-attributes (.Attributes method) method-flags)))

(defn- declared-methods
  "Return a set of the declared constructors of class as a Clojure map."
  [^Type cls]
  (set (map
        method->map
        (.GetMethods cls basic-binding-flags))))

(defrecord Field
  [name type declaring-class flags])

(defn- field->map
  [^System.Reflection.FieldInfo field]
  (Field.
   (symbol (.Name field))
   (typesym (.FieldType field))
   (typesym (.DeclaringType field))
   (parse-attributes (.Attributes field) field-flags)))

(defn- declared-fields
  "Return a set of the declared fields of class as a Clojure map."
  [^Type cls]
  (set (map
        field->map
        (.GetFields cls basic-binding-flags))))

(defrecord Property
  [name type declaring-class flags])

(defn- property->map
  [^System.Reflection.PropertyInfo property]
  (Property.
   (symbol (.Name property))
   (typesym (.PropertyType property))
   (typesym (.DeclaringType property))
   (parse-attributes (.Attributes property) property-flags)))

(defn- declared-properties
  "Return a set of the declared fields of class as a Clojure map."
  [^Type cls]
  (set (map
        property->map
        (.GetProperties cls basic-binding-flags))))


(deftype ClrReflector [a]
  Reflector
  (do-reflect [_ typeref]
           (let [cls (clojure.lang.RT/classForName (typename typeref))]
             {:bases (not-empty (set (map typesym (bases cls))))
              :flags (parse-attributes (.Attributes cls) class-flags)
              :members (set/union (declared-fields cls)
			                      (declared-properties cls)
                                  (declared-methods cls)
                                  (declared-constructors cls))})))

(def ^:private default-reflector
     (ClrReflector. nil))
