(ns arcadia.components
  (:require [clojure.string :as string]
             [arcadia.messages :as messages]))

;; TODO path seperators
(def path "Assets/Arcadia/Components/")

(defn component-name [message]
  (str message "Hook"))

(defn component-file [message]
  (str path (component-name message) ".cs"))

(defn component-source [message args]
  (let [arg-names (repeatedly (count args) gensym)]
    (str
"using UnityEngine;
using clojure.lang;

public class " (component-name message) " : ArcadiaBehaviour
{
  void " message "(" (string/join ", " (map #(str %1 " " %2) args arg-names)) ")
  {
    if(!System.String.IsNullOrEmpty(ns))
    {
      Var v = (Var)RT.var(ns, var);
      if(v.isBound)
        ((IFn)v.getRawRoot()).invoke(" (string/join ", " (concat ['gameObject] arg-names)) ");
    }
  }
}")))

(defn write-component! [message args]
  (spit (component-file message)
        (component-source message args)
        :encoding "UTF-8"
        :write true))

(defn write-components! []
  (doseq [[message args] messages/messages]
    (write-component! message args)))