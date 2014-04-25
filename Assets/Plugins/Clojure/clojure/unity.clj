(ns clojure.unity
	(:gen-class
	 :extends UnityEngine.MonoBehaviour))

(defn -Update [this]
	(Debug/Log this))

(defn -Setup [this]
	(Debug/Log "I am born"))