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
           [System.Threading Thread ThreadStart]))

;; ============================================================
;; utils

(defmacro gets [m & ks]
  `(-> ~m ~@(map (fn [k] `(get ~k)) ks)))

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
  (s/keys :req [::g ::fsis]))

(defn merge-file-graphs
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

(defn info-children [info]
  {:pre [(as/loud-valid? ::info info)]}
  (condp instance? info
    FileInfo empty-set
    DirectoryInfo (set (.GetFileSystemInfos info))))

(defn path [x]
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
(defn info ^FileSystemInfo [x]
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
(defn file-graph [root]
  {:pre [(as/loud-valid? ::info-path root)]
   :post [(or (nil? %) (as/loud-valid? ::file-graph %))]}
  (when-let [root-info (info root)]
    (let [kids (info-children root-info)
          fg {::g {(path root-info) (into #{} (map path) kids)}
              ::fsis {(path root-info) root-info}}]
      (transduce
        (map file-graph)
        merge-file-graphs
        fg
        kids))))

(defn add-file [fg path]
  (merge-file-graphs fg (file-graph path)))

(defn remove-file [fg path]
  (let [ps (keys (::g (file-graph path)))
        remove (fn [m] (persistent! (reduce dissoc! (transient m) ps)))]
    (-> fg
      (update ::g remove)
      (update ::fsis remove))))

;; ============================================================
;; watch

;; ------------------------------------------------------------
;; api

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
  (s/map-of ::event-type (s/* ::listener)))

(s/def ::watch-data
  (s/keys :req [::history ::event-types ::file-graph ::event-type->listeners]))

(defn add-event [history {:keys [::event-type ::path] :as event}]
  (assoc-in history [path event-type] event))

(defn- qwik-event [path event-type time]
  {::path path, ::event-type event-type, ::time time})

(defn before? [^DateTime d1 ^DateTime d2]
  (< (DateTime/Compare d1 d2) 0))

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

(defmethod event-change ::child-create [e watch-data path]
  (let [ct (.CreationTime (get-info watch-data path))]
    (if-let [{:keys [::time]} (gets watch-data ::history path e)]
      (when (before? time ct)
        (qwik-event path e ct))
      (qwik-event path e ct))))

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

(defn- run-listeners [{:keys [::event-types, ::event-type->listeners]},
                     path->changes]
  (dorun
    (for [e event-types
          l (event-type->listeners e)
          ch (path->changes e)]
      (apply-listener l ch))))

(defn- indexed-changes [watch-data]
  (let [changes (for [path (keys (gets watch-data ::file-graph ::g))
                      e (::event-types watch-data)
                      :let [change (event-change e watch-data path)]
                      :when change]
                  change)]
    (group-by ::path changes)))

(defn- update-history [history path->changes]
  {:pre [(as/loud-valid? (s/map-of ::path ::event) path->changes)
         (as/loud-valid? ::history history)]
   :post [(as/loud-valid? ::history %)]}
  (letfn [(f1 [history path changes]
            (update history path merge-with into
              (group-by ::event-type changes)))]
    (reduce-kv f1 history path->changes)))

(defn- update-file-graph [file-graph path->changes]
  (let [e->ps (-> (group-by ::event (eduction (mapcat val) path->changes))
                (mu/map-vals ::path))]
    (as-> file-graph file-graph
      (reduce add-file file-graph (::create e->ps))
      (reduce remove-file file-graph (::destroy e->ps)))))

(defn watch-step [watch-data]
  (let [path->changes (indexed-changes watch-data)]
    (run-listeners watch-data path->changes) ;; side effecting
    (-> watch-data
      (update ::history update-history path->changes)
      (update ::file-graph update-file-graph path->changes))))

(defn- remove-listener [watch-data listener-key]
  ;; stupid way for now, no one cares
  (update watch-data ::event-type->listeners
    (fn [e->l]
      (mu/map-vals e->l
        (fn [ls]
          (into (empty ls)
            (remove #(= (::listener-key %) listener-key))
            ls))))))

(defn- add-listener [watch-data, {:keys [::listener-key ::event-type]
                                  :as new-listener}]
  (-> watch-data
    (remove-listener listener-key) ;; so stupid
    (assoc-in [::event-type->listeners ::event-type] new-listener)))

(defn- start-thread [f]
  (let [t (Thread.
            (gen-delegate ThreadStart []
              (f)))]
    (.Start t)
    t))

(defn start-watch [root, interval]
  (let [watch-state (atom {::event-types #{}
                           ::event-type->listeners {}
                           ::history {}
                           ::file-graph (file-graph root)})
        control (atom {:interval interval
                       :should-loop true})
        thread (start-thread
                 (fn watch-loop []
                   (loop []
                     (let [{:keys [interval should-loop]} @control]
                       (when should-loop
                         (Thread/Sleep interval)
                         (reset! watch-state ;; NOT swap!; watch-step side-effects.
                           (watch-step @watch-state))
                         (recur))))))]
    {::thread thread
     ::set-interval (fn [i] (swap! control assoc :interval i))
     ::state (fn [] @watch-state) ;; for debugging
     ::control (fn [] @control) ;; for debugging
     ::stop (fn [] (swap! control assoc :should-loop false))
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
     ::cancelled (fn [] (and (:should-loop @control) (.IsAlive thread)))}))
