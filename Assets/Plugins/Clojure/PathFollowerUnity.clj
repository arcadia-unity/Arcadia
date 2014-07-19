(ns PathFollowerUnity
  (:import 
    (UnityEngine Transform Debug GUILayout)
    (PathFollowerUnity PathFollower)))

(defmacro with-unchecked-math [& xs]
  `(binding [*unchecked-math* true]
     ~@xs))

(comment 
  (defmacro get-component* [obj t]
    (with-meta `(.GetComponent ~obj (~'type-args ~t))
      {:tag t})))

(comment 
  (gen-interface
    :name PathFollowerUnity.IStatefulStuff
    :methods [[GetState [] |System.Object[]|]
              [SetState [|System.Object|]]]))

(gen-class
  :name PathFollowerUnity.PathFollower
  :methods [[Update [] void]]
  :main false
;;  :implements [IStatefulStuff] 
  :extends UnityEngine.MonoBehaviour
  :prefix "PathFollower-"
  :init init
  :state state)
  
(defn PathFollower-init []
  (with-unchecked-math
    [ []
      (into-array System.Object
        (vec (repeatedly 1000 #(Vector3. (rand-int 30) (rand-int 18) 0))))]))

(comment)
(defn PathFollower-Update [^UnityEngine.Component self]
  (with-unchecked-math
    (let [^objects ary (.state self)
          ^object current-pos (aget ary Time/time)
          ^object next-pos (aget ary (inc Time/time))
          ^Transform transform (. self GetComponent (type-args Transform))]

      (set! (.localPosition transform)
        (Vector3/Lerp
          current-pos
          next-pos
          (rem Time/time 1))))))
 
;; (import 'PathFollowerUnity.PathFollower)

(comment 
  (import 'PathFollowerUnity.PathFollower) ;; might work :-/

  (defn PathFollower-Update [^PathFollowerUnity.PathFollower self]
    (with-unchecked-math
      (let [^objects ary (.state self)
            current-pos (aget ary Time/time)
            next-pos (aget ary (inc Time/time))
            transform (get-component* self Transform)]
        (set! (.localPosition transform)
          (Vector3/Lerp
            current-pos
            next-pos
            (rem Time/time 1)))))))
