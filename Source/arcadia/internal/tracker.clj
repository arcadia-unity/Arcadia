(ns arcadia.internal.tracker)

;; No step debugger, so we must make do with stuff like this.

;; Offers logging of fn var invocations. This isn't a full logging
;; library, this is a little doodad for debugging functions invoked at
;; the bottom of deep stacks.

;; Usage:

;; To track a var #'some-var, use
;; (track #'some-var)

;; If a tracked var is redef'd, its history will be reset but it will
;; remain tracked.

;; All tracked var will record the arguments and the current time for
;; each call to them in a global atom.

;; To track every public var in a namespace, use track-all.

;; To recover the total history of all tracked vars, call
;; (history).

;; To recover the history of a particular var, call
;; (var-history #'some-var).

;; The history will be returned as a sequence of maps with the
;; following keys:

;; :time <- time of the corresponding call
;; :var <- called var
;; :args <- arguments to the call

;; When a var is untracked, its history is lost.
;; To untrack a var, call (untrack #'some-var).
;; To untrack all vars, call (untrack-all).

;; To recover the last args passed to a given tracked var, use
;; (last-args #'some-var).

;; To directly apply a tracked var to its last passed args, use
;; (apply-last #'some-var).

;; To pause the tracking machinery temporarily, so that vars no longer
;; log their time and args, use with-pause. This is particularly
;; useful when replaying a var under investigation.

;; To temporarily track a var for a block of code, use with-track.

;; Behavior when combined with other wrappers such as
;; clojure.spec.test/instrument is not known right now.

(defonce tracked-vars
  (atom {}))

(defn var-symbol [var]
  (symbol
    (name (.Name (.Namespace var)))
    (name (.Symbol var))))

;; for temporarily suspending tracking, to test last failing args say
(def ^:dynamic *pause* false)

(defmacro with-pause [& body]
  `(binding [*pause* true]
     ~@body))

(defn tracking-fn [v f]
  (fn tracker [& args]
    (when-not *pause*
      (locking tracked-vars
        (swap! tracked-vars update-in [v ::history] conj
          {::time DateTime/Now
           ::args args})))
    (apply f args)))

(declare track untrack)

(defn- track-watch [key var old new]
  ;; will execute in lock, so we can ignore old and new
  (untrack var)
  (track var))

(declare tracked?)

(defn track [v]
  (assert (var? v))
  (locking tracked-vars
    (when-not (tracked? v)
      (locking v
        (remove-watch v ::track-watch)
        (alter-var-root v
          (fn [current]
            (let [{:keys [::raw ::wrapped]} (get @tracked-vars v)]
              (if-not (= wrapped current)
                (let [tracked (tracking-fn v current)]
                  ;; remember we're locking tracked-vars
                  (swap! tracked-vars assoc v
                    {::raw current
                     ::wrapped tracked
                     ::history []})
                  tracked)
                current))))
        (add-watch v ::track-watch track-watch)))
    v))

(defn track-all [ns]
  (assert (instance? clojure.lang.Namespace ns))
  (->> (vals (ns-map ns))
       (filter #(= ns (:ns (meta %))))
       (filter #(:arglists (meta %)))
       (remove #(:macro (meta %)))
       (map track)
       dorun))

(defn untrack [v]
  (assert (var? v))
  (locking tracked-vars
    (locking v
      (remove-watch v ::track-watch)
      (alter-var-root v
        (fn [current]
          (if-let [{:keys [::raw ::wrapped]} (get @tracked-vars v)]
            (do (swap! tracked-vars dissoc v)
                (if (= wrapped current)
                  raw
                  current))
            current))))
    v))

(defn untrack-all
  ([]
   (doseq [v (keys @tracked-vars)]
     (untrack v)))
  ([ns]
   (doseq [v (->> (keys @tracked-vars)
                  (filter #(= ns (:ns (meta %)))))]
     (untrack v))))

(defmacro with-track [vars & body]
  `(let [vars# ~vars]
     (doseq [v# vars#]
       (track v#))
     (try
       (do ~@body)
       (finally
         (doseq [v# vars#]
           (untrack v#))))))

(defmacro with-track-all [nss & body]
  `(let [nss# ~nss]
     (doseq [ns# nss#]
       (track-all ns#))
     (try
       (do ~@body)
       (finally
         (doseq [ns# nss#]
           (untrack-all ns#))))))

(defn history
  ([] (history ::all))
  ([vs]
   (let [trackings (if (= ::all vs)
                     @tracked-vars
                     (select-keys @tracked-vars vs))
         record-fn (fn [bldg var {:keys [::history]}]
                     (into bldg
                       (map #(assoc % ::var var))
                       history))]
     (->> (reduce-kv record-fn [] trackings)
          (sort-by ::time)))))

(defn tracked? [v]
  (contains? @tracked-vars v))

(defn print-history
  ([] (print-history ::all))
  ([vs]
   (doseq [{:keys [::time ::var]} (history vs)]
     (println
       (str (var-symbol var) " at " time)))))

(defn print-rev-history
  ([] (print-rev-history ::all))
  ([vs]
   (doseq [{:keys [::time ::var]} (reverse (history vs))]
     (println
       (str (var-symbol var) " at " time)))))

(defn var-history
  "For tracked var v, returns history of tracked calls to v.

  If v is not tracked, returns :arcadia.internal.tracker/not-tracked."
  [v]
  (assert (var? v))
  (if (tracked? v)
    (let [tvs @tracked-vars]
      (when (contains? tvs v)
        (history [v])))
    ::not-tracked))

(defn last-args
  "For tracked var v, returns collection of arguments to last tracked call to v.

  If v is not tracked, returns :arcadia.internal.tracker/not-tracked.

  If v has not been called yet while tracked, returns nil."
  [v]
  (if (tracked? v)
    (::args (last (var-history v)))
    ::not-tracked))

(defn apply-last
  "For tracked var v, apply v to the arguments of the last logged call
  to v. This call to v (and calls to v down the stack from it) will
  not be tracked.

  If v is not tracked, returns :arcadia.internal.tracker/not-tracked.

  If v has not been called yet while tracked (ie, if v has no history),
  returns :arcadia.internal.tracker/not-called."
  [v]
  (if (tracked? v)
    (if-let [args (last-args v)]
      (with-pause
        (apply @v args))
      ::not-called)
    ::not-tracked))
