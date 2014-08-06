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
;   (compile 'clojure.testgenclass)
;
; You should then be able to play games such as:
;
;  (def v (test.my.gen/fy 12 "test" "thingy"))
;  (.m2 v 12)
;  (.State v)
;  (.getx v)
;  (.setx v 99)
;  (.getx v)
;
;  Also, you should be able to go to your root directory and execute test.my.gen.exe.
;  It will load everything and call the main routine listed below.

(gen-interface :name test.my.I2
   :methods [ [m10 [Int32 String] Int32] ])

(ns clojure.testgenclass
  (:gen-class
      :name test.my.gen
      :main true
      :implements [ dm.I1 test.my.I2 ]
      :extends dm.C1
      :factory fy
      :init init
      :post-init pinit
      :state State
      :constructors {[Int32 String Object][String Int32]}
      :methods [ [f [Object] Object] [g [Int32] Int32] ]
      :exposes { x { :get getx :set setx } }
      :exposes-methods { m4 do-m4 }
  )
)  
      
      
(defn -init [x y z] [[y x] z])

(defn -pinit [this x y z] nil)

(defn -f [x] x)
(defn -g [x] (inc x))

(defn -main [& args] 
   (let [ v (test.my.gen/fy 12 "test" "thingy")]
     (println (str (.State v)))))
     
 
      