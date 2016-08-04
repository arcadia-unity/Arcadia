(ns arcadia.internal.asset-watcher
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs])
  (:import [System.IO FileSystemInfo]))

;; Contains the arcadia asset filewatcher. Separate namespace to
;; decouple from arcadia.internal.state (which just holds the
;; application state atom, ideally containing more or less pure data) and
;; arcadia.internal.filewatcher itself, which is a general-purpose
;; filewatcher and shouldn't contain things this specific to Arcadia.

(def ^:private interval 500)

(defonce asset-watcher-ref
  (atom
    (fw/start-watch
      (.FullName
        (fs/info "Assets"))
      interval)))

(defn asset-watcher []
  @asset-watcher-ref)
