(ns PureClojureUnity
  (:import 
    (UnityEngine MonoBehaviour Debug GUILayout)))

(import '(UnityEngine Debug GUIText ParticleSystem))

(gen-class
  :name PureClojureUnity.Behaviour
  :methods [
    [Update [] void]
    [Start [] void]
    ]
  :main false
  :extends UnityEngine.MonoBehaviour
  :prefix "Behaviour-")

(defn Behaviour-Update [this]
  ;; (.Translate (.. this gameObject transform) -1 0 0)
  (.Rotate (.. this gameObject transform) 0 0 1)
  (Debug/Log this))

(defn Behaviour-Start [this]
  (Debug/Log "I am born"))

(gen-class
  :name PureClojureUnity.Moves
  :prefix "Moves-"
  :main false
  :extends UnityEngine.MonoBehaviour
  :methods [
    [Update [] void]
    [Start [] void]])

(defn Moves-Start [this]
  (Debug/Log "whatever")
  ;; (let [gos (seq (Enumerable/Where (GameObject/FindGameObjectsWithTag "sprite") (fn [go] (= "left" (.. go name)))))] (Debug/Log (count gos)))
  )

(defn Moves-Update [this]
  (set!
    (.. this transform position)
    (new Vector3
      0
      (* (Mathf/Sin Time/time) 5)
      0))
  (.. this transform (Find "left") (Rotate 0 0 -1))
  (if (Input/GetKeyDown "space") 
    (do
      (set! (.. this (AddComponent GUIText) text) "Believe in your dreams")
      (.. this (AddComponent ParticleSystem) (Play)))))


(gen-class
  :name PureClojureUnity.DeclareNothing
  :prefix "DeclareNothing-"
  :main false
  :extends UnityEngine.MonoBehaviour)

(defn DeclareNothing-Start [this]
  (Debug/Log "DeclareNothing"))

(defn DeclareNothing-Update [this]
  (if (.shouldPrint this)
    (Debug/Log (str "DeclareNothing Update " (.name this)))))

(gen-class
  :name PureClojureUnity.DeclareSomething
  :prefix "DeclareSomething-"
  :main false
  :extends UnityEngine.MonoBehaviour
  :methods [
    [Start [] void]])

(defn DeclareSomething-Start [this]
  (Debug/Log "DeclareSomething"))

(gen-class
  :name PureClojureUnity.SubclassDummy
  :prefix "DeclareSomething-"
  :main false
  :extends UnityEngine.DummyClass)



(deftype ThisWasDefTyped
  [])