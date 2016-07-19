(ns arcadia.internal.filewatcher
  ;; dev
  (:use clojure.pprint clojure.repl)
  (:require [arcadia.introspection :as i]
            [arcadia.internal.benchmarking :as b])
  ;; prod
  (:require [clojure.spec :as s]
            [arcadia.internal.spec :as as]
            [arcadia.internal.map-utils :as mu])
  (:import [System.Collections Queue]
           [System.IO FileSystemInfo DirectoryInfo FileInfo]
           [System DateTime]
           [System.Threading Thread ThreadStart]
           [UnityEngine Debug]))

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
    :null nil?
    :populated (s/keys :req [::g ::fsis])))

(defn- merge-file-graphs
  ([] {::g {} ::fsis {}})
  ([fg] fg)
  ([fg1 fg2]
   {:pre [(as/loud-valid? ::file-graph fg1)
          (as/loud-valid? ::file-graph fg2)]
    :post [(as/loud-valid? ::file-graph %)]}
   (-> fg1
     (update ::g #(merge-with into % (::g fg2)))
     (update ::fsis merge (::fsis fg2)))))

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
(defn- file-graph [root]
  {:pre [(as/loud-valid? ::info-path root)]
   :post [(as/loud-valid? ::file-graph %)]}
  (when-let [root-info (info root)]
    (let [kids (info-children root-info)
          fg {::g {(path root-info) (into #{} (map path) kids)}
              ::fsis {(path root-info) root-info}}]
      (transduce
        (map file-graph)
        merge-file-graphs
        fg
        kids))))

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

(defn get-info [fg, path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (-> fg (get ::fsis) (get path)))

(defn contains-path? [fg, path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (-> fg (get ::g) (contains? path)))

(defn children [fg, path]
  {:pre [(as/loud-valid? ::file-graph fg)
         (as/loud-valid? ::path path)]}
  (-> fg (get ::g) (get path)))

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
  (s/map-of ::event-type (s/coll-of ::listener [])))

(s/def ::watch-data
  (s/keys :req [::history ::event-types ::file-graph ::event-type->listeners]))

(defn- add-event [history {:keys [::event-type ::path] :as event}]
  (assoc-in history [path event-type] event))

(defn- qwik-event [path event-type time]
  {::path path, ::event-type event-type, ::time time})

(defn- before? [^DateTime d1 ^DateTime d2]
  (< (DateTime/Compare d1 d2) 0))

;; ------------------------------------------------------------
;; event-change

(defmulti event-change (fn [e watch-data path] e))

;; not sure this is robust to delete
(defmethod event-change ::write [e watch-data path]
  (let [lwt (.LastWriteTime (get-info watch-data path))]
    (if-let [{:keys [::time]} (gets watch-data ::history path e)]
      (when (before? time lwt)
        (qwik-event path e lwt))
      (qwik-event path e lwt))))

;; this one's sort of stupid
(defmethod event-change ::create [e watch-data path]
  (let [ct (.CreationTime (get-info watch-data path))]
    (if-let [{:keys [::time]} (gets watch-data ::history path e)]
      (when (before? time ct)
        (qwik-event path e ct))
      (qwik-event path e ct))))

(defmethod event-change ::create-child [e {:keys [::file-graph] :as watch-data} path]
  (let [info (get-info file-graph path)]
    (.Refresh info)
    (when (instance? DirectoryInfo info)
      (let [lwt (.LastWriteTime info)]
        (if-let [{:keys [::time]} (gets watch-data ::history path e)]
          (when (before? time lwt)
            (qwik-event path e lwt))
          (qwik-event path e lwt))))))

(defmethod event-change ::delete [e watch-data path]
  (let [^FileSystemInfo fsi (get-info watch-data path)]
    (when-not (or (.Exists fsi) (gets watch-data ::history path e))
      {::path path, ::event-type e, ::time (DateTime/Now)})))

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

(defn- changes [watch-data]
  (doduce ; refresh all file infos
    (map (fn [^FileSystemInfo fsi]
           (.Refresh fsi)))
    (vals (gets ((::state watch)) ::file-graph ::fsis)))
  (let [events (keys (::event-type->listeners watch-data))
        paths (keys (gets watch-data ::file-graph ::g))]
    (into []
      (mapcat
        (fn [e]
          (eduction
            (keep #(event-change e watch-data %))
            paths)))
      events)))

(defn- update-history [history changes]
  {:pre [(as/loud-valid? (s/spec (s/* ::event)) changes)
         (as/loud-valid? ::history history)]
   :post [(as/loud-valid? ::history %)]}
  (reduce (fn [history {:keys [::path ::event-type] :as change}]
            (assoc-in history [path event-type] change))
    history
    changes))

(defn- update-file-graph [file-graph changes]
  (letfn [(process [file-graph event f]
            (transduce
              (comp (filter #(= event (::event-type %))) (map ::path))
              f file-graph changes))]
    (-> file-graph
      (process ::create-child add-file)
      (process ::destroy remove-file))))

(defn- watch-step [{:keys [::event-type->listeners] :as watch-data}]
  {:pre [(as/loud-valid? ::watch-data watch-data)]}
  (let [chs (changes watch-data)]
    (run-listeners event-type->listeners chs) ;; side effecting
    (-> watch-data
      (update ::history update-history chs)
      (update ::file-graph update-file-graph chs))))

(defn- remove-listener
  ([watch-data] watch-data)
  ([watch-data listener-key]
   (update watch-data ::event-type->listeners mu/map-vals e->ls
     (fn [ls]
       (into [] (remove #(= listener-key (::listener-key %))) ls)))))

(defn- add-listener
  ([watch-data] watch-data)
  ([watch-data, {:keys [::listener-key ::event-type]
                 :as new-listener}]
   (-> watch-data
     (remove-listener listener-key) ;; so stupid
     (update-in [::event-type->listeners event-type] conj new-listener))))

(defn- start-thread [f]
  (let [t (Thread.
            (gen-delegate ThreadStart []
              (f)))]
    (.Start t)
    t))

(defonce ^:private all-watches
  (atom #{}))

(defn kill-all-watches []
  (doseq [{:keys [::stop]} @all-watches]
    (stop))
  (swap! all-watches
    (fn [ws]
      (into #{} (remove #((::cancelled %))) ws))))

(defn start-watch [root, interval]
  (let [watch-state (atom {::event-types #{}
                           ::event-type->listeners {}
                           ::history {}
                           ::file-graph (file-graph root)})
        control (atom {:interval interval
                       :should-loop true})
        errors (atom [])
        thread (start-thread
                 (fn watch-loop []
                   (loop []
                     (as/loud-valid? ::watch-data @watch-state)
                     (let [{:keys [interval should-loop]} @control]
                       (when should-loop
                         (Thread/Sleep interval)
                         (try
                           (reset! watch-state ;; NOT swap!; watch-step side-effects.
                             (watch-step @watch-state))
                           (catch Exception e
                             (swap! errors conj e)
                             (throw e)))
                         (recur))))))
        watch {::thread thread
               ::set-interval (fn [i] (swap! control assoc :interval i))
               ::state (fn [] @watch-state) ;; for debugging
               ::control (fn [] @control) ;; for debugging
               ::stop (fn [] (swap! control assoc :should-loop false))
               ::errors (fn [] @errors)
               ::add-listener (fn [k f e]
                                (let [listener {::listener-key k
                                                ::func f
                                                ::event-type e}]
                                  (if (s/valid? ::listener listener)
                                    (swap! watch-state add-listener listener)
                                    (throw
                                      (System.ArgumentException.
                                        (str
                                          "Attempting to add invalid listener:/n"
                                          (with-out-str (s/explain ::listener listener))))))))
               ::remove-listener (fn [k] (swap! watch-state remove-listener k))
               ::cancelled (fn [] (not (and (:should-loop @control) (.IsAlive thread))))}]
    (swap! all-watches conj watch)
    watch))
