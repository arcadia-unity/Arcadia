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
;   Simple example testing arguments and returns of value type.
;
; Place this file in the clojure subdirectory of your main directory.
;   (compile 'clojure.testgenclass3)
;
; Tests:
;
;   (def v (my.TGC3/fy))
;   (.add v 12)
;   (.add v 13)
;   (.val v)
;   (.zero v)
;   (.val v)


(ns clojure.testgenclass3
   (:gen-class
	   :state state
	   :init  init
	   :main false
	   :name my.TGC3
	   :factory fy
	   :methods [
			[zero [] Int32]
			[add [Int32] Int32]
			[val [] Int32] ]))
	   

(defn -init []
  [[] (ref 0)])
  
(defn -zero [this]
  (dosync
    (let [state (.state this)
          old  @state]
      (ref-set state 0)
      old)))
    
 (defn -add [this val]
   (print (str this))
   (print (str val))
   (dosync
      (commute (.state this) + val)))
      
 (defn -val [this]
    @(.state this))
    
                 
  
  