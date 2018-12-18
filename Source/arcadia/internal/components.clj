(ns
  ^{:doc "Generates the C# implementation of the Arcadia hooks"}
  arcadia.internal.components
  (:require [clojure.string :as string]
            [arcadia.internal.events :as events])
  (:import [clojure.lang RT]))

;; TODO path seperators
(def path "Assets/Arcadia/Components/")

(defn component-name [event]
  (str event "Hook"))

(defn component-file [event]
  (str path (component-name event) ".cs"))

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
  ([event args] (component-source event args nil))
  ([event args interface]
  (let [arg-names (take (count args) alphabet)]
    (str
"#if NET_4_6
using UnityEngine;
" (if interface
    (str "using " (.Namespace (RT/classForName interface)) ";\n"))
"using clojure.lang;

public class " (component-name event) " : ArcadiaBehaviour" (if interface (str ", " interface))
"
{
  " (string/join " " (filter some? ['public (overrides event) 'void])) " "
      event "(" (string/join ", " (map #(str %1 " " %2) args arg-names)) ")
  {
" (if (call-base event)
    (str "      base." event "(" (string/join ", " arg-names) ");\n"))
"      RunFunctions(" (string/join ", " arg-names) ");
  }
}
#endif"))))

(defn write-component!
  ([event args] (write-component! event args nil))
  ([event args interface]
   (spit (component-file event)
         (component-source event args interface)
         :encoding "UTF-8"
         :write true)))

(defn write-components! []
  (doseq [[event args] events/events]
    (write-component! event args))

  (doseq [[event args] events/interface-events]
    (let [interface (namespace event)
          event (name event)]
      (write-component! event args interface))))
