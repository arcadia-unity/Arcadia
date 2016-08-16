(ns ^{:doc
      "General purpose filewatcher. 
Much much faster (about 150 to 375 times faster) than walking
assets periodically, minimal allocations if no change."}
    arcadia.internal.filewatcher
  (:use clojure.pprint
        [clojure.repl :exclude [dir]])
  (:require [arcadia.introspection :as i]
            [arcadia.internal.thread :as thr]
            [arcadia.internal.benchmarking :as b]
            [clojure.repl :as repl]
            [clojure.set :as set])
  ;; prod
  (:require [clojure.spec :as s]
            [clojure.core.reducers :as r]
            [arcadia.internal.functions :as af]
            [arcadia.internal.file-system :as fs]
            [arcadia.internal.spec :as as]
            [arcadia.internal.map-utils :as mu]
            [arcadia.internal.array-utils :as au]
            [clojure.test :as t]
            [arcadia.internal.test :as at]
            [clojure.data :as d])
  (:import [System.Collections Queue]
           [System.IO FileSystemInfo DirectoryInfo FileInfo Path File]
           [System DateTime]
           [System.Threading Thread ThreadStart]
           [UnityEngine Debug]))

(as/push-assert false)

;; ============================================================
;; utils

(defmacro gets [m & ks]
  (list* `-> m (for [k ks] `(get ~k))))

(defn- absorber
  ([])
  ([_])
  ([_ _])
  ([_ _ _]))

(defn- doduce [xform coll]
  (transduce xform absorber nil coll))

(defn- assoc-by [m f x]
  (assoc m (f x) x))

(defn- before? [^DateTime d1 ^DateTime d2]
  (< (DateTime/Compare d1 d2) 0))

(defn- latest
  ([^DateTime a, ^DateTime b]
   (if (before? a b) b a))
  ([^DateTime a, ^DateTime b & ds]
   (reduce latest (latest a b) ds)))

;; ============================================================
;; file graph

(s/def ::path string?)

