(ns arcadia.internal.asset-watcher
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs]
            [arcadia.internal.thread :as thr])
  (:import [System.IO FileSystemInfo]))

;; Contains the arcadia asset filewatcher. Separate namespace to
;; decouple from arcadia.internal.state (which just holds the
;; application state atom, ideally containing more or less pure data) and
;; arcadia.internal.filewatcher itself, which is a general-purpose
;; filewatcher and shouldn't contain things this specific to Arcadia.

(defonce dead-watch (atom false))

;; seems a little janky
(defn- watch-promise
  "Returns a promise after starting a thread which will deliver a new filewatcher to the promise."
  []
  (let [p (promise)]
    (if-not @dead-watch
      (thr/start-thread
        (fn []
          (deliver p
            (fw/start-watch
              (.FullName
                (fs/info "Assets"))
              500))))
      (deliver p nil))
    p))

(defonce ^:private asset-watcher-ref
  (atom
    (watch-promise)))

(defn asset-watcher []
  @@asset-watcher-ref)

(defn add-listener
  "Asynchronously add a listener to the asset watcher. Returns thread."
  [e k r f]
  (thr/start-thread
    (fn []
      (when-let [aw (asset-watcher)]
        ((::fw/add-listener aw) e k r f)))))

(defn remove-listener
  "Asynchronously remove a listener from the asset watcher. Returns thread."
  [k]
  (thr/start-thread
    (fn []
      (when-let [aw (asset-watcher)]
        ((::fw/remove-listener (asset-watcher)) k)))))
