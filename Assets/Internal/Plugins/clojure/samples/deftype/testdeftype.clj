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
; load or compile, depending on your mood

(ns clojure.testdeftype)

(definterface I1 
  (^Int32 m1 [^Int32 x ^String y])
  (^Int32 m1 [^String x ^Int32 y])
  (^Int32 m2 [^Int32 x ^String y]))
  
(definterface I2
  (^Int32 m1 [^Int32 x ^String y])
  (^String m2 [^Int32 x ^String y])
  (m3 [x y])
  )

(deftype T1 [a b]
    I1
    (^Int32 m1 [_ ^Int32 x ^String y]    (unchecked-add x (.Length y)))
    (^Int32 m1 [_ ^String x ^Int32 y]    (unchecked-multiply (.Length x) y))
    (^Int32 m2 [this ^Int32 x ^String y] (unchecked-multiply (int 2) (.m1 this x y)))
)

(deftype T2 [a b]
    I1
    (^Int32 m1 [_ ^Int32 x ^String y]    (unchecked-add x (.Length y)))
    (^Int32 m1 [_ ^String x ^Int32 y]    (unchecked-multiply (.Length x) y))
    (^Int32 m2 [this ^Int32 x ^String y] (unchecked-multiply (int 2) (.m1 this x y)))
    I2
    (m3 [_ x y] (list a b x y))
    (^String user.I2.m2 [this ^Int32 x ^String y] (str y " " x))
)

(deftype T3 [a b]
    I1
    (^Int32 m1 [_ ^Int32 x ^String y]    (unchecked-add x (.Length y)))
    (^Int32 m1 [_ ^String x ^Int32 y]    (unchecked-multiply (.Length x) y))
    I2
    (m3 [_ x y] (list a b x y))
)





(deftype VecNode [edit arr])

(def EMPTY-NODE (VecNode nil (object-array 32)))

(definterface IVecImpl
  (^int tailoff [])
  (arrayFor [^int i])
  (pushTail [^int level parent tailnode])
  (popTail [^int level node])
  (newPath [edit ^int level node])
  (doAssoc [^int level node ^int i val]))

(definterface ArrayManager
  (array [^int size])
  (^int alength [arr])
  (aclone [arr])
  (aget [arr ^int i])
  (aset [arr ^int i val]))	