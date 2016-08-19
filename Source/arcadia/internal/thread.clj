(ns arcadia.internal.thread
  (:import [System.Threading Thread ThreadStart]))

(defn start-thread [f]
  (let [t (Thread.
            (gen-delegate ThreadStart []
              (f)))]
    (.Start t)
    t))
