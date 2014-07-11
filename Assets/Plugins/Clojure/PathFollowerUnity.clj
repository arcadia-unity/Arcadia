(ns PathFollowerUnity
  (:import 
    (UnityEngine Transform Debug GUILayout)
    (PathFollowerUnity PathFollower)))

(set! *unchecked-math* true)

(defmacro get-component-m [x ct]
  `(let [x# ~x
         ct# ~ct]
         (tag-it (.GetComponent ) ct#)))

(defmacro tag-it [x t]
  (with-meta x {:tag t}))

(defn tag-it-fn [sym tag]
  (with-meta sym (merge (meta sym) {:tag tag})))

;; this doesn't work but we can figure out something that does
;; need to do the symbolic math to determine what we're saying
;; when, then it'll work. totally tractable. 
(defmacro get-component [obj com]
  (let [obj-sym (tag-it-fn (gensym "obj") com)
        com-sym (tag-it-fn (gensym "com") com)]
    `(let [~obj-sym ~obj
           ~com-sym ~com]
      (. (with-meta ~obj-sym {:tag ~com-sym}) GetComponent (type-args ~com)))))

(gen-class
  :name PathFollowerUnity.PathFollower
  :methods [
    [Update [] void]]
  :main false
  :extends UnityEngine.MonoBehaviour
  :prefix "PathFollower-"
  :init init
  :state state)

(defn PathFollower-init []
  [ []
    (into-array System.Object
      (vec (repeatedly 1000 #(Vector3. (rand-int 30) (rand-int 18) 0))))])

(defn PathFollower-Update [^UnityEngine.Component self]
  (let [^objects ary (.state self)
        ^object current-pos (aget ary Time/time)
        ^object next-pos (aget ary (inc Time/time))
        ^Transform transform (. self GetComponent (type-args Transform))]

    (set! (.localPosition transform)
      (Vector3/Lerp
        current-pos
        next-pos
        (rem Time/time 1)))))