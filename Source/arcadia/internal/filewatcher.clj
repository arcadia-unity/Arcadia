(ns ^{:doc
      "General purpose filewatcher. Minimal allocations if no change."}
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
  (:import [Arcadia StringHelper ArrayHelper]
   [System.Collections Queue]
           [System.IO FileSystemInfo DirectoryInfo FileInfo Path File]
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

(s/fdef merge-file-graphs
  :args (s/alt
          :0 #{[]}
          :1 ::file-graph
          :2 (s/cat :g1 ::file-graph, :g2 ::file-graph))
  :ret ::file-graph)

(defn- merge-file-graphs
  ([] {::g {}, ::fsis {}, ::type ::file-graph})
  ([fg] fg)
  ([{g1 ::g :as fg1}
    {g2 ::g :as fg2}]
   (let [
         ;; Path names are fully-qualified, and fully-qualified paths
         ;; don't move, they are only created or deleted. We can
         ;; therefore define nodes in the graph as a whole that should
         ;; be removed via a merge. There are those that, for a given
         ;; key shared by both graphs, are in that key's children for
         ;; the first graph and missing from that key's children in
         ;; the second.
         removed (reduce-kv 
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

(s/fdef info-children
  :args (s/cat :info ::info))

(defn- info-children [info]
  ;;{:pre [(as/loud-valid? ::info info)]}
  (condp instance? info
    FileInfo empty-set
    DirectoryInfo (into #{} 
                    (remove ;; for emacs nonsense
                      #(let [n (.Name ^FileSystemInfo %)]
                         (StringHelper/StartsWith n "#")
                         (StringHelper/StartsWith n ".#"))) 
                    (.GetFileSystemInfos info))))

;; (s/fdef path
;;   :args (s/cat :arg #(instance? FileSystemInfo %)))

(defn- path [x]
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

(s/fdef info
  :args (s/cat :arg ::info-path)
  :ret (s/or :info ::info
             :nil nil?))

;; !!!!THIS SOMETIMES RETURNS NIL!!!!
(defn- info ^FileSystemInfo [x]
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


(s/fdef file-graph
  :args (s/alt
          :no-opts (s/cat :root ::path)
          :with-opts (s/cat
                       :path ::path
                       :opts map?))
  :ret (s/or :info ::info
             :nil nil?))

;; this might screw up symlinks
(defn file-graph
  ([root] (file-graph root nil))
  ([root {:keys [::do-not-descend?] :as opts}]
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
   ;; {:pre [(as/loud-valid? ::path path)
   ;;        (as/loud-valid? ::file-graph fg)]}
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
  ;; {:pre [(as/loud-valid? ::file-graph fg)
  ;;        (as/loud-valid? ::path path)]}
  (mu/checked-keys [[::fsis] fg]
    (get fsis path)))

(defmethod get-info ::watch-data [watch-data path]
  (mu/checked-keys [[::file-graph] watch-data]
    (get-info file-graph path)))

(defn contains-path? [fg, path]
  ;; {:pre [(as/loud-valid? ::file-graph fg)
  ;;        (as/loud-valid? ::path path)]}
  (-> fg (get ::g) (contains? path)))

(defmulti get-children
  (fn [data path]
    (mu/checked-keys [[::type] data]
      type)))

(defmethod get-children ::file-graph [fg, path]
  ;; {:pre [(as/loud-valid? ::file-graph fg)
  ;;        (as/loud-valid? ::path path)]}
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

(defn- regex? [x]
  (instance? System.Text.RegularExpressions.Regex x))

(s/def ::re-filter
  (s/or
    :directory #{:directory}
    :string string?
    :strings (s/and
               vector?
               (s/coll-of string?))))

(s/def ::listener
  (s/keys :req [::listener-key ::func ::event-type ::re-filter]))

(s/fdef apply-listener
  :args (s/cat :listener ::listener, :event ::event))

(defn- apply-listener [{:keys [::func] :as listener} event]
  (func event))

(s/fdef run-listeners
  :args (s/cat
          :watch-data ::watch-data
          :changes (s/every ::event))
  :ret nil?)

(defn- re-filter-matches? [re-filter path]
  (or (= :directory re-filter)
      (if (string? re-filter)
        (StringHelper/EndsWith path re-filter)
        (if (vector? re-filter)
          (loop [i (int 0)]
            (when (< i (count re-filter))
              (or (StringHelper/EndsWith path (nth re-filter i))
                  (recur (inc i)))))
          (throw (Exception. (str "Invalid re-filter type: " (type re-filter))))))))

(defn- run-listeners
  "Side-effecting. Given the watch-data and a coll of changes, will
  run all listeners matching both the event type and (via ::re-filter)
  the path of the file for each change."
  [{:keys [::event-type->listeners]
    {:keys [::fsis]} ::file-graph,
    :as watch-data},
   changes]
  (letfn [(process-change [{:keys [::path ::event-type] :as ch}]
            (doseq [{:keys [::re-filter ::info]
                     :as listener} (event-type->listeners event-type)
                    :when (re-filter-matches? re-filter path)]
              (apply-listener listener ch)))]
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

(def ^:private preserving-reduced (var-get #'clojure.core/preserving-reduced))

(defn- splice-if [pred]
  (fn [rf]
    (let [rrf (preserving-reduced rf)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (if (pred input)
           (reduce rrf result input)
           (rf result input)))))))

;; I guess it makes sense for the regex prefilter to come from the
;; listeners, rather than the event types? Because if they come from
;; the event types you would have to make a new event type for each
;; new file pattern, which would lead to redundant checks and more
;; allocations and stuff
(defn- file-path-filter-fn [{:keys [::event-type->listeners]}]
  (let [^|System.String[]| ends (ArrayHelper/CountedArray (type-args System.String)
                                  (into []
                                    (af/comp
                                      cat
                                      (map ::re-filter)
                                      (splice-if vector?)
                                      (filter string?))
                                    (mu/valsr event-type->listeners)))]
    (fn [^String path]
      (StringHelper/EndsWithAny path ends))))

(defn changes [{{:keys [::g, ::fsis], :as fg} ::file-graph,
                 started ::started,
                 :as watch-data}]
  (let [infos (mu/valsr (gets watch-data ::file-graph ::fsis))
        events (vec (keys (::event-type->listeners watch-data)))
        fpf (file-path-filter-fn watch-data)
        filt (fn [x]
               (or (and (instance? FileInfo x)
                        (let [^FileInfo x x]
                          (fpf (.FullName x))))
                   (instance? DirectoryInfo x)))] ;; need all directory infos for topology updating
    (into []
      (af/comp
        (filter filt) ;; takes us from 30-40% CPU to 7% CPU on my computer
        (map refresh)
        (mapcat #(info-changes % watch-data events)))
      infos)))

;; ------------------------------------------------------------
;; updating topology

(s/fdef update-history
  :args (s/cat :history ::history, :changes (s/every ::event))
  :ret ::history)

(defn- update-history [history changes]
  (reduce (fn [history {:keys [::path ::event-type] :as change}]
            (assoc-in history [path event-type] change))
    history
    changes))

(defn- update-file-graph [file-graph changes]
  (->> changes
    (filter #(= ::alter-children (::event-type %)))
    (reduce (fn [fg {:keys [::path]}]
              (merge-file-graphs fg
                (arcadia.internal.filewatcher/file-graph path)))
       file-graph)))

;; ------------------------------------------------------------
;; driver 

(defonce ws-weirdness-log (atom []))

(s/fdef watch-step
  :args (s/cat :watch-data ::watch-data)
  :ret ::watch-data)

(defn- watch-step [{:keys [::event-type->listeners] :as watch-data}] 
  (let [chs (changes watch-data)]
    (run-listeners watch-data chs) ;; side effecting
    (let [wd2 (-> watch-data
                   (update ::history update-history chs)
                   (update ::file-graph update-file-graph chs)
                   (assoc ::prev-file-graph (::file-graph watch-data)))]
       (if (some #(= ::alter-children (::event-type %)) chs) ;; stupid but easy to type
         (if (= (::file-graph wd2)
                (::file-graph (::prev-file-graph watch-data)))
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
        (let [{:keys [::interval ::should-loop]} @control]
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
  ;; the listener key; r is the regex filter; f is the
  ;; listener function
  (fn [e k r f]    
    (let [listener {::listener-key k
                    ::func f
                    ::event-type e
                    ::re-filter r}]
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
