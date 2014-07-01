(ns AHappyNamespaceUnity
  (:import 
    (UnityEngine MonoBehaviour Debug GUILayout)))

(import '(UnityEngine Debug GUIText ParticleSystem))

(gen-class
  :name AHappyNamespaceUnity.Apathy
  :methods [
    [Update [] void]
    [Start [] void]]
  :main false
  :extends UnityEngine.MonoBehaviour
  :prefix "Apathy-")


(defn Apathy-Update [this]
  (Debug/Log "updating apathetically"))

(defn Apathy-Start [this]
  (Debug/Log "apathetically hatching"))



(gen-class
  :name ^{System.SerializableAttribute {} UnityEditor.CustomEditor AHappyNamespaceUnity.Apathy } AHappyNamespaceUnity.ApathyEditor
  :methods [
    [OnInspectorGUI [] void]]
  :main false
  :extends UnityEditor.Editor
  :prefix "ApathyEditor-")

(defn ApathyEditor-OnInspectorGUI [this]
  (EditorGUILayout/IntSlider 20 0 100))