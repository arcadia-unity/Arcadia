;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;   Author: David Miller

(in-ns 'clojure.core)

;;;;;; Extensions to core for the CLR platform  ;;;;;;;


 
 (defmacro gen-delegate 
    [type argVec & body] `(clojure.lang.GenDelegate/Create ~type (fn ~argVec ~@body)))
    
;;; Additional numeric casts
;;; Somewhat useless until our arithmetic package is extended to support all these types.

(defn uint
  "Coerce to uint"
  {:inline (fn  [x] `(. clojure.lang.RT (~(if *unchecked-math* 'uncheckedUIntCast 'uintCast) ~x)))
   :added "1.0"}
  [x] (. clojure.lang.RT (uintCast x)))
  
(defn ushort
  "Coerce to ushort"
  {:inline (fn  [x] `(. clojure.lang.RT (~(if *unchecked-math* 'uncheckedUShortCast 'ushortCast) ~x)))
   :added "1.0"}
  [x] (. clojure.lang.RT (ushortCast x)))
  
(defn ulong
  "Coerce to ulong"
  {:inline (fn  [x] `(. clojure.lang.RT (~(if *unchecked-math* 'uncheckedULongCast 'ulongCast) ~x)))
   :added "1.0"}
  [x] (. clojure.lang.RT (ulongCast x)))
  
(defn decimal
  "Coerce to decimal"
  {:inline (fn  [x] `(. clojure.lang.RT (~(if *unchecked-math* 'uncheckedDecimalCast 'decimalCast) ~x)))
   :added "1.0"}
  [x] (. clojure.lang.RT (decimalCast x)))
  
(defn sbyte
  "Coerce to sbyte"
  {:inline (fn  [x] `(. clojure.lang.RT (~(if *unchecked-math* 'uncheckedSByteCast 'sbyteCast) ~x)))
   :added "1.0"}
  [x] (. clojure.lang.RT (sbyteCast x)))

;;; Additional aset-XXX variants

(def-aset
  ^{:doc "Sets the value at the index/indices. Works on arrays of uint. Returns val."
    :added "1.0"}
  aset-uint setUInt uint)
  
(def-aset
  ^{:doc "Sets the value at the index/indices. Works on arrays of ushort. Returns val."
    :added "1.0"}
  aset-ushort setUShort ushort)
  
(def-aset
  ^{:doc "Sets the value at the index/indices. Works on arrays of ulong. Returns val."
    :added "1.0"}
  aset-ulong setULong ulong)
  
(def-aset
  ^{:doc "Sets the value at the index/indices. Works on arrays of decimal. Returns val."
    :added "1.0"}
  aset-decimal setDecimal decimal)
  
(def-aset
  ^{:doc "Sets the value at the index/indices. Works on arrays of sbyte. Returns val."
    :added "1.0"}
  aset-sbyte setSByte sbyte)

;; Addtional array types

(defn uint-array
  "Creates an array of uints"
  {:inline (fn [& args] `(. clojure.lang.Numbers uint_array ~@args))
   :inline-arities #{1 2}
   :added "1.5"}
  ([size-or-seq] (. clojure.lang.Numbers uint_array size-or-seq))
  ([size init-val-or-seq] (. clojure.lang.Numbers uint_array size init-val-or-seq)))

(defn ushort-array
  "Creates an array of ushorts"
  {:inline (fn [& args] `(. clojure.lang.Numbers ushort_array ~@args))
   :inline-arities #{1 2}
   :added "1.5"}
  ([size-or-seq] (. clojure.lang.Numbers ushort_array size-or-seq))
  ([size init-val-or-seq] (. clojure.lang.Numbers ushort_array size init-val-or-seq)))

(defn ulong-array
  "Creates an array of ulongs"
  {:inline (fn [& args] `(. clojure.lang.Numbers ulong_array ~@args))
   :inline-arities #{1 2}
   :added "1.5"}
  ([size-or-seq] (. clojure.lang.Numbers ulong_array size-or-seq))
  ([size init-val-or-seq] (. clojure.lang.Numbers ulong_array size init-val-or-seq)))

(defn sbyte-array
  "Creates an array of sbytes"
  {:inline (fn [& args] `(. clojure.lang.Numbers sbyte_array ~@args))
   :inline-arities #{1 2}
   :added "1.5"}
  ([size-or-seq] (. clojure.lang.Numbers sbyte_array size-or-seq))
  ([size init-val-or-seq] (. clojure.lang.Numbers sbyte_array size init-val-or-seq)))


