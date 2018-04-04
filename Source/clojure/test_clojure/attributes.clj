;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;;  Original authors: Stuart Halloway, Rich Hickey
;;  CLR version author: David Miller

(ns clojure.test-clojure.attributes
  (:use clojure.test)
  (:import [System.Security.Permissions FileIOPermissionAttribute FileDialogPermissionAttribute SecurityAction]))

(definterface Foo (foo []))

(deftype ^{
             ObsoleteAttribute "abc"
             FileDialogPermissionAttribute SecurityAction/Demand
			 FileIOPermissionAttribute #{ SecurityAction/Demand { :__args [SecurityAction/Deny] :Read "def" }}}
   Bar [^int a
        ^{ :tag int
		    NonSerializedAttribute {}
            ObsoleteAttribute "abc"}
		b]

Foo (^{     ObsoleteAttribute "abc"
             FileDialogPermissionAttribute SecurityAction/Demand
			 FileIOPermissionAttribute #{ SecurityAction/Demand { :__args [SecurityAction/Deny] :Read "def" }}}
     foo [this] 42))

(defn get-custom-attributes [x]
  (.GetCustomAttributes x false))

(defn attribute->map [attr]
	(cond 
	  (instance? NonSerializedAttribute attr) {:type NonSerializedAttribute}
	  (instance? ObsoleteAttribute attr) {:type ObsoleteAttribute :message (.Message attr)}
	  (instance? FileDialogPermissionAttribute attr) {:type FileDialogPermissionAttribute :action (.Action attr)}
	  (instance? FileIOPermissionAttribute attr) {:type FileIOPermissionAttribute :action (.Action attr) :read (.Read attr)}
	  :else {:type (class attr)}))

(def expected-attributes
 #{ {:type ObsoleteAttribute :message "abc"}
	{:type FileDialogPermissionAttribute :action SecurityAction/Demand}
	{:type FileIOPermissionAttribute :action SecurityAction/Demand :read nil}
	{:type FileIOPermissionAttribute :action SecurityAction/Deny :read "def"}})

(def expected-attributes+ser
 #{ {:type SerializableAttribute}
    {:type ObsoleteAttribute :message "abc"}
	{:type FileDialogPermissionAttribute :action SecurityAction/Demand}
	{:type FileIOPermissionAttribute :action SecurityAction/Demand :read nil}
	{:type FileIOPermissionAttribute :action SecurityAction/Deny :read "def"}})

(def expected-attributes-field
 #{ {:type NonSerializedAttribute}
    {:type ObsoleteAttribute :message "abc"}})

(deftest test-attributes-on-type
  (is (=

       expected-attributes+ser
       (into #{} (map attribute->map (get-custom-attributes Bar))))))

(deftest test-attributes-on-field
  (is (=
       expected-attributes-field
       (into #{} (map attribute->map (get-custom-attributes (.GetField Bar "b")))))))

(deftest test-attributes-on-method
  (is (=
       expected-attributes
       (into #{} (map attribute->map (get-custom-attributes (.GetMethod Bar "foo")))))))

(gen-class :name foo.Bar
           :extends clojure.lang.Box
           :constructors {^{ObsoleteAttribute "help"} [Object] [Object]}
           :init init
		   :class-attributes {
             ObsoleteAttribute "abc"
             FileDialogPermissionAttribute SecurityAction/Demand
			 FileIOPermissionAttribute #{ SecurityAction/Demand { :__args [SecurityAction/Deny] :Read "def" }}}
           :prefix "foo")

(defn foo-init [obj]
  [[obj] nil])

(assembly-load "foo.Bar")
(deftest test-attributes-on-constructor
  (is (some #(instance? ObsoleteAttribute %)
            (for [ctor (.GetConstructors (clojure.lang.RT/classForName "foo.Bar"))
                  attribute (get-custom-attributes ctor)]
              attribute))))

(deftest test-attributes-on-genclass-class
  (is (=
       expected-attributes
       (into #{} (map attribute->map (get-custom-attributes (clojure.lang.RT/classForName "foo.Bar")))))))

  