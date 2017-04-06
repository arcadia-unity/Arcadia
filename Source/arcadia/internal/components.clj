(ns
  ^{:doc "Generates the C# implementation of the Arcadia hooks"}
  arcadia.internal.components
  (:require [clojure.string :as string]
            [arcadia.internal.messages :as messages])
  (:import [clojure.lang RT]))

;; TODO path seperators
(def path "Assets/Arcadia/Components/")

(defn component-name [message]
  (str message "Hook"))

(defn component-file [message]
  (str path (component-name message) ".cs"))

(def alphabet
  (->> (range \a \z)
       (map (comp str char))))

(def overrides
  "Which methods of ArcadiaBehaviour should be overriden/hidden."
  '{Awake override})

(def call-base
  "Which overriden methods should call the base method."
  '{Awake true})

(defn component-source
  ([message args] (component-source message args nil))
  ([message args interface]
  (let [arg-names (take (count args) alphabet)]
    (str
"using UnityEngine;
" (if interface
    (str "using " (.Namespace (RT/classForName interface)) ";\n"))
"using clojure.lang;

public class " (component-name message) " : ArcadiaBehaviour" (if interface (str ", " interface))
"   
{
  " (string/join " " (filter some? ['public (overrides message) 'void])) " "
      message "(" (string/join ", " (map #(str %1 " " %2) args arg-names)) ")
  {
" (if (call-base message)
    (str "      base." message "(" (string/join ", " arg-names) ");\n"))
"      var _go = gameObject;
      foreach (var fn in fns)
        fn.invoke(" (string/join ", " (concat ['_go] arg-names)) ");
  }
}"))))

(defn write-component!
  ([message args] (write-component! message args nil))
  ([message args interface]
   (spit (component-file message)
         (component-source message args interface)
         :encoding "UTF-8"
         :write true)))

(defn write-components! []
  (doseq [[message args] messages/messages]
    (write-component! message args))
  
  (doseq [[message args] messages/interface-messages]
    (let [interface (namespace message)
          message (name message)]
      (write-component! message args interface))))