; Support for enums
  
(defn enum-val [t n]
  "Gets a value from an enum from the name"
  {:added "1.0"}
  (let [s (if (string? n) n (name n))]
   (Enum/Parse t s)))

(defn enum-or 
  "Combine via or several enum (flag values).  Coerced to type of first value."
  {:added "1.3"}
  [flag & flags]    
  (Enum/ToObject (class flag) (reduce1 #(bit-or (long %1) (long %2)) flag flags)))

(defn enum-and 
  "Combine via and several enum (flag values).  Coerced to type of first value."
  {:added "1.3"}
  [flag & flags]    
  (Enum/ToObject (class flag) (reduce1 #(bit-and (long %1) (long %2)) flag flags)))

; Support for interop

(defn by-ref
  "Signals that a by-ref parameter is desired at this position in an interop call or method signature.
  
  Should only be used in CLR interop code.  Throws an exception otherwise."
  {:added "1.2"}
   [v] (throw (ArgumentException. "by-ref not used at top-level in an interop call or method signature")))
  
(defn type-args
  "Supplies type arguments to a generic method interop call

  Should only be used in CLR interop code.  Throws an exception otherwise."
  {:added "1.3"}
  [v] (throw (ArgumentException. "type-args not used in interop call")))

(defn- str-join    ;; clojure.string not yet loaded
  [coll]
  (loop [sb (StringBuilder. (str (first coll)))
	     more (next coll)]
	(if more
		(recur (-> sb (.Append ",") (.Append (str (first more))))
			   (next more))
		(str sb))))

(defn- generate-generic-delegate 
  [typename typesyms body]
  (let [types (map (fn [tsym] (clojure.lang.CljCompiler.Ast.HostExpr/MaybeType tsym false)) typesyms)
  		ftype (symbol (str typename "`" (count types) "[" (str-join types) "]"))]
	  `(gen-delegate ~ftype ~@body)))

(defmacro sys-func
  "Translates to a gen-delegate for a System.Func<,...> call"
  {:added "1.3"}
  [typesyms & body]
  (generate-generic-delegate "System.Func" typesyms body))

(defmacro sys-action
  "Translates to a gen-delegate for a System.Action<,...> call"
  {:added "1.3"}
  [typesyms & body]
  (generate-generic-delegate "System.Action" typesyms body))  


; Attribute handling

(defn enum? [v]
  (instance? Enum v))
  
(defn array? [v]
  (instance? Array v))

(defn- is-attribute? [c]
  (and (class? c)
       (.IsAssignableFrom System.Attribute c)))

(defn- attribute-filter [[k v]]
  (when (symbol? k)
    (when-let [c (resolve k)]
      (is-attribute? c))))
      
      
; Note: we are not handling the non-CLS-compliant case of a one-dimensional array of arg values -- yet.
;
;  Most often attributes will be attached to classes, methods, etc. via metadata.
;  The key will be an class derived from System.Attribute.
;  The value will be arguments to the constructor and/or property setters.
;  We wish to simplify the syntax for the most common (simplest) cases.
;  We have to accommodate:
;    positional arguments to pass to constructors
;    property/value pairs
;    multiple values for an attribute
; The _normalized form_ for an attribute argument is:
;
;   #{ init1 init2 ... }
;
;  where an <init> is a hash with keys representing property names (and case is important).
;  The special key :__args will have as a value a vector of arguments that are passed to the constructor for the attribute class.
;
;  The surface synax (the value for the metadata allows the following simplifications:
;  
;  A set implies multiple values.  Each element of the set will be processed to create a standardarized init.
;  A vector implies just c-tor args. 
;  A map will be passed through
;  Any other value implies a single argument to a constructor.
;
;  System.Serializable {}    =>   System.Serializable #{ {} }   =>  call no-arg c-tor 
;
;  Assuming we have imported FileIOPermission and SecurityAction from System.Security.Permissions:
;  
;  FileIOPermission SecurityAction/Demand     =>  FileIOPermission #{ {:__args [SecurityAction/Demand]} }  =>  new FileIOPermission(SecurityAction/Demand)
;
;  FileIOPermission #{ SecurityAction/Demand SecurityAction/Deny }
;              ==> FileIOPermission #{  {:__args [SecurityAction/Demand]} {:__args [SecurityAction/Deny]} 
;              ==> new FileIOPermission(SecurityAction/Demand) + new FileIOPermission(SecurityAction/Demand)  (multiple values for this attribute)
;
; FileIOPermission #{ SecurityAction/Demand { :__args [SecurityAction/Deny] :Read "abc" } }
;             ==> FileIOPermission #{  {:__args [SecurityAction/Demand]} {:__args [SecurityAction/Deny] :Read "abc"} 
;              ==> new FileIOPermission(SecurityAction/Demand) 
;                  let x = new FileIOPermission(SecurityAction/Demand) + x.Read = "abc"
;			     (multiple values for this attribute, second has ctor call + property set)
;
;  Note that symbols are eval.  They must evaluate to either values of enums or to types.


(defn- normalize-attribute-arg-value [v]
  (cond
	(symbol? v) (let [ev (eval v)]
		          (cond
				     (enum? ev) ev
	                 (class? ev) ev
	                  :else  (throw (ArgumentException. (str "Unsupported attribute argument value: " v " of class " (class ev))))))
	(vector? v) (into1 [] (map normalize-attribute-arg-value v))
	(map? v) (into1 {} (map (fn [[k v]] [k (normalize-attribute-arg-value v)]) v))
	:else v))

(defn- normalize-attribute-init [init]
  (cond
	(vector? init) { :__args (map normalize-attribute-arg-value init) }
	(map? init)    (into1 {} (map (fn [[k v]] [k (normalize-attribute-arg-value v)]) init))
	:else          { :__args [ (normalize-attribute-arg-value init) ] } ))
     
(defn- normalize-attribute-arg [arg]
  (if (set? arg)    
	(into1 #{} (map normalize-attribute-init arg))
	#{ (normalize-attribute-init arg) }))
  
(defn- resolve-attribute [v]
  (cond
    (is-attribute? v) v
    (symbol? v) (when-let [c (resolve v)]
                   (when (is-attribute? c)
                     c))
    :else nil))
         

(defn- extract-attributes [m]
   (into1 {} 
     (remove nil? 
       (for [[k v] (seq m)]
         (when-let [c (resolve-attribute k)]
           [ c (normalize-attribute-arg v) ])))))



;; assembly loading helpers

(defn assembly-load 
  "Load an assembly given its name"
  {:added "1.3"}
  [^String assembly-name]
  (System.Reflection.Assembly/Load assembly-name))

(defn assembly-load-from
  "Load an assembly given its path"
  {:added "1.3"}
  [^String assembly-name]
  (System.Reflection.Assembly/LoadFrom assembly-name))

(defn assembly-load-file
  "Load an assembly given its name"
  {:added "1.3"}
  [^String assembly-name]
  (System.Reflection.Assembly/LoadFile assembly-name))

(defn assembly-load-with-partial-name
  "Load an assembly given a partial name"
  {:added "1.4"}
  [^String assembly-name]
  (System.Reflection.Assembly/LoadWithPartialName assembly-name))

(defn add-ns-load-mapping
  "Convenience function to assist with loading .clj files embedded in
  C# projects.  ns-root specifies part of a namespace such as MyNamespace.A and
  fs-root specifies the filesystem location in which to look for files within that
  namespace.  For example, if MyNamespace.A mapped to MyNsA would allow
  MyNamespace.A.B to be loaded from MyNsA\\B.clj.  When a .clj file is marked as an
  embedded resource in a C# project, it will be stored in the resulting .dll with
  the default project namespace prefixed to its path.  To allow these files to
  be loaded dynamically during development, the paths to these files can be mapped
  to allow them to be loaded from a different directory other than their root namespace
  (i.e. the common case where the project directory is different from its default
  namespace)."
  {:added "1.5"}
  [^String ns-root ^String fs-root]
  (swap! *ns-load-mappings* conj
	[(.Replace ns-root "." "/") fs-root]))
