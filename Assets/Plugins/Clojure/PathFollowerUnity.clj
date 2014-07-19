(ns PathFollowerUnity
  (:import 
    (UnityEngine Transform Debug GUILayout)
    (PathFollowerUnity PathFollower)))

(defmacro with-unchecked-math [& xs]
  `(binding [*unchecked-math* true]
     ~@xs))

(defmacro get-component* [obj t]
  (with-meta `(.GetComponent ~obj (~'type-args ~t))
    {:tag t}))

(gen-class
  :name PathFollowerUnity.PathFollower
  :methods [[Update [] void]]
  :main false
  :extends UnityEngine.MonoBehaviour
  :prefix "PathFollower-"
  :init init
  :state state)

(defn PathFollower-init []
  (with-unchecked-math
    [ []
      (into-array System.Object
        (vec (repeatedly 1000 #(Vector3. (rand-int 30) (rand-int 18) 0))))]))

(defn PathFollower-Update [^UnityEngine.Component self]
  (with-unchecked-math
    (let [^objects ary (.state self)
          current-pos (aget ary Time/time)
          next-pos (aget ary (inc Time/time))
          transform (get-component* self Transform)]
      (set! (.localPosition transform)
        (Vector3/Lerp
          current-pos
          next-pos
          (rem Time/time 1))))))
