;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.
;
;	Author: David Miller

; Test of gen-class facility.
;
; Place this file in the clojure subdirectory of your main directory.
; Compile the file C.cs via:  csc /t:library C.cs
; Place C.dll in your root directory.
; Start Clojure and do:
;   (System.Reflection.Assembly/LoadFrom "C.dll")
;   (compile 'clojure.testattribute)
;
; You should then be able to play games such as:

(ns clojure.testattribute)

(def x (dm.Pet/Dog))

(gen-interface 
  :name ^{System.SerializableAttribute {} dm.PetTypeAttribute x} test.I1
  :methods [ [m1 [] Object] ])
  
(definterface ^{System.SerializableAttribute {} dm.PetTypeAttribute x} I2 (m2 []))

; (seq (.GetCustomAttributes test.I1 true))
; (seq (.GetCustomAttributes I2 true))


(definterface ^{ dm.PetTypeAttribute x } I3 
  (^{ dm.PetTypeAttribute x } m1 [ x  y])
  (m2 [x ^{ dm.PetTypeAttribute x } y]))
  

(deftype ^{System.SerializableAttribute {}}  T1 [a ^{ dm.PetTypeAttribute x } b]
    I3
    (^{ dm.PetTypeAttribute x } m1 [_ p q]    p)
    (m2 [_ p ^{ dm.PetTypeAttribute x } q] q)
)