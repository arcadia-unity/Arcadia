(ns arcadia.internal.asset-watcher
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs])
  (:import [System.IO FileSystemInfo]))

;; Contains the arcadia asset filewatcher. Separate namespace to
;; decouple from arcadia.internal.state (which just holds the
;; application state atom, ideally containing more or less pure data) and
;; arcadia.internal.filewatcher itself, which is a general-purpose
;; filewatcher and shouldn't contain things this specific to Arcadia.

;; indirection, because watch itself is stateful and ops on it
;; probably not idempotent, so swap! isn't a safe move here. We need
;; some state to hold it in anyway, since we might want to restart it
;; down the line, and as built that requires swapping it out for a new
;; watch.
(defonce ^:private asset-watcher-ref
  (atom
    (fw/start-watch
      (.FullName
        (fs/info "Assets"))
      500)))

(defn asset-watcher []
  @asset-watcher)
