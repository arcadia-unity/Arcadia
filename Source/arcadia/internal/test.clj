(ns arcadia.internal.test
  (:require [clojure.test :as test]
            [clojure.spec.alpha :as s]
            [arcadia.internal.macro :as am]
            [arcadia.internal.map-utils :as mu]))

;; (defn run-tests [& args]
;;   (binding [test/*test-out* *out*]
;;     (apply test/run-tests args)))

;; ============================================================
;; async testing. just do it from scratch, clojure.test not worth it
;; get the data right and the rest will follow
;; networks of refs. must be acyclic and this must be enforced.
;; state because asynchrony.
;; multiple bits of state because we want this to be recursive
;; and non-global
;; refs because coordinated changes among multiple state sites,
;; and it's ok for those changes to be synchronous on their
;; own threads
;; need to support but not demand notion of completion

;; if we don't want to juggle a bunch of protocols etc,
;; I suppose t could just be a hyperactive function
;; closing over state that dispatches aggressively on its first
;; argument. We don't really need polymorphism right?

;; eh

;;============================================================
;; protocol

;; bit crude, but gotta start somewhere
;; can abstract this away when we know what we're doing
;; or at least hide it in some other namespace
(defprotocol ITester
  (tester? [this])
  (get-ref [this]))

;; ============================================================
;; data spec

(s/def ::type #{::result-group ::result ::tester-state})

;; in practice, :pass :fail or :error
(s/def ::status any?)

(s/def ::message string?)

(s/def ::form any?)

(s/def ::expected any?)

(s/def ::actual any?)

;; these are the maps that the individual tests should return
(s/def ::result
  (s/keys
    :req [::status]
    :opt [::form ::result ::message ::error]))

;; not a set or a map; they just pile up
(s/def ::results
  (s/every (s/or :result ::result :tester tester?) :kind vector?))

(s/def ::result-group
  (s/keys
    :req [::results]
    :opt [::label]))

(s/def ::result-groups (s/every ::result-group :kind vector?))

(s/def ::label some?)

;; forward declaration
(s/def ::tester-state nil)

(s/def ::tester-ref
  (s/and
    #(instance? clojure.lang.Ref %)
    ;; this could lead to an infinite loop on check:
    ;; #(s/valid? (s/or :tester ::tester-state :exec ::tester-exec) (deref %))
    ;; so in
    ;; we're also going to include the registry as a special optional parent
    ))

(s/def ::parents
  (s/every ::tester-ref :kind set?))

(s/def ::closed boolean?)

(s/def ::tester-state
  (s/and
    (s/keys
      :req [::parents ::children ::result-groups ::closed ::type]
      :opt [::label])
    #(= ::tester-state (::type %))))

;; ============================================================
;; tester object

(extend-protocol ITester
  System.Object
  (tester? [this]
    false)
  (get-ref [this]
    (throw
      (InvalidOperationException.
        (str "`get-ref` not implemented for instances of type " (class this))))))

(defn count-zero? [x]
  (zero? (count x)))

;; can replace this with cached, structure-sharing sets of
;; all parents and descendents on each node when we have
;; a chance
(defn- search-chain [k ref-a ref-b]
  (loop [frontier (get @ref-b k)]
    (cond
      (zero? (count frontier)) false
      (contains? frontier ref-a) true
      :else (recur (into #{} (mapcat #(get (deref %) k)) frontier)))))

;; is ref-a an ancestor of ref-b?
;; keep cycles from ever coming up, and we can have less exhaustive
;; cycle check
(defn- ancestor-of? [ref-a ref-b]
  (search-chain ::parents ref-a ref-b))

(defn- descendent-of? [ref-a ref-b]
  (search-chain ::children ref-a ref-b))

(defn- add-child-ref [parent-ref child-ref]
  (dosync
    (cond
      (= parent-ref child-ref)
      (throw
        (InvalidOperationException.
          "Cycle detected: parent-ref is child-ref"))
          
      (contains? (get @child-ref ::parents) parent-ref)
      nil ;; already there
      
      (ancestor-of? child-ref parent-ref)
      (throw
        (InvalidOperationException.
          "Cycle detected: child-ref is ancestor of parent-ref"))

      (descendent-of? parent-ref child-ref)
      (throw
        (InvalidOperationException.
          "Cycle detected: parent-ref is descendent of child-ref"))

      :else
      (do
        (alter parent-ref update ::children conj child-ref)
        (alter child-ref update ::parents conj parent-ref))))
  parent-ref)

;; tester's complete if it is closed and all of its descendents
;; are closed, can check by checking children's completion.
(defn check-complete [state-ref]
  (dosync
    (let [{:keys [::parents ::children ::closed ::complete]} @state-ref]
      (when (and (not complete) ;; no work needed
                 closed
                 (every? #(-> % deref ::complete) children))
        (alter state-ref assoc ::complete true)
        (doseq [parent parents] ;; recurse upwards
          (check-complete parent)))))
  state-ref)

(defn mark-closed [state-ref]
  (dosync
    (let [{:keys [::parents ::children]} @state-ref]
      (alter state-ref assoc ::closed true)
      (check-complete state-ref)))
  state-ref)

;; narrow rather than broad gives more latitude for
;; future api extensions
(defn valid-input? [x]
  (or (tester? x)
      (= :close x)
      (and (map? x) (= ::result (::type x)))))

(defn validate-input [x]
  (or (valid-input? x)
      (throw
        (InvalidOperationException.
          (str "Invalid input. Type of input: " (class x))))))

(defn consume-inputs [tester inputs]
  (let [state (get-ref tester)
        [label results] (if (string? (first inputs))
                          [(first inputs) (next inputs)]
                          [nil inputs])
        _   (doseq [input results]
              (validate-input input))
        closed? (some #(= :close %) inputs)
        results (vec (remove keyword? results)) ;; there for control
        children-inputs (filter tester? results)
        result-group (cond-> {::type ::result-group,
                              ::results (if (seq children-inputs)
                                          (mapv #(if (tester? %) (get-ref %) %) results)
                                          results)}
                             label (assoc ::label label))]
    (dosync
      (when (seq (::results result-group))
        (alter state update ::result-groups conj result-group))
      (doseq [x children-inputs]
        (add-child-ref state (get-ref x)))
      (when closed? (mark-closed state))
      ;; doesn't actually work, need group uids to make this robust and
      ;; then we're into the identity game:
      
      ;; {::type ::consume-return 
      ;;  ::result-group-index (-> @state ::result-groups count dec)}
      
      ;; don't know what we'll return, so empty map for now
      {})))

(defn- make-tester [state]
  (am/reify-variadic-ifn
    (applyTo [this inputs]
      (consume-inputs this inputs))
    ITester
    (tester? [this] true)
    (get-ref [this] state)))

;; label of nil is the same as no label
(defn tester
  ([] (tester nil))
  ([label]
   (-> {::type ::tester-state
        ::parents #{}
        ::children #{}
        ::result-groups []}
       (cond-> label (assoc ::label label))
       ref
       make-tester)))

(defn sub
  ([t] (sub t nil))
  ([t label]
   (let [r   (get-ref t)
         t-2 (tester label)
         r-2 (get-ref t-2)]
     (dosync
       (alter r update ::children conj r-2)
       (alter r-2 update ::parents conj r))
     t-2)))

(defn complete? [tester]
  (-> tester get-ref deref ::complete boolean))

(defn process-is [expr result message]
  {::type ::result
   ::status (if result :pass :fail)
   ::form expr
   ::result result
   ::message message})

(defn process-is-err [expr error message]
  {::type ::result
   ::status :error
   ::form expr
   ::error error
   ::message message})

(defn assert-expr-dispatch [test-expr _]
  (if (seq? test-expr)
    (first test-expr)
    :default))

(defmulti assert-expr #'assert-expr-dispatch)

(defmethod assert-expr :default [test-expr message]
  `(let [expr# (quote ~test-expr)]
     (try
       (process-is expr# ~test-expr ~message)
       (catch Exception e#
         (process-is-err expr# e# ~message)))))

(defn assert-expr-equals [test-expr message]
  (let [expr-arg-bindings (apply concat
                            (for [expr (rest test-expr)]
                              [(gensym "arg_") expr]))]
    `(let [expr# (quote ~test-expr)]
       (try
         (let [~@expr-arg-bindings
               result# (= ~@(take-nth 2 expr-arg-bindings))]
           (if result#
             (process-is expr# result# ~message)
             (assoc (process-is expr# result# ~message)
               ::actual (list '= ~@(take-nth 2 expr-arg-bindings)))))
         (catch Exception e#
           (process-is-err expr# e# ~message))))))

(defmethod assert-expr '= [test-expr message]
  (if (= #'clojure.core/= (ns-resolve *ns* '=))
    (assert-expr-equals test-expr message)
    `(let [expr# (quote ~test-expr)]
       (try
         (process-is expr# ~test-expr ~message)
         (catch Exception e#
           (process-is-err expr# e# ~message))))))

(defmethod assert-expr 'clojure.core/= [test-expr message]
   (assert-expr-equals test-expr message))

;; TODO all the cute multimethod stuff
(defmacro is
  ([test-expr]
   `(is ~test-expr nil))
  ([test-expr message]
   (assert-expr test-expr message)))

(defmacro throws
  ([test-expr argument-type message]
   `(let [expr# (quote ~test-expr)]
      (try
        (let [res# ~test-expr]
            ;; TODO: should better differentiate in the printout between
            ;; failing `is` and failing `throws`
            {::type ::result 
             ::status :fail
             ::form expr#
             ::result res#
             ::message ~message})
        (catch Exception e#
          (if (instance? ~argument-type e#)
            {::type ::result
             ::status :pass
             ::form expr#
             ::error e#
             ::message ~message}
            {::type ::result
             ::status :error
             ::form expr#
             ::error e#
             ::message ~message}))))))

;; ------------------------------------------------------------
;; extract data

;; (defmulti scrubro #'identity)

;; (defmethod scrubro :default [x]
;;   :marzipan)

(defn data-dispatch [x]
  (cond
    (instance? clojure.lang.Ref x) ::ref
    (tester? x) ::tester
    :else (::type x)))

(defmulti data-scuba #'data-dispatch)

(defmethod data-scuba :default [x]
  (throw
    (InvalidOperationException.
      (str "No method found in `data-scuba` for dispatch value. Type of argument: " (class x)))))

(defmethod data-scuba ::tester [x]
  (data-scuba (get-ref x)))

(defmethod data-scuba ::ref [x]
  (dosync (data-scuba (deref x))))

(defmethod data-scuba ::tester-exec [x]
  (dosync
    (update x ::children #(mapv data-scuba %))))

(defmethod data-scuba ::result [x]
  x)

(defmethod data-scuba ::result-group [rg]
  (dosync
    (update rg ::results #(mapv data-scuba %))))

(defmethod data-scuba ::tester-state [{:keys [::result-groups ::children] :as d}]
  (dosync
    (let [extra-children (vec (clojure.set/difference children (into #{} (mapcat ::results) result-groups)))]
      (-> d
          (update ::result-groups
            (fn [rgs]
              (->> 
                (conj rgs {::type ::result-group, ::results extra-children})
                (mapv data-scuba))))
          (dissoc ::parents ::children)))))

;; ============================================================
;; results
;; more "relational" view. Vector containing just results and "paths" to them
;; as vector of labels. Can make this a multimethod later if we want the system
;; to be more extensible.

(defn results [x & opts]
  (let [opts (into #{} opts)]
    (letfn [(step [results labels x]
              (cond
                (and (map? x) (= ::result (::type x)))
                (conj results (assoc x ::labels labels)) ;; New key, just for this
                
                (and (map? x) (#{::tester-state ::result-group ::tester-exec} (::type x)))
                (let [labels-2 (if-let [l (::label x)] (conj labels l) labels)
                      children (case (::type x)
                                 ::tester-exec  (::children x)
                                 ::tester-state (::result-groups x)
                                 ::result-group (::results x))]
                  (reduce #(step %1 labels-2 %2) results children))
                
                :else
                (throw
                  (InvalidOperationException.
                    (str "Invalid data. Type of data: " (class x))))))]
      (let [raw (step [] [] (data-scuba x))]
        (if (contains? opts :non-passing)
          (vec (remove #(= (::status %) :pass) raw))
          raw)))))

;; 

;; ;; As is, there's going to be a problem with the spec for the data
;; ;; representation.  This is a sort of stupid spec issue more than a
;; ;; real issue, but something to bear in mind as we keep going as it is
;; ;; a possible source of confusion and could muck up instrument, were
;; ;; that function to be rendered usable.

(defn print-data-dispatch [ctx data]
  (::type data))

;; drop the var
(defmulti print-data #'print-data-dispatch)

(defmethod print-data :default [ctx x]
  (throw
    (InvalidOperationException.
      (str "No dispatch value defined in print-data for this value. Type of data: " (class x)))))

(defn- ind [{:keys [indent]}]
  (let [s  (apply str (repeat indent (apply str "|" (repeat 4 " "))))]
    (fn [& xs]
      (println (apply str s xs)))))

(defmethod print-data ::result-group [{:keys [:indent] :as ctx}
                                      {:keys [::results ::label]}]
  (let [p (ind ctx)]
    (when label (p label))
    (let [ctx2 (if label (update ctx :indent + 1) ctx)]
      (doseq [r results]
        (print-data ctx2 r)))))

(defmethod print-data ::result [{:keys [:indent] :as ctx}
                                {:keys [::status
                                        ::form
                                        ::result
                                        ::message
                                        ::error
                                        ::actual]
                                 :as r}]
  (let [p (ind ctx)
        p2 (ind (update ctx :indent inc))]
    (case status
      :pass (p "PASS " message " Form: " form)
      :fail (p "FAIL " message " Expected: " form
              (if (contains? r ::actual)
                (str " Actual: "
                     (binding [*print-level* 10 *print-length* 5] ; bit arbitrary
                       (pr-str actual)))))
      ;; really should go to the simpler thing that uses `results` directly
      :error (let [[head & tail] (clojure.string/split-lines (.Message ^Exception error))
                   msg (clojure.string/join "\n"
                         (cons head
                           (for [s tail]
                             (with-out-str (p2 s)))))]
               (p
                 (if message
                   (str "ERROR: " (class error) ": " msg
                        (if (seq tail)
                          (clojure.string/trimr (with-out-str (p2 (str "in: " message " Form: " form))))
                          (str " in: " message)))
                   (str "ERROR: " (class error) ": " msg " Form: " form)))))))

(defmethod print-data ::tester-state [{:keys [:indent] :as ctx}
                                      {:keys [::result-groups ::label]}]
  (let [p (ind ctx)]
    (when label (p label))
    (let [ctx2 (if label (update ctx :indent + 1) ctx)]
      (doseq [rg result-groups]
        (print-data ctx2 rg)))))

(defmethod print-data ::tester-exec [{:keys [:indent] :as ctx}
                                     {:keys [::children] :as d}]
  (let [p (ind ctx)
        ;; gives count of distinct `::status` values across all `::result`s
        summary (-> (->> children
                         (mapcat
                           (fn [child]
                             (tree-seq
                               #(and (map? %) (#{::tester-state ::result-group} (::type %)))
                               (fn [{:keys [::type] :as d}]
                                 (case type
                                   ::tester-state (::result-groups d)
                                   ::result-group (::results d)))
                               child)))
                         (filter #(= (::type %) ::result))
                         (group-by ::status))
                    (mu/map-vals count))]
    (doseq [x children]
      (print-data {:indent 0} x))
    (println summary)))

;; ;; ------------------------------------------------------------
;; ;; deftest

;; contains test functions, not testers
(defonce ^:private test-registry (atom {}))

;; if doing redef stuff call this first in the namespace
(defn clear-registry
  ([] (clear-registry *ns*))
  ([ns]
   (swap! test-registry dissoc ns)))

(defn register-test
  "Internal, don't use"
  [ns key test]
  (swap! test-registry assoc-in [ns key] test))

;; ------------------------------------------------------------

;; We reify `exec` in `run-tests` so that this typically cyclical
;; value doesn't kill the repl if `*print-level*` is set to
;; nil. Should find a more elegant solution that doesn't gum up the
;; ITester protocol.

(deftype ResultsWrapper [results]
  Object
  (ToString [this]
    "results-wrapper"))

(defn exec-wrapper [exec]
  (ResultsWrapper. exec))

;; run tests
(defn run-tests
  ([] (run-tests *ns*))
  ([& namespaces]
   (let [reg @test-registry
         testers (->> namespaces
                      (map #(get reg %))
                      (mapcat vals)
                      (map #(%)))
         exec (ref {::type ::tester-exec
                    ::children []
                    ::closed true})
         exec-wrapped (exec-wrapper exec)]
     (dosync
       (doseq [t testers]
         (add-child-ref exec (get-ref t)))
       (check-complete exec)
       (add-watch exec :complete-test-run
         (let [out *out*] ;; maybe should do something with *test-out* here
           (fn complete-test-run [k r old new]
             (when (and (not (::complete old)) (::complete new))
               (binding [*out* out]
                 (println
                   (with-out-str
                     (println)
                     (print-data {:indent 0}
                       (data-scuba new)))))))))
       exec-wrapped))))

(defmacro deftest [name tester-sym & body]
  (assert (symbol? name))
  (assert (symbol? tester-sym))
  `(do
     (defn ~name []
       (let [~tester-sym (tester ~(clojure.core/name name))]
         ~@body
         (~tester-sym :close)
         ~tester-sym))
     (register-test *ns* ~(clojure.core/name name) (var ~name))
     (var ~name)))

