(ns PureClojureBehaviour
	(:import 
		(UnityEngine MonoBehaviour Debug GUILayout)
		(UnityEditor Editor EditorGUILayout)))
(gen-class
	:name PureClojureBehaviour.Behaviour
	:prefix "PureClojureBehaviour-"
	:main false
	:extends UnityEngine.MonoBehaviour
	:methods [
		[Update [] void]
		[Start [] void]
		])

(defn PureClojureBehaviour-Update [this]
	(Debug/Log "whatever"))

(defn PureClojureBehaviour-Start [this]
	(Debug/Log "I am born"))

(gen-class
	:name ^{UnityEditor.CustomEditor PureClojureBehaviour.Behaviour} PureClojureBehaviour.Editor
	:prefix "PureClojureEditor-"
	:main false
	:extends UnityEditor.Editor
	:methods [
		[OnInspectorGUI [] void]
		])

(defn PureClojureEditor-OnInspectorGUI [this]
	(GUILayout/Button("Nice")))

