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
;   (compile 'clojure.testmvc)
;
; You should then be able to play games such as:

;  (def v (clojure.testmvc/fy)
;  (.index v)


(System.Reflection.Assembly/Load "System.Web.Mvc, Version=1.0.0.0, Culture=neutral, PublicKeyToken=31bf3856ad364e35, processorArchitecture=MSIL")
 
(ns clojure.testmvc
   (:gen-class
    :factory fy
    :extends System.Web.Mvc.Controller
    :methods [
            [index [] System.Web.Mvc.ActionResult]
    ]))
 
(defn -index []
   (.Content "Hello World, from Clojure Controller"))
