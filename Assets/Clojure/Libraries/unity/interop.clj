(ns unity.interop
  (:import [UnityEngine GameObject]))

;; namespace for essential unity interop conveniences.

;; not sure the following is essential :\
;; (defmacro with-unchecked-math [& xs]
;;   `(binding [*unchecked-math* true]
;;      ~@xs))

(defn get-component
  {:inline (fn [obj t]
             (with-meta `(.GetComponent ~obj (~'type-args ~t))
               {:tag t}))
   :inline-arities #{2}}
  [^GameObject obj, ^Type t]
  (.GetComponent obj t))
