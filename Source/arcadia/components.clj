(ns arcadia.components
  (:refer-clojure :exclude [merge])
  (:import [System.IO File]
           [UnityEngine GameObject]
           ArcadiaBehaviour)
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
    if(fn != null)
      fn.invoke(" (string/join ", " (concat ['gameObject] arg-names)) ");
  }
}")))

(defn write-component! [message args]
  (File/Delete (component-file message))
  (spit (component-file message)
        (component-source message args)
        :encoding "UTF-8"))

(defn write-components! []
  (doseq [[message args] messages/messages]
    (write-component! message args)))

; (write-components!)

(defn- camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(def hooks
  (let [ks (keys messages/messages)
        types (->> ks
                   (map #(str % "Hook"))
                   (map #(clojure.lang.RT/classForName %)))
        keywords (map (comp keyword
                            string/lower-case
                            camels-to-hyphens
                            str)
                      ks)]
    (apply hash-map (interleave keywords types))))


(defn normalize [m]
  (reduce-kv (fn [m k v]
               (if (vector? v)
                 m
                 (assoc m k [v])))
             m m))

(defn merge [a b]
  (merge-with (comp vec concat)
              (normalize a)
              (normalize b)))

(defn add
  ([go component]
   (case (type component)
     UnityEngine.MonoBehaviour
     ))
  ([^GameObject go kw f]
   (let [^ArcadiaBehaviour new-hook (.AddComponent go ^System.MonoType (hooks kw))]
     (set! (.fn new-hook) f))))

(set! Selection/activeObject (GameObject. "foo"))

(add Selection/activeObject :update #'add)




