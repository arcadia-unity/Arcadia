(ns arcadia.internal.callbacks
  (:require [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]
           [System.Collections Queue]
           [Arcadia Callbacks Callbacks+IntervalData]))

;; used by arcadia.internal.editor-callbacks and
;; arcadia.internal.player-callbacks

;; ============================================================
;; normal callbacks

(defn safe-dequeue-all [^Queue queue]
  (locking queue
    (when (> (.Count queue) 0)
      (let [objs (.ToArray queue)]
        (.Clear queue)
        objs))))

;; ============================================================
;; repeating callbacks

(defn build-repeating-callbacks [m]
  {:map m, :arr (into-array (vals m))})

(defn add-repeating-callback [repeating-callbacks-atom k f interval]
  (swap! repeating-callbacks-atom
    (fn [rcs]
      (build-repeating-callbacks
        (assoc (:map rcs) k
          (Callbacks+IntervalData. f interval k))))))

(defn remove-repeating-callback [repeating-callbacks-atom k]
  (swap! repeating-callbacks-atom
    (fn [rcs] (build-repeating-callbacks (dissoc (:map rcs) k)))))

(defn remove-repeating-callback [repeating-callbacks-atom k]
  (swap! repeating-callbacks-atom
    (fn [rcs] (build-repeating-callbacks (dissoc (:map rcs) k)))))

;; ============================================================
;; run all

(defn run-callbacks [work-queue run-repeating-callbacks-fn]
  (let [ar (safe-dequeue-all work-queue)]
    (loop [i (int 0)]
      (when (< i (count ar))
        (do ((aget ar i))
            (recur (inc i))))))
  (run-repeating-callbacks-fn))
