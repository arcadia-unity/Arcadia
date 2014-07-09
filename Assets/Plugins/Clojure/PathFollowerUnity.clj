(ns PathFollowerUnity
  (:import 
    (UnityEngine Transform Debug GUILayout)
    (PathFollowerUnity PathFollower)))

(gen-class
  :name PathFollowerUnity.PathFollower
  :methods [
    [Update [] void]]
  :main false
  :extends UnityEngine.MonoBehaviour
  :prefix "PathFollower-"
  :init init
  :state state)

(defn PathFollower-init [] [[] (into-array System.Object (vec (repeatedly 1000 #(Vector3. (rand-int 30) (rand-int 18) 0))))])

(defn PathFollower-Update [^UnityEngine.Component self]
  (let [^UnityEngine.Component this self
        t (long (Time/get_time))
        tf (float (Time/get_time))
        ^objects ary (.state this)
        ^object current-pos (aget ary t)
        ^object next-pos (aget ary (+ 1 t))
        ^UnityEngine.Transform transform (.GetComponent ^UnityEngine.Component this ^Type Transform)]

    (.set_localPosition transform
      (Vector3/Lerp
        current-pos
        next-pos
        (- tf t)))))