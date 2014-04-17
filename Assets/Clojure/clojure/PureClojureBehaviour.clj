(ns PureClojureBehaviour
	(:import (UnityEngine Debug MonoBehaviour)))
(gen-class
	:main false
	:extends UnityEngine.MonoBehaviour
	:name PureClojureBehaviour
	:methods [
		[Update [] void]
		[Start [] void]
		]
	:exposes-methods {Update Start})

(defn -Update [this]
	(Debug/Log "whatever"))

(defn -Start [this]
	(Debug/Log "I am born"))

(defn method-Foo []
	(Debug/Log "I am foo"))