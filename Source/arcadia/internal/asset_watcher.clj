(ns arcadia.internal.asset-watcher
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs]
            [arcadia.internal.thread :as thr]
            [arcadia.internal.map-utils :as mu])
  (:import [System.IO FileSystemInfo]
           [Arcadia UnityStatusHelper]))

;; move the parts of this that aren't actually the filewatcher to
;; arcadia.internal.state if that seems feasible once things are
;; working
(defonce asset-watcher-state
  (atom {}))

(defn watch-cancelled? []
  ((::fw/cancelled? (::watch @asset-watcher-state))))

(defn watch-errors []
  ((::fw/errors (::watch @asset-watcher-state))))

(defn update-watch []
  (thr/start-thread
    (fn []
      (let [{:keys [::watch ::listeners] :as ws} @asset-watcher-state]
        (when watch
          (let [{etl ::fw/event-type->listeners} ((::fw/state watch))
                remove-listener (::fw/remove-listener watch)]
            ;; following should be a function in filewatcher or something
            ;; remove all current listeners
            (doseq [[e ls] etl]
              (doseq [{:keys [::fw/listener-key]} ls]
                (remove-listener listener-key)))
            ;; add all stored listeners
            (doseq [[_ {:keys [::fw/listener-key
                               ::fw/func
                               ::fw/event-type
                               ::fw/re-filter]}] listeners]
              ((::fw/add-listener watch) event-type listener-key re-filter func))))))))

(defn start-asset-watcher
  ;; handing in config map for the first argument here. This
  ;; indirection is due to awkward load-order concerns. see 
  ([{:keys [reactive]}] 
   (when reactive
     (start-asset-watcher)))
  ([]
   (thr/start-thread
     (fn []
       (locking asset-watcher-state
         (let [{:keys [::watch ::listeners] :as ws} @asset-watcher-state]
           (if (or (not watch)
                   ((::fw/cancelled? watch)))
             (let [w (fw/start-watch
                       (.FullName (fs/info "Assets"))
                       500)]
               (swap! asset-watcher-state assoc ::watch w)
               (update-watch)
               w)
             watch)))))))

(defn stop-asset-watcher []
  (locking asset-watcher-state
    (let [{:keys [::watch]} @asset-watcher-state]
      (when watch ((::fw/stop watch)))
      (swap! asset-watcher-state dissoc ::watch))))

(defn add-listener [e k r f]
  (swap! asset-watcher-state assoc-in [::listeners k]
    {::fw/listener-key k
     ::fw/func f
     ::fw/event-type e
     ::fw/re-filter r})
  (update-watch))

(defn remove-listener [k]
  (swap! asset-watcher-state mu/dissoc-in [::listeners k])
  (update-watch))
