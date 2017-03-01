(ns arcadia.internal.asset-watcher
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.file-system :as fs]
            [arcadia.internal.thread :as thr]
            [arcadia.internal.map-utils :as mu])
  (:import [System.IO FileSystemInfo]
           [Arcadia UnityStatusHelper]))

;; Contains the arcadia asset filewatcher. Separate namespace to
;; decouple from arcadia.internal.state (which just holds the
;; application state atom, ideally containing more or less pure data) and
;; arcadia.internal.filewatcher itself, which is a general-purpose
;; filewatcher and shouldn't contain things this specific to Arcadia.

;; arcadia.config depends on this namespace, so we can't directly
;; query the existing configuration here. If we want to specify
;; dead-watch from configuration, we'll need to set it elsewhere,
;; before constructing the asset watcher itself. We should also maybe
;; change the name "dead-watch" to something more externally
;; respectable.

;; We have to be a bit careful: too much fanciness leads to
;; proliferation of atoms and callbacks.

;; There are no dependency problems with storing dead-watch in
;; arcadia.internal.state.

;; We need some way of pausing and restarting filewatcher if it's
;; going to be properly reactive.

;; (defonce dead-watch
;;   (atom
;;     (not UnityStatusHelper/IsInEditor)))

;; ;; seems a little janky
;; (defn- watch-promise
;;   "Returns a promise after starting a thread which will deliver a new filewatcher to the promise."
;;   []
;;   (let [p (promise)]
;;     (if-not @dead-watch
;;       (thr/start-thread
;;         (fn []
;;           (deliver p
;;             (fw/start-watch
;;               (.FullName
;;                 (fs/info "Assets"))
;;               500))))
;;       (deliver p nil))
;;     p))

;; (defonce ^:private asset-watcher-ref
;;   (atom
;;     (watch-promise)))

;; (defn asset-watcher []
;;   @@asset-watcher-ref)

;; move the parts of this that aren't actually the filewatcher to
;; arcadia.internal.state if that seems feasible once things are
;; working
(defonce asset-watcher-state
  (atom {}))

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
  ([{:keys [reactive?]}] 
   (when reactive?
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
      (when watch ((::stop watch)))
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
