(ns unity.interop
  (:import [UnityEngine GameObject]))

;; namespace for essential unity interop conveniences.

(defn some-2
  "Uses reduced, should be faster + less garbage + more general than clojure.core/some"
  [pred coll]
  (reduce #(when (pred %2) (reduced %2)) nil coll))

(defn in? [x coll]
  (boolean (some-2 #(= x %) coll)))

(defn type-name? [x]
  (boolean
    (and (symbol? x)
      (when-let [y (resolve x)]
        (instance? System.MonoType y)))))

(defmacro get-component* [obj t]
  (cond
    (in? t (keys &env))
    `(.GetComponent ~obj ~t)

    (type-name? t)
    `(.GetComponent ~obj (~'type-args ~t))

    :else
    `(.GetComponent ~obj ~t)))

(defn get-component
  {:inline (fn [obj t]
             (list 'get-component* obj t))
   :inline-arities #{2}}
  [obj, t]
  (case (type obj) ;; presumably less garbage than naive reflection
    UnityEngine.GameObject (.GetComponent ^UnityEngine.GameObject obj t)
    UnityEngine.Component (.GetComponent ^UnityEngine.Component obj t)))
