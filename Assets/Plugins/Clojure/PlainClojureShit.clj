(ns PlainClojureShit
  (:import 
    (UnityEngine Debug)
    (UnityEditor EditorGUILayout)))

(def happy (atom true)) ;; Multiple instances of this component will SHARE this atom! They should each have their OWN atom!

(defn start [this]
  (Debug/Log "PlainClojureShit is starting"))

(defn update [this]
 (Debug/Log (str "happy is " @happy)))

(defn on-mouse-down [this]
  (reset! happy (not @happy)))

(defn on-inspector-gui [this]
  (Debug/Log (str "on-inspector-gui " @happy))
  (let [gui-happy (EditorGUILayout/Toggle "Happy?" @happy nil)]
    (if (not= gui-happy happy)
      (do
        (reset! happy gui-happy)
        true)
      false)))

