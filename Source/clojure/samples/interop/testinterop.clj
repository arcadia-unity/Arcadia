;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.
;
;	Author: David Miller

; Test of new interop code
;
; Place this file in the clojure subdirectory of your main directory.
; Compile the file C.cs via:  csc /t:library C.cs
; Place C.dll in your root directory.
; Start Clojure and do:
;   (System.Reflection.Assembly/LoadFrom "C.dll")
;   (compile 'clojure.testinterop ')
;
; You should then be able to play games such as:
;



(ns clojure.testinterop)

; Create some instances
(def c1 (dm.interop.C1.))
(def c2 (dm.interop.C2.))
(def c3 (dm.interop.C3.))
(def c4 (dm.interop.C4.))


; Test the instance field/property/method()
(defn f1 [c]
  (.m1 c))
  
;  (f1 c1) => 11  ; accesses field
;  (f1 c2) => 21  ; accesses property
;  (f1 c3) => 31  ; acccesses zero-arity method
;  (f1 c4) => throws

; Test the static field/property/method()
(defn f1s []
  (+ (dm.interop.C1/m1s) (dm.interop.C2/m1s) (dm.interop.C3/m1s)))

 
; Test overload resolving
(defn f2none [c]
  (.m2 c))
  
; (f2none c1) =>  writes something appropriate.

; Really test overload resolving
(defn f2one [c x] 
  (.m2 c x))
  
; (f2one c1 (int 7))
; (f2one c1 7.1)
; (f2one c1 "whatever")
; (f2one c1 '(a b c))

(defn f2two [c x y]
  (.m2 c x y))
  
; (f2two c1 "Here it is: {0}" 12)
; (f2two c2 "Here it is: {0}" 12)


; Test by-ref, resolved at compile-time

(defn f3c [c n]
  (let [m (int n)
        v (.m3 ^dm.interop.C1 c (by-ref m))]
     [v m]))
     
; Test by-ref, resolved at runtime

(defn f3r [c n]
  (let [m (int n)
        v (.m3 c (by-ref m))]
     [v m]))     
  
; Make sure we find the non-by-ref overload
(defn f3n [c n]
  (let [m (int n)
        v (.m3 c m)]
	[v m]))
     
; (f3c c1 12) => [33 13]
; (f3r c1 12) => [33 13]
; (f3n c1 12) => [22 12]

; Testing some ambiguity with refs
(defn f5 [c x y]
  (let [m (int y)
        v (.m5 c x (by-ref m))]
    [v m]))
    
; (f5 c1 "help" 12) => ["help22" 22]
; (f5 c1 15 20) => [135 120]

; Try the following to test c-tor overloads
; (dm.interop.C5.)
; (dm.interop.C5. 7)
; (dm.interop.C5. "thing")
; (let [x (int 12)] (dm.interop.C5. (by-ref x)) x)
; 

; Test dynamic overload resolution
(defn make5 [x] (dm.interop.C5. x))


; Test overload resolution with ref param
(defn make5a [x y] 
   (let [n (int y)
         v (dm.interop.C5. x (by-ref n))]
     [v n]))
     
; (make5a "help" 12)  => [#<C5 Constructed with String+int-by-ref c-tor> 32]
; (make5a 20 30) =>  [#<C5 Constructed with int+int-by-ref c-tor> 60]

(defn f6 [c a]
  [(.m6 c (by-ref a)) a])
  
; (f6 c1 12)  => [ "123" 123 ]                  NOPE
; (f6 c1 "def") => [ "defabc" "defab" ]         NOPE

(defn c6sm1a [x ys]
  (dm.interop.C6/sm1 x ^objects (into-array Object ys)))
  
; (c6sm1a 12 [ 1 2 3] ) => 15

(defn c6sm1b [x ys]
  (dm.interop.C6/sm1 x ^"System.String[]" (into-array String ys)))

; (c6sm1b 12 ["abc" "de" "f"]) => 18

(def c6 (dm.interop.C6.))

(defn c6m1a [c x ys]
  (.m1 c x ^objects (into-array Object ys)))
  
; (c6m1a c6 12 [ 1 2 3] ) => 15

(defn c6m1b [c x ys]
  (.m1 c x ^"System.String[]" (into-array String ys)))

; (c6m1b c6 12 ["abc" "de" "f"]) => 18

(defn c6m2 [x] 
  (let [n (int x)
        v (dm.interop.C6/m2 (by-ref n) ^objects (into-array Object [1 2 3 4]))]
    [n v]))
    
;  (c6m2 12) => [16 4]
  