(ns boxes
  (:use unity.core)
  (:require [unity.hydrate :as h])
  (:import UnityEditor.Selection))


(map destroy-immediate (objects-named "Cube"))

(doseq [x (range 3)
        y (range 3)
        z (range 3)]

        (if (< 0.5 (rand))
          (h/populate!
          (create-primitive :cube)
          { :transform [{ :position [x y z] }]
            :rigidbody [{ :mass 45 }]})))