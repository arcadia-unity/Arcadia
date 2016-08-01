(ns arcadia.internal.filewatcher
  ;; dev
  (:use clojure.pprint
        [clojure.repl :exclude [dir]])
  (:require [arcadia.introspection :as i]
            [arcadia.internal.benchmarking :as b]
            [clojure.repl :as repl]
            [clojure.set :as set])
  ;; prod
  (:require [clojure.spec :as s]
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
(defn- file-graph [root]
  {:pre [(as/loud-valid? ::info-path root)]
   :post [(as/loud-valid? ::file-graph %)]}
  (when-let [root-info (info root)]
    (let [kids (info-children root-info)
          fg {::type ::file-graph
              ::g {(path root-info) (into #{} (map path) kids)}
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
  (s/map-of ::event-type (s/coll-of ::listener [])))

(s/def ::started #(instance? System.DateTime %))

(s/def ::watch-data
  (s/and
    #(= ::watch-data (::type %))
    (s/keys :req [::history ::file-graph ::event-type->listeners ::started])))

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
  ;;  (Debug/Log (str "::alter-children considering " path))
  (let [fsi (get-info watch-data path)]
    (when (instance? DirectoryInfo fsi)
      ;;      (Debug/Log (str "::alter-children 1"))
      (when-let [ch (change-buddy watch-data path e (.LastWriteTime fsi))]
        ;;        (Debug/Log (str "::alter-children 2"))
        (let [subfs (into #{} (map #(.FullName %)) (.GetFileSystemInfos fsi))]
          (when (not= subfs (get-children watch-data path))
            ;;          (Debug/Log (str "::alter-children registering at " path))
            ch))))))

(defmethod event-change ::create-modify-delete-file
  [e, watch-data, path]
  (let [^FileSystemInfo fsi (get-info watch-data path)]
    ;;(Debug/Log (str "::create-modify-delete-file considering " path))
    (when (instance? FileInfo fsi)
      (or (and                                       ; check for delete
            (not (.Exists fsi))                      ; it's not there...
            ;;(not (gets watch-data ::history path e)) ; only once! it's deleted
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

(defn- changes [watch-data]
  (let [fsis (vals (gets watch-data ::file-graph ::fsis))]
    (doseq [^FileSystemInfo fsi fsis] ;; refresh all file system infos
      (.Refresh fsi)))
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
  (->> changes
    (filter #(= ::alter-children (::event-type %)))
    (reduce (fn [fg {:keys [::path]}]
              (Debug/Log (str "merging for path: " path))
              (let [fg2 (merge-file-graphs fg
                          (arcadia.internal.filewatcher/file-graph path))]
                (Debug/Log (str "merge result:\n"
                             (with-out-str (pprint fg2))))
                fg2))
      file-graph)))

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
        (watch-step wd2)
        wd2))))

(defn- remove-listener
  ([watch-data] watch-data)
  ([watch-data listener-key]
   (update watch-data ::event-type->listeners mu/map-vals
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
    (stop)))

;; gc dead watches
(defn clean-watches
  ([]
   (swap! all-watches
     (fn swapso [xset]
       (clojure.set/select #(.IsAlive (::thread %))
         xset))))
  ([delay]
   (start-thread
     (fn []
       (Thread/Sleep delay)
       (clean-watches)))))

(defn start-watch [root, interval]
  (let [watch-state (atom {::event-type->listeners {::alter-children []}
                           ::history {}
                           ::started DateTime/Now
                           ::file-graph (file-graph root)
                           ::type ::watch-data})
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
                           (let [ws2 (watch-step @watch-state)]
                             (swap! watch-state
                               (fn [{:keys [::event-type->listeners]}] ;; might have changed
                                 (assoc ws2 ::event-type->listeners event-type->listeners))))
                           (catch Exception e
                             (clean-watches (* 2 interval))
                             (swap! errors conj e)
                             (throw e)))
                         (recur))))))
        watch {::thread thread
               ::set-interval (fn [i] (swap! control assoc :interval i))
               ::state (fn [] @watch-state) ;; for debugging
               ::control (fn [] @control)   ;; for debugging
               ::stop (fn []
                        (let [control (swap! control assoc :should-loop false)]
                          (clean-watches (* (:interval control) 2))
                          control))
               ::errors (fn [] @errors)
               ::add-listener (fn [e k f] ;; e is the event-type; k is
                                          ;; the listener k; f is the
                                          ;; listener function
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
               ::cancelled? (fn [] (not (and (:should-loop @control) (.IsAlive thread))))}]
    (swap! all-watches conj watch)
    watch))

;; ============================================================
;; tests
;; ============================================================

;; absolute, so it doesn't screw up _your_ file system
(def ^:private test-path "/Users/timothygardner/Desktop/filesystemwatcher_tests")

(defn- path-combine [& paths]
  (reduce #(Path/Combine %1 %2) paths))

(defn- path-split [path]
  (vec (. path (Split (au/lit-array System.Char Path/DirectorySeparatorChar)))))

(defn- path-supers [path]
  (take-while (complement nil?)
    (iterate #(Path/GetDirectoryName %)
      path)))

(defn- txt [name & contents]
  {::type :txt
   ::name name
   ::contents (vec contents)})

(defn- dir [name & children]
  {::type :dir
   ::name name
   ::children (vec children)})

(defmulti ^:private make-file-system
  (fn [root spec] (::type spec)))

 ;; guard so I don't screw up _my_ file system
(defn- ward-file-system [root]
  (when-not (.Contains root test-path)
    (throw (Exception. "root must contain test path"))))

(defmethod make-file-system :dir [root spec]
  (ward-file-system root)
  (let [dir (DirectoryInfo. (Path/Combine root (::name spec)))]
    (if (.Exists dir)
      (doseq [fsi (.GetFileSystemInfos dir)]
        (cond
          (instance? DirectoryInfo fsi) (.Delete fsi true)
          (instance? FileInfo fsi) (.Delete fsi)))
      (.Create dir))
    (doseq [child-spec (::children spec)]
      (make-file-system (.FullName dir) child-spec))))

(defmethod make-file-system :txt [root spec]
  (ward-file-system root)
  (let [file (FileInfo. (Path/Combine root (str (::name spec) ".txt")))]
    (when (.Exists file) (. file (Delete)))
    (spit (.FullName file)
      (clojure.string/join (::contents spec) "\n"))))

(defn- setup-test-1 []
  (let [watch (start-watch test-path, 500)
        side-state (atom {:new-file-log []})
        listener (fn test-listener-1 [& args]
                   (Debug/Log "Firing test-listener-1")
                   (swap! side-state update :new-file-log conj args))]
    ((::add-listener watch) :new-file listener ::alter-children)
    (mu/lit-map watch side-state listener)))

(defn- delete-in-directory [dir]
  (let [dir (info dir)]
    (ward-file-system (.FullName dir))
    (doseq [^FileSystemInfo fsi (.GetFileSystemInfos dir)]
      (if (instance? DirectoryInfo fsi)
        (.Delete fsi true)
        (.Delete fsi)))))

(defn- correct-file-graph? [{:keys [::g] :as fg} root]
  (let [fg-traversal (tree-seq
                       (fn branch-a? [path]
                         (instance? DirectoryInfo (get-info fg path)))
                       (fn children-a [path]
                         (get-children fg path))
                       root)
        baseline-traversal (tree-seq
                             (fn branch-b? [path]
                               (instance? DirectoryInfo (info path)))
                             (fn children-b [path]
                               (map #(.FullName (info %))
                                 (.GetFileSystemInfos (info path))))
                             root)]
    ;; writing test for correct topology is a little harder since
    ;; we're using sets in file-graph
    (and 
      (= (set fg-traversal) (set baseline-traversal))
      (= (set (keys g)) (set baseline-traversal)))))

(defmacro ^:private cleanup [& body]
  `(try
     (do ~@body)
     (finally
       (kill-all-watches))))

;; hard to know how to automate this without better support for
;; printing to repl from other threads
(t/deftest create-modify-delete-test
  (let [fs1 (dir "d1"
              (txt "t1" "start\n")
              (txt "t2" "start\n"))
        interval 500
        setup (fn setup
                ([] (setup nil))
                ([& listener-specs]
                 (make-file-system test-path fs1)
                 (Thread/Sleep 200)
                 (let [watch (start-watch test-path interval)]
                   (doseq [{:keys [k f e]} listener-specs]
                     ((::add-listener watch) k f e))                   
                   watch)))]
    (t/testing "initial topology"
      (cleanup
        (let [watch (setup)]
          (t/is (correct-file-graph?
                  (::file-graph ((::state watch)))
                  test-path)))))
    (t/testing "create file"
      (cleanup
        (let [state (atom {::create []
                           ::create-modify-delete-file []})
              watch (setup
                      {:f #(swap! state update ::create conj %)
                       :e ::create
                       :k ::create}
                      {:f #(swap! state update ::create-modify-delete-file conj %)
                       :e ::create-modify-delete-file
                       :k ::create-modify-delete-file})]
          (File/WriteAllText (path-combine test-path "d1" "t3.txt")
            "start\n")
          (Thread/Sleep (* 2 interval))
          (let [s @state]
            (t/is (= (map ::path (::create s))
                    [(path-combine test-path "d1" "t3.txt")]))
            (t/is (= (map ::path (::create-modify-delete-file s))
                    [(path-combine test-path "d1" "t3.txt")]))))))))

(as/pop-assert)
