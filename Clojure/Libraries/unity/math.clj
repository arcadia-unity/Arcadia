(ns unity.math
  (:use unity.core)
  (:import UnityEngine.Mathf))

(defwrapper Mathf)

(def pi (Mathf/PI))
(def epsilon (Mathf/Epsilon))

(defn perlin-noise
  "Generate 2D Perlin noise."
  ([^double x] (Mathf/PerlinNoise (+ 0.1 x) 0))
  ([^double x ^double y] (Mathf/PerlinNoise (+ 0.1 x) y)))

(defn abs 
  "Returns the absolute value of f."
  [^double x]
  (Mathf/Abs x))

(defn clamp
  "Returns the absolute value of f."
  ([^double value]
   (Mathf/Clamp01 value))
  ([^double value ^double min ^double max]
  (Mathf/Clamp value min max)))