(s/def ::info #(instance? FileSystemInfo %))

(s/def ::info-path (as/qwik-or ::info ::path))

(s/def ::g ;; map of paths to sets of paths!
  (s/map-of ::path (as/collude #{} ::path)))

(s/def ::fsis ;; map of paths to FileSystemInfos!
  (s/map-of ::path ::info))

(s/def ::file-graph
  (s/or
    :nil nil?
    :some (s/and #(= ::file-graph (::type %))
            (s/keys :req [::g ::fsis])))
  ;; (s/nilable
  ;;   (s/and #(= ::file-graph (::type %))
  ;;     (s/keys :req [::g ::fsis])))
  )


(defn- merge-file-graphs
  ([] {::g {}, ::fsis {}, ::type ::file-graph})
  ([fg] fg)
  ([{g1 ::g :as fg1}
    {g2 ::g :as fg2}]
   {:pre [(as/loud-valid? ::file-graph fg1)
          (as/loud-valid? ::file-graph fg2)]
    :post [(as/loud-valid? ::file-graph %)]}
   (let [removed (reduce-kv
                   (fn [bldg k v]
                     (into bldg (set/difference v (get g2 k))))
                   []
                   (select-keys g1 (keys g2)))]
     (-> fg1
       (update ::g
         (fn [g]
           (reduce dissoc
             (merge g g2)
             removed)))
       (update ::fsis
         (fn [fsis]
           (reduce dissoc
             (merge fsis (::fsis fg2))
             removed)))))))

(def empty-set #{}) ;;omg

(defn- info-children [info]
  {:pre [(as/loud-valid? ::info info)]}
  (condp instance? info
    FileInfo empty-set
    DirectoryInfo (set (.GetFileSystemInfos info))))

(defn- path [x]
  {:pre [(instance? FileSystemInfo x)]}
  (.FullName x))

(defn- file-info [x]
  (cond
    (instance? FileInfo x) x
    (string? x) (try
                  (FileInfo. x)
                  (catch System.ArgumentException e))
    :else (throw (System.ArgumentException. "Expects FileInfo or String."))))

(defn- directory-info [x]
  (cond
    (instance? DirectoryInfo x) x
    (string? x) (try
                  (DirectoryInfo. x)
                  (catch System.ArgumentException e))
    :else (throw (System.ArgumentException. "Expects DirectoryInfo or String."))))

;; !!!!THIS SOMETIMES RETURNS NIL!!!!
(defn- info ^FileSystemInfo [x]
  {:pre [(as/loud-valid? ::info-path x)]
   :post [(as/loud-valid? (s/or ::info ::info :nil nil?) %)]}
  (cond
    (instance? FileSystemInfo x) x
    ;; Yes I hate it too
    (string? x) (let [fi (file-info x)] 
                  (if (and fi (.Exists fi))
                    fi
                    (let [di (directory-info x)] 
                      (when (and di (.Exists di))
                        di))))
    :else (throw (System.ArgumentException. "Expects FileSystemInfo or String."))))

;; this might screw up symlinks
(defn file-graph
  ([root] (file-graph root nil))
  ([root {:keys [::do-not-descend?] :as opts}]
   {:pre [(as/loud-valid? ::info-path root)]
    :post [(as/loud-valid? ::file-graph %)]}
   (when-not (if do-not-descend? (do-not-descend? (fs/path root)))
     (when-let [root-info (info root)]
       (let [kids (info-children root-info)
             fg {::type ::file-graph
                 ::g {(path root-info) (into #{} (map path) kids)}
                 ::fsis {(path root-info) root-info}}]
         (transduce
           (map #(file-graph % opts))
           merge-file-graphs
           fg
           kids))))))

(defn- add-file
  ([fg] fg)
  ([fg path]
   {:pre [(as/loud-valid? ::path path)
          (as/loud-valid? ::file-graph fg)]}
   (Debug/Log "Should be adding a file now")
   (merge-file-graphs fg (file-graph path))))

(defn- remove-file
  ([fg] fg)
  ([fg path]
   (let [ps (keys (::g (file-graph path)))
         remove (fn [m] (persistent! (reduce dissoc! (transient m) ps)))]
     (-> fg
       (update ::g remove)
       (update ::fsis remove)))))

;; ============================================================
;; watch

;; ------------------------------------------------------------
;; api (for defining listeners)

(defmulti get-info
  (fn [data path]
    (mu/checked-keys [[::type] data]
      type)))

(defmethod get-info ::file-graph [fg path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (mu/checked-keys [[::fsis] fg]
    (get fsis path)))

(defmethod get-info ::watch-data [watch-data path]
  (mu/checked-keys [[::file-graph] watch-data]
    (get-info file-graph path)))

(defn contains-path? [fg, path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (-> fg (get ::g) (contains? path)))

(defmulti get-children
  (fn [data path]
    (mu/checked-keys [[::type] data]
      type)))

(defmethod get-children ::file-graph [fg, path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (gets fg ::g path))

(defmethod get-children ::watch-data [wd, path]
  (mu/checked-keys [[::file-graph] wd]
    (get-children file-graph path)))

;; ============================================================
;; file event history

(s/def ::event-type keyword?)

(s/def ::time #(instance? System.DateTime %))

(s/def ::event
  (s/keys :req [::event-type ::time ::path]))

(s/def ::history ; yeah I know its more complicated, but much more efficient
  (s/map-of ::path
    (s/map-of ::event-type ::event)))

(s/def ::event-type->listeners
  (s/map-of ::event-type (as/collude [] ::listener)))

(s/def ::started #(instance? System.DateTime %))

(s/def ::watch-data
  (s/and
    #(= ::watch-data (::type %))
    (s/keys :req [::history ::file-graph ::event-type->listeners ::started ::path])))

(defn- add-event [history {:keys [::event-type ::path] :as event}]
  (assoc-in history [path event-type] event))

(defn- qwik-event [path event-type time]
  {::path path, ::event-type event-type, ::time time})

;; ------------------------------------------------------------
;; event-change

;; TODO: ensure some ::create, ::create-child, ::delete things are in
;; place even if those listeners haven't been explicitly set on the
;; watch, so they will run anyway

(defmulti event-change (fn [e watch-data path] e))

(defn- change-buddy [{:keys [::started] :as watch-data} path e t]
  (when (before? started t)
    (if-let [{:keys [::time]} (gets watch-data ::history path e)]
      (when (before? time t)
        (qwik-event path e t))
      (qwik-event path e t))))

;; not sure this is robust to delete
;; (defmethod event-change ::write-file
;;   [e, watch-data, path]
;;   (let [fsi (get-info watch-data path)]
;;     (when (instance? FileInfo fsi)
;;       (change-buddy watch-data path e (.LastWriteTime fsi)))))

;; a file is created if:
;; - its creation time has changed
;; - it wasn't in the filegraph previously

;; ... but that last one is hard to track at the moment. we don't
;; have access to the n - 1 filegraph, only the current filegraph.

;; Answer might be to hang onto the n - 1 filegraph, which would be
;; a bit awkward given the current layout

;; which *makes sense* because it gives us a good meaning for change in the filesystem
;; (defmethod event-change ::create
;;   [e, watch-data, path]  
;;   (change-buddy watch-data path e
;;     (.CreationTime (get-info watch-data path))))

;; this seems to be about all we can say just by looking at the time
;; fields of DirectoryInfos
(defmethod event-change ::alter-children
  [e, watch-data, path]
  (let [fsi (get-info watch-data path)]
    (when (instance? DirectoryInfo fsi)
      (when-let [ch (change-buddy watch-data path e (.LastWriteTime fsi))]
        (let [subfs (into #{} (map #(.FullName %)) (.GetFileSystemInfos fsi))]
          (when (not= subfs (get-children watch-data path))
            ch))))))

;; all changes to directories
;; for gross but ultimately much more efficient optimization to refresh
(defmethod event-change ::alter-directory
  [e, watch-data, path]
  (let [fsi (get-info watch-data path)]
    (when (instance? DirectoryInfo fsi)
      (change-buddy watch-data path e (.LastWriteTime fsi)))))

(defmethod event-change ::create-modify-delete-file
  [e, watch-data, path]
  (let [^FileSystemInfo fsi (get-info watch-data path)]
    (when (instance? FileInfo fsi)
      (or (and                                       ; check for delete
            (not (.Exists fsi))                      ; it's not there...
            (qwik-event path e DateTime/Now))        ; return event
        (change-buddy watch-data path e              ; other cases
          (latest ; yes. maybe it was created after it was written to. nothing's off the table
            (.CreationTime fsi)
            (.LastWriteTime fsi)))))))

(defmethod event-change ::delete
  [e watch-data path]
  (let [^FileSystemInfo fsi (get-info watch-data path)]
    (when-not (or (.Exists fsi) (gets watch-data ::history path e))
      (qwik-event path e DateTime/Now))))

;; ============================================================
;; file listening

(s/def ::listener
  (s/keys :req [::listener-key ::func ::event-type] :opt [::re-filter]))

(defn- apply-listener [{:keys [::func] :as listener} event]
  {:pre [(as/loud-valid? ::listener listener)
         (as/loud-valid? ::event event)]}
  (func event))

;; side-effecting
(defn- run-listeners [event-type->listeners, changes]
  {:pre [(as/loud-valid? ::event-type->listeners event-type->listeners)]}
  (letfn [(process-change [ch]
            (doduce (map #(apply-listener % ch))
              (event-type->listeners (::event-type ch))))]
    (doduce (map process-change) changes)))

;; ------------------------------------------------------------
;; changes, and associated low-level helpers

(defn- refresh ^FileSystemInfo [^FileSystemInfo fsi]
  (.Refresh fsi)
  fsi)

(defn- info-changes [^FileSystemInfo fsi, watch-data, events]
  (let [p (.FullName fsi)]
    (eduction
      (keep #(event-change % watch-data p))
      events)))

(defn- new-path? [p watch-data]
  (not (some-> watch-data ::prev-file-graph ::g (get p))))

(defn- directory-changes [^DirectoryInfo di, {fg ::file-graph, :as watch-data}, events]
  (let [child-files (eduction
                      (af/comp
                        (map #(get-info fg %))
                        (filter #(instance? FileInfo %))
                        (remove (fn [^FileInfo fi] ; needs to be disjoint with new files
                                  (new-path? (.FullName fi) watch-data)))
                        (map refresh)) ; !!
                      (get-children fg (.FullName di)))]
    (eduction
      (af/comp cat (mapcat #(info-changes % watch-data events)))
      [[di] child-files])))

(defn- directory-written? [^DirectoryInfo di, {:keys [::started] :as watch-data}]
  (let [p (.FullName di)
        lwt (.LastWriteTime di)]
    (when (before? started lwt)
      (if-let [{:keys [::time]} (gets watch-data ::history p ::alter-directory)]
        (before? time lwt)
        true))))

(defn- changes [{{:keys [::g, ::fsis], :as fg} ::file-graph,
                 started ::started,
                 :as watch-data}]
  (let [infos (mu/valsr (gets watch-data ::file-graph ::fsis))
        events (vec (keys (::event-type->listeners watch-data)))
        new-files (af/comp
                    (filter #(and (instance? FileInfo %)
                                  (new-path? (.FullName %) watch-data)))
                    (map refresh)
                    (mapcat #(info-changes % watch-data events)))
        changed-directories (af/comp
                              (filter #(instance? DirectoryInfo %))
                              (map refresh) ; !!
                              (filter #(directory-written? % watch-data))
                              (mapcat #(directory-changes % watch-data events)))
        chs (into []
              (r/cat
                (af/transreducer
                  new-files
                  infos),
                (af/transreducer  ; old files with changed parents
                  changed-directories
                  infos)))]
    chs))

;; ------------------------------------------------------------
;; updating topology

(defn- update-history [history changes]
  {:pre [(as/loud-valid? (s/spec (s/* ::event)) changes)
         (as/loud-valid? ::history history)]
   :post [(as/loud-valid? ::history %)]}
  (reduce (fn [history {:keys [::path ::event-type] :as change}]
            (assoc-in history [path event-type] change))
    history
    changes))

(defn- update-file-graph [file-graph changes]
  (->> changes
    (filter #(= ::alter-children (::event-type %)))
    (reduce (fn [fg {:keys [::path]}]
              (Debug/Log (str "merging for path: " path))
              (let [fg2 (merge-file-graphs fg
                          (arcadia.internal.filewatcher/file-graph path))]
                fg2))
       file-graph)))

;; ------------------------------------------------------------
;; driver 

(def ws-weirdness-log (atom []))

(defn- watch-step [{:keys [::event-type->listeners] :as watch-data}]
  {:pre [(as/loud-valid? ::watch-data watch-data)]}
  (let [chs (changes watch-data)]
    (when (seq chs)
      (Debug/Log (str "changes!\n" (with-out-str (pprint chs)))))
    (run-listeners event-type->listeners chs) ;; side effecting
    (let [wd2 (-> watch-data
                (update ::history update-history chs)
                (update ::file-graph update-file-graph chs)
                (assoc ::prev-file-graph (::file-graph watch-data)))]
      (if (some #(= ::alter-children (::event-type %)) chs) ;; stupid but easy to type
        (if (= (::file-graph wd2)
              (::file-graph
               (::prev-file-graph watch-data)))
          (do
            (swap! ws-weirdness-log conj
              (filter #(= ::alter-children (::event-type %)) chs))
            (throw (Exception. "something's up")))
          (watch-step wd2))
        wd2))))

;; ------------------------------------------------------------
;; this def should go somewhere I guess

(def constant-events
  #{::alter-children ::alter-directory})

;; ------------------------------------------------------------
;; listener API

(defn- remove-listener
  ([watch-data] watch-data)
  ([watch-data listener-key]
   (update watch-data ::event-type->listeners
     (fn [etl]
       (into {}
         (af/comp
           (map (fn [[k v]]
                  [k (into []
                       (remove #(= listener-key (::listener-key %)))
                       v)]))
           (filter (fn [[k v]] ;; clean up inessential events with no listeners
                     (or (constant-events k)
                         (seq v)))))
         etl)))))

(defn- add-listener
  ([watch-data] watch-data)
  ([watch-data, {:keys [::listener-key ::event-type]
                 :as new-listener}]
   (-> watch-data
       (remove-listener listener-key)
       (update-in [::event-type->listeners event-type]
         (fnil conj [])
         new-listener))))

;; ------------------------------------------------------------
;; thread management

(defonce ^:private all-watches
  (atom #{}))

(defn kill-all-watches []
  (doseq [{:keys [::stop]} @all-watches]
    (stop)))

;; gc dead watches 
(defn clean-watches
  ([]
   (swap! all-watches
     (fn swapso [xset]
       (clojure.set/select #(.IsAlive (::thread %))
         xset))))
  ([delay]
   (thr/start-thread
     (fn []
       (Thread/Sleep delay)
       (clean-watches)))))

(defn- watch-thread [{:keys [::watch-state,
                             ::control,
                             ::errors]}]
  (thr/start-thread
    (fn watch-loop []
      (loop []
        (as/loud-valid? ::watch-data @watch-state)
        (let [{:keys [::interval should-loop]} @control]
          (when should-loop
            (Thread/Sleep interval)
            (try
              (let [ws2 (watch-step @watch-state)]
                (swap! watch-state
                  (fn [{:keys [::event-type->listeners]}] ;; might have changed
                    (assoc ws2 ::event-type->listeners event-type->listeners))))
              (catch Exception e
                (clean-watches (* 2 interval))
                (swap! errors conj e)
                (throw e)))
            (recur)))))))

(defn- add-listener-fn [{:keys [::watch-state]}]
  ;; e is the event-type; k is
  ;; the listener k; f is the
  ;; listener function
  (fn [e k f]    
    (let [listener {::listener-key k
                    ::func f
                    ::event-type e}]
      (if (as/loud-valid? ::listener listener)
        (swap! watch-state add-listener listener)
        (throw
          (System.ArgumentException.
            (str "Attempting to add invalid listener:/n"
              (with-out-str
                (s/explain ::listener listener))))))
      listener)))

;; ------------------------------------------------------------
;; top level entry point

(defn start-watch [root, interval]
  (let [do-not-descend? (fn [p]
                          (boolean
                            (re-find #"\.git$" p)))
        watch-state (atom {::type ::watch-data
                           ::event-type->listeners (zipmap constant-events (repeat []))
                           ::path root
                           ::history {}
                           ::do-not-descend? do-not-descend?
                           ::started DateTime/Now
                           ::file-graph (file-graph root
                                          {::do-not-descend? do-not-descend?})})
        control (atom {::interval interval
                       ::should-loop true})
        errors (atom [])
        all-state {::watch-state watch-state
                   ::control control
                   ::errors errors}
        thread (watch-thread all-state)
        watch {::type ::watch
               ::thread thread
               ::state (fn [] @watch-state)
               ::control (fn [] @control)
               ::errors (fn [] @errors)
               ::set-interval (fn [i] (swap! control assoc ::interval i))
               ::stop (fn []
                        (let [control (swap! control assoc ::should-loop false)]
                          (clean-watches (* (::interval control) 2))
                          control))
               ::add-listener (add-listener-fn all-state)
               ::remove-listener (fn [k]
                                   (do (swap! watch-state remove-listener k)
                                       nil))
               ::cancelled? (fn [] (not (and (::should-loop @control) (.IsAlive thread))))}]
    (swap! all-watches conj watch)
    watch))

(as/pop-assert)
