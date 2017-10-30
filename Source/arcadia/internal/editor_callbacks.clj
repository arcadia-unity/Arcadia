(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu]
           ;; [arcadia.internal.editor-callbacks.types :as ect]
            )
  (:import [UnityEngine Debug]
           [System.Collections Queue]
           [Arcadia EditorCallbacks EditorCallbacks+IntervalData]))

;; ============================================================
;; normal callbacks

;; Queue of functions, each of which will be run and then removed from
;; the queue in order every time run-callbacks is called.
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

;; ============================================================
;; repeating callbacks

(defn- build-repeating-callbacks [m]
  {::map m, ::arr (into-array (vals m))})

(defonce repeating-callbacks
  (atom (build-repeating-callbacks {})))

(defn add-repeating-callback [k f interval]
  (swap! repeating-callbacks
    (fn [rcs]
      (build-repeating-callbacks
        (assoc (::map rcs) k
          (EditorCallbacks+IntervalData. f interval k))))))

(defn remove-repeating-callback [k]
  (swap! repeating-callbacks
    (fn [rcs] (build-repeating-callbacks (dissoc (::map rcs) k)))))

(defn handle-repeating-callback-error [^System.Exception e, ^EditorCallbacks+IntervalData ecd]
  (Debug/LogError e)
  (Debug/Log "Removing callback at key " (.key ecd))
  (swap! repeating-callbacks remove-repeating-callback (.key ecd)))

(defn run-repeating-callbacks [rcs]
  (EditorCallbacks/RunIntervalCallbacks (::arr rcs), handle-repeating-callback-error))

;; ============================================================
;; run all

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  (let [ar (safe-dequeue-all work-queue)]
    (loop [i (int 0)]
      (when (< i (count ar))
        (do ((aget ar i))
            (recur (inc i))))))
  (run-repeating-callbacks @repeating-callbacks))
