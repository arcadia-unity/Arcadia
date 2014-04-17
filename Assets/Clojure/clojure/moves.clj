(ns clojure.unity)
(import '(UnityEngine Debug GUIText ParticleSystem))
(import 'System.Linq.Enumerable)

(defn setup [this]
  (Debug/Log "whatever")
  ;; (let [gos (seq (Enumerable/Where (GameObject/FindGameObjectsWithTag "sprite") (fn [go] (= "left" (.. go name)))))] (Debug/Log (count gos)))
  )

(defn update [this]
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