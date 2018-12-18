(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu]
            [arcadia.internal.callbacks :as cb])
  (:import [UnityEngine Debug]
           [System.Collections Queue]
           [Arcadia EditorCallbacks Callbacks Callbacks+IntervalData]))

;; ============================================================
;; normal callbacks

;; Queue of functions, each of which will be run and then removed from
;; the queue in order every time run-callbacks is called.
(defonce ^Queue work-queue
  (Queue/Synchronized (Queue.)))

(defn add-callback [f]
  (.Enqueue work-queue f))

;; ============================================================
;; repeating callbacks

(defonce repeating-callbacks
  (atom (cb/build-repeating-callbacks {})))

(defn add-repeating-callback [k f interval]
  (cb/add-repeating-callback repeating-callbacks k f interval))

(defn remove-repeating-callback [k]
  (cb/remove-repeating-callback repeating-callbacks k))

(defn handle-repeating-callback-error [^System.Exception e, ^Callbacks+IntervalData ecd]
  (Debug/LogError e)
  (Debug/Log (str "Removing callback at key " (.key ecd)))
  (remove-repeating-callback (.key ecd)))

(defn run-repeating-callbacks []
  (Callbacks/RunIntervalCallbacks
    (:arr @repeating-callbacks)
    handle-repeating-callback-error))

;; ============================================================
;; crude timeout
;; Extremely coarse-grained, only useful for scheduling stuff
;; roughly later

(defn add-timeout [k f timeout]
  (let [state (volatile! true)]
    (arcadia.internal.editor-callbacks/add-repeating-callback
      k
      (fn timeout-callback []
        (if @state
          (vreset! state false)
          (do (remove-repeating-callback k)
              (f))))
      timeout)))

(defn add-editor-frame-timeout [k f editor-frame-timeout]
  (assert (number? editor-frame-timeout))
  (let [frames (volatile! 0)]
    (arcadia.internal.editor-callbacks/add-repeating-callback
      k
      (fn editor-frame-timeout-callback []
        (when (<= editor-frame-timeout (vswap! frames inc))
          (remove-repeating-callback k)
          (f)))
      0)))

;; ============================================================
;; run all

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  (cb/run-callbacks work-queue run-repeating-callbacks))
