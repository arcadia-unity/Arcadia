;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.
;
;
;	Author: David Miller
;
;   A simple test of the gen-class facility taking defaults for all.
;
;
; Place this file in the clojure subdirectory of your main directory.
;   (compile 'clojure.testgenclass2)
;
; You should then find clojure.testgenclass2.exe in your compile path. 
; Executing it should print "Hello, world".


(ns clojure.testgenclass2
   (:gen-class))
   

(defn -main []
  (println "Hello, world"))