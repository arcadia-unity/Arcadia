(ns arcadia.internal.player-callbacks
  (:require [arcadia.internal.callbacks :as cb]
            [arcadia.core :as ac])
  (:import [UnityEngine Debug]
           [System.Collections Queue]
           [Arcadia PlayerCallbacks Callbacks Callbacks+IntervalData Util]))

;; Main motivation of this namespace is for testing, but might have use
;; for exportable repls too.

;; ============================================================
;; singleton

(defonce active-callback-component
  (atom nil
    :validator (fn [x]
                 (or (nil? x)
                     (isa? (class x) Arcadia.PlayerCallbacks)))))

(defn active-callback-component? [x]
  (and (some? x) (= x @active-callback-component)))

;; think this is only sound on the main thread
(defn callback-component []
  (locking active-callback-component
    (if-let [^PlayerCallbacks acc @active-callback-component]
      (if (some-> acc Arcadia.Util/TrueNil .gameObject Arcadia.Util/TrueNil)
        acc
        ;; Old one has been destroyed. There's a possibility that it's
        ;; been just scheduled for destruction but not destroyed yet,
        ;; but there seems to be no way to tell in Unity, so this is
        ;; the best we can do. Means we should always try to destroy
        ;; the old one through our own interface.
        (let [obj (UnityEngine.GameObject. "unity-player-callbacks-object")
              cc (.AddComponent obj PlayerCallbacks)]
          (reset! active-callback-component cc)
          cc))
      (let [obj (UnityEngine.GameObject. "unity-player-callbacks-object")
            cc (.AddComponent obj PlayerCallbacks)]
        (reset! active-callback-component cc)
        cc))))

(defn destroy-callback-component []
  (locking active-callback-component
    (when-let [^PlayerCallbacks acc @active-callback-component]
      (do (when-let [obj (some-> acc Arcadia.Util/TrueNil .gameObject Arcadia.Util/TrueNil)]
            (ac/retire obj))
          (reset! active-callback-component nil)))))

;; ============================================================
;; Normal callbacks

(defonce ^Queue update-work-queue
  (Queue/Synchronized (Queue.)))

(defn add-update-callback [f]
  (callback-component)
  (.Enqueue update-work-queue f))

(defonce ^Queue fixed-update-work-queue
  (Queue/Synchronized (Queue.)))

(defn add-fixed-update-callback [f]
  (callback-component)
  (.Enqueue fixed-update-work-queue f))

;; ============================================================
;; Repeating callbacks

;; ------------------------------------------------------------
;; update

(defonce update-repeating-callbacks
  (atom (cb/build-repeating-callbacks {})))

(defn add-update-repeating-callback [k f interval]
  (callback-component)
  (cb/add-repeating-callback update-repeating-callbacks k f interval))

(defn remove-update-repeating-callback [k]
  (cb/remove-repeating-callback update-repeating-callbacks k))

(defn handle-update-repeating-callback-error [^System.Exception e, ^Callbacks+IntervalData ecd]
  (Debug/LogError e)
  (Debug/Log (str "Removing Update callback at key " (.key ecd)))
  (remove-update-repeating-callback (.key ecd)))

(defn run-update-repeating-callbacks []
  (Callbacks/RunIntervalCallbacks
    (:arr @update-repeating-callbacks),
    handle-update-repeating-callback-error))

;; ------------------------------------------------------------
;; fixed-update

(defonce fixed-update-repeating-callbacks
  (atom (cb/build-repeating-callbacks {})))

(defn add-fixed-update-repeating-callback [k f interval]
  (callback-component)
  (cb/add-repeating-callback fixed-update-repeating-callbacks k f interval))

(defn remove-fixed-update-repeating-callback [k]
  (cb/remove-repeating-callback fixed-update-repeating-callbacks k))

(defn handle-fixed-update-repeating-callback-error [^System.Exception e, ^Callbacks+IntervalData ecd]
  (Debug/LogError e)
  (Debug/Log (str "Removing FixedUpdate callback at key " (.key ecd)))
  (remove-fixed-update-repeating-callback (.key ecd)))

(defn run-fixed-update-repeating-callbacks []
  (Callbacks/RunIntervalCallbacks
    (:arr @fixed-update-repeating-callbacks),
    handle-fixed-update-repeating-callback-error))

;; ============================================================
;; timeouts

(defn add-update-frame-timeout [k f timeout]
  (callback-component)
  (let [frames (volatile! 0)]
    (add-update-repeating-callback
      k
      (fn update-frame-repeating-callback []
        (when (<= timeout (vswap! frames inc))
          (remove-update-repeating-callback k)
          (f)))
      0)))

(defn add-fixed-update-frame-timeout [k f timeout]
  (callback-component)
  (let [frames (volatile! 0)]
    (add-fixed-update-repeating-callback
      k
      (fn fixed-update-frame-repeating-callback []
        (when (<= timeout (vswap! frames inc))
          (remove-fixed-update-repeating-callback k)
          (f)))
      0)))

;; ============================================================
;; run all

(defn run-update-callbacks []
  (cb/run-callbacks update-work-queue run-update-repeating-callbacks))

(defn run-fixed-update-callbacks []
  (cb/run-callbacks fixed-update-work-queue run-fixed-update-repeating-callbacks))
