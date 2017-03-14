(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]
           [System.Collections Queue]))

;; Queue of functions, each of which will be run and then removed from
;; the queue in order every time run-callbacks is called.
(defonce ^Queue work-queue
  (Queue/Synchronized (Queue.)))

;; Map from keys to functions. Each function will be run every time
;; run-callbacks is called. If a callback throws an exception it will
;; be removed from repeating-callbacks by dissoc-ing its key.
(defonce repeating-callbacks
  (atom {}))

(defn add-callback [f]
  (.Enqueue work-queue f))

(defn add-repeating-callback [k f]
  (swap! repeating-callbacks assoc k f))

(defn remove-repeating-callback [k]
  (swap! repeating-callbacks dissoc k))

(defn repeating-callback-runner [_ k f]
  (try
    (f)
    (catch Exception e
      (Debug/Log
        (str "Exception encountered when running editor callback at key " k ":"))
      (Debug/Log e)
      (Debug/Log (str "Removing editor callback at key " k))
      (remove-repeating-callback k)))
  nil)

(defn safe-dequeue-all [^Queue queue]
  (locking queue
    (when (> (.Count queue) 0)
      (let [objs (.ToArray queue)]
        (.Clear queue)
        objs))))

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  ;; empty the work-queue
  (doseq [f (safe-dequeue-all work-queue)]
    (try
      (f)
      (catch Exception e
        (Debug/Log
          "Exception encountered when running editor callback")
        (Debug/Log e))))
  ;; run all the repeating callbacks once
  (reduce-kv repeating-callback-runner nil @repeating-callbacks))
