(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]
           [System.Collections Queue]))

(defonce ^Queue work-queue
  (Queue/Synchronized (Queue.)))

(defn add-callback [f]
  (.Enqueue work-queue f))

(defn safe-dequeue-all [^Queue queue]
  (locking queue
    (when (> (.Count queue) 0)
      (let [objs (.ToArray queue)]
        (.Clear queue)
        objs))))

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  (doseq [f (safe-dequeue-all work-queue)]
    (try
      (f)
      (catch Exception e
        (Debug/Log
          (str  "Exception encountered when running editor callback"))
        (Debug/Log e)))))
