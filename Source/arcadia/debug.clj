(ns arcadia.debug
  (:require [clojure.pprint :as pprint]
            [arcadia.internal.state :as state]
            [clojure.main :as m]
            [clojure.spec.alpha :as s]
            [arcadia.socket-repl :as socket-repl]
            [arcadia.internal.thread :as thread])
  (:import [System.Diagnostics StackFrame StackTrace]))

;; Differs from some other implementations in that it:
;; - actually runs repl for off-thread breakpoints on their own threads,
;;   which is important in Unity.
;; - does not expose connection to off-thread breakpoints in a stack-like way:
;;   if at breakpoint A you connect to breakpoint B, then C, then disconnect
;;   from C, you return to A, not B. This prevents accumulating weird
;;   implicit paths, there's no administrative cost to jumping around between
;;   breakpoints.
;; - special breakpoint commands (connect, show available breakpoints, etc)
;;   are all expressed as top-level repl inputs with no evaluation semantics,
;;   such as `:a` and `[:c 0]`. These are only meaningful from within a breakpoint.
;; - supports interrupts for contingency that a breakpoint is hit on the
;;   main repl's eval thread, but not during a main repl eval. Breakpoints
;;   encountered during execution of Unity messages are examples of this.

;; Caveats
;; - The main repl thread is currently hard-coded to be Unity's main thread.
;; - Currently no support for multiple repls, attempting to use with multiple
;;   repls could lead to deadlock or other weird behavior.

;; (defn current-thread []
;;   System.Threading.Thread/CurrentThread)

;; ;; ------------------------------------------------------------
;; ;; the store!
;; ;; let's see how far we can get with a lockless atom
;; (defonce breakpoint-registry
;;   (atom []
;;     :validator (fn [bpr]
;;                  (cond
;;                    (not (vector? bpr))
;;                    (throw (Exception.
;;                             (str "breakpoint registry must be vector, instead is " bpr)))

;;                    (not= (count (set (map :id bpr)))
;;                          (count bpr))
;;                    (throw (Exception. "duplicate ids in breakpoint-registry!"))

;;                    :else true))))

;; (defn index-by [v pred]
;;   (loop [i 0]
;;     (when (< i (count v))
;;       (if (pred (get v i))
;;         i
;;         (recur (inc i))))))

;; ;; this one isn't stupid
;; (defn find-by-id [bpr id]
;;   (first (filter #(= (:id %) id) bpr)))

;; (defn update-by-id [bpr id f & args]
;;   (if-let [i (index-by bpr #(= (:id %) id))]
;;     (apply update bpr i f args)
;;     (throw (Exception. "No breakpoint found with id " id))))

;; (defn available-breakpoints [state]
;;   (let [bpr @breakpoint-registry
;;         own-info (find-by-id bpr (:id state))]
;;     (->> bpr
;;          (remove :connected)
;;          ;; do need this, for now. see run-own-breakpoint
;;          (remove
;;            (fn [{:keys [connecting id]}]
;;              (and connecting
;;                   (not= id (:connected own-info))))) 
;;          (remove :interrupt)  ; don't want to consider the true upper connected bp of an interrupt available
;;          (remove #(realized? (:begin-connection-promise %)))
;;          vec)))

;; (defn id-for-available-breakpoint [state i]
;;   (let [abs (available-breakpoints state)]
;;     (if (< i (count abs))
;;       (:id (get abs i))
;;       (throw (Exception. (str "index of breakpoint out of range (max range " (count abs) ""))))))

;; (defn register-breakpoint [state]
;;   (peek
;;     (swap! breakpoint-registry
;;       (fn [breg]
;;         (let [begin-connection-promise (promise)]
;;           (conj breg
;;             {:thread (:thread state)
;;              :id (:id state)
;;              :interrupt (:interrupt state)
;;              :begin-connection-promise begin-connection-promise}))))))

;; (defn unregister-breakpoint [{:keys [id thread] :as state}]
;;   (swap! breakpoint-registry
;;     (fn [breg]
;;       (if-let [{:keys [begin-connection-promise]} (find-by-id breg id)]
;;         (do (when-not (realized? begin-connection-promise)
;;               (deliver begin-connection-promise
;;                 {:exit true}))
;;             (vec (remove #(= (:id %) id) breg)))
;;         breg))))

;; (defn kill-all-breakpoints []
;;   (locking breakpoint-registry
;;     (doseq [{:keys [begin-connection-promise]} @breakpoint-registry]
;;       (deliver begin-connection-promise {:exit true}))      
;;     (reset! breakpoint-registry [])))

;; (defn renew-begin-connection-promise [{:keys [id] :as state}]
;;   (let [begin-connection-promise (promise)]
;;     (swap! breakpoint-registry update-by-id id
;;       (fn [bp]
;;         (-> bp
;;             (dissoc :connected) ;; :connecting might not be needed at all
;;             (assoc :begin-connection-promise begin-connection-promise))))
;;     (assoc state :begin-connection-promise begin-connection-promise)))

;; ;; ------------------------------------------------------------

;; (def breaklog
;;   (atom :initial))

;; (def exception-log (atom :initial))

;; ;; --------------------------------------------------
;; ;; for building, debugging
;; (def repl-thread)
;; (def repl-out)

;; (def verbose (atom false))

;; (defn repl-println [& args]
;;   (when @verbose
;;     (locking repl-out
;;       (binding [*out* repl-out]
;;         (println "------------------------------")
;;         (apply println args)))))

;; ;; --------------------------------------------------

;; ;; for now
;; (defn under-repl-evaluation? []
;;   ;; (when-not (bound? #'repl-thread)
;;   ;;   (throw (Exception. "remember to bind #'repl-thread!")))
;;   ;; ;; currently unsound, of course
;;   ;; (identical? (current-thread) repl-thread)
;;   (let [st (System.Diagnostics.StackTrace.)
;;         eval-fn-types (set (get @arcadia.internal.state/state :eval-fn-types))]
;;     (->> (.GetFrames st)
;;          (map #(.. % GetMethod DeclaringType))
;;          (some eval-fn-types)
;;          boolean)))

;; (defn own-breakpoint? [state]
;;   ;; here is a crux
;;   (and (not (:interrupt state))
;;        (or (not (:should-connect-to state))
;;            (= (:should-connect-to state)
;;               (:id state)))))

;; (defmacro capture-env []
;;   (let [ks (keys &env)]
;;     (zipmap (for [k ks] `(quote ~k)) ks)))

;; (defn on-main-repl-thread? []
;;   ;; yep
;;   (= 1 (.ManagedThreadId (current-thread))))

;; (def ^:dynamic *interrupt* nil)

;; (declare interrupt-break-fn
;;   should-exit-signal?
;;   run-own-breakpoint
;;   connect-to-off-thread-breakpoint
;;   run-breakpoint-receiving-from-off-thread)

;; ;; Using dynamic var *interrupt* _and_ macro param
;; ;; to minimize chance of somehow capturing *interrupt*
;; ;; mistakenly (ie with bound-fn). maybe a bit paranoid
;; (defmacro break [& {:keys [interrupt]}]
;;   `(let [env# (capture-env)
;;          ure?# (under-repl-evaluation?)
;;          ns# (find-ns (quote ~(ns-name *ns*)))
;;          sentinel# (System.Object.)
;;          initial-state# {:env env#
;;                          :ns ns#
;;                          :interrupt ~(when interrupt `*interrupt*)
;;                          :id sentinel#
;;                          :thread (current-thread)}]
;;      (register-breakpoint initial-state#)
;;      ;; Emit and register an interrupt if we need to
;;      (when (and (on-main-repl-thread?)
;;                 (not (under-repl-evaluation?)))
;;        (socket-repl/send-interrupt
;;          (interrupt-break-fn sentinel#)))
;;      (repl-println "entering loop")
;;      (try
;;        (loop [state# initial-state#]
;;          (repl-println "looping. state#:\n" (with-out-str (pprint/pprint state#)))
;;          ;; this stuff could all be tucked away into a function rather than a macro body
;;          (if-not (should-exit-signal? state#)
;;            (if (or ure?# (:interrupt state#))
;;              (if (own-breakpoint? state#)
;;                (recur (run-own-breakpoint state#))
;;                (recur (connect-to-off-thread-breakpoint state#)))
;;              (recur (run-breakpoint-receiving-from-off-thread state#)))
;;            state#))
;;        (catch Exception e#
;;          (repl-println (class e#) "encountered on thread " (current-thread) "! message:" (.Message e#)
;;            "\n" (.StackTrace e#))
;;          (reset! exception-log e#))
;;        (finally
;;          (repl-println "exiting loop")
;;          (unregister-breakpoint initial-state#)))))

;; (defn interrupt-break-fn [sentinel]
;;   (fn interrupt-break []
;;     (binding [*interrupt* sentinel]
;;       (break :interrupt true))))

;; (defn should-exit-signal? [signal]
;;   (and (map? signal)
;;        (= true (get signal :exit))))

;; (defn should-connect-signal? [signal]
;;   (and (map? signal)
;;        (:should-connect-to signal)))

;; (defn process-completion-signal [state signal]
;;   (let [state' (cond
;;                  (should-exit-signal? signal)
;;                  (assoc state :exit true)

;;                  (should-connect-signal? signal)
;;                  (assoc state :should-connect-to (:should-connect-to signal))
                 
;;                  :else state) ]
;;     (repl-println "in process-completion-signal on thread " (current-thread)
;;       "\nsignal:" signal
;;       "\nstate:" (with-out-str (pprint/pprint state))
;;       "\nstate':" (with-out-str (pprint/pprint state')))
;;     state'))

;; ;; bit sketchy
;; (def ^:dynamic *env-store*)

;; (defn trunc [s n]
;;   (if (< n (count s))
;;     (subs s 0 n)
;;     s))

;; (defn print-available-breakpoints [state]
;;   ;; print as a nice table eventually
;;   (println
;;     (clojure.string/join
;;       "\n"
;;       (->> (available-breakpoints state)
;;            (map-indexed (fn [i {:keys [thread]}]
;;                           (String/Format "[{0,-3}] {1}" i (.GetHashCode thread))))
;;            (cons (String/Format "{0,-5} {1}" "Inx" "Thread"))))))

;; (defn repl-read-fn [state completion-signal-ref]
;;   (fn repl-read [request-prompt request-exit]
;;     (let [input (m/repl-read request-prompt request-exit)]
;;       (cond
;;         (= :state input)
;;         (do (pprint/pprint state)
;;             request-prompt)
        
;;         (#{:a :available} input)
;;         (do (print-available-breakpoints state)
;;             request-prompt)
        
;;         (or (= :tl input)
;;             (identical? request-exit input))
;;         (do (repl-println "in repl-read, exit case. thread:" (current-thread)
;;               "\ninput:" input)
;;             (reset! completion-signal-ref {:exit true})
;;             request-exit)

;;         (and (vector? input)
;;              (#{:c :connect} (first input))
;;              (= 2 (count input)))
;;         (let [[_ i] input]
;;           (reset! completion-signal-ref
;;             {:should-connect-to (id-for-available-breakpoint state i)})
;;           request-exit)

;;         :else input))))

;; (defn env-eval [input]
;;   (clojure.core/eval
;;     `(let [~@(mapcat
;;                (fn [k] [k `(get *env-store* (quote ~k))])
;;                (keys *env-store*))]
;;        ~input)))

;; (swap! state/state update :eval-fn-types (fnil conj #{}) (class env-eval))

;; (defn run-own-breakpoint [state]
;;   (repl-println "in run-own-breakpoint. state:"
;;     (with-out-str (pprint/pprint state)))
;;   (swap! breakpoint-registry update-by-id (:id state)
;;     assoc :connecting (:id state))  
;;   (let [completion-signal-ref (atom :initial)]
;;     (binding [*env-store* (:env state)]
;;       (m/repl
;;         :read (repl-read-fn state completion-signal-ref)
;;         :eval env-eval
;;         :prompt #(print "debug=> ")))
;;     (swap! breakpoint-registry update-by-id (:id state)
;;       dissoc :connecting)
;;     (process-completion-signal state @completion-signal-ref)))

;; ;; doesn't actually need to be a completion-signal ref for this one
;; (defn obtain-connection [state completion-signal-ref]
;;   (let [should-connect-to (or (:should-connect-to state) (:interrupt state))]
;;     (when-not should-connect-to
;;       (throw (Exception. "missing should-connect-to")))
;;     (swap! breakpoint-registry update-by-id (:id state)
;;       assoc :connecting should-connect-to)    
;;     (let [bpr (swap! breakpoint-registry
;;                 (fn [bpr]
;;                   ;; TODO: accommodate possibility breakpoint has been terminated in meantime
;;                   (update-by-id bpr should-connect-to
;;                     (fn [bp]
;;                       (if (:connecting bp)
;;                         (throw (Exception. "breakpoint already connected")) ; come up with something more graceful
;;                         (assoc bp :connected (:id state)))))))
;;           {:keys [begin-connection-promise] :as bp} (find-by-id bpr should-connect-to)]
;;       (if (realized? begin-connection-promise)
;;         (repl-println "connection-promise already realized, whole system is probably in a bad state")
;;         (let [end-connection-promise (promise)]
;;           (deliver begin-connection-promise ; here's where we make the connection
;;             {:end-connection-promise end-connection-promise
;;              :in *in*
;;              :err *err*
;;              :out *out*})
;;           ;; wait for disconnect, or whatever
;;           (reset! completion-signal-ref @end-connection-promise))))))

;; (defn connect-to-off-thread-breakpoint [state]
;;   (repl-println "in connect-to-off-thread-breakpoint. state:\n"
;;     (with-out-str
;;       (pprint/pprint state)))
;;   (let [completion-signal-ref (atom :initial)]
;;     (obtain-connection state completion-signal-ref) ;; blocking
;;     (repl-println "made it past obtain-connection in connect-to-off-thread-breakpoint")
;;     (let [completion-signal @completion-signal-ref]
;;       (as-> state state
;;             (process-completion-signal state completion-signal)
;;             (if (should-exit-signal? completion-signal)
;;               (if-let [interrupt (:interrupt state)]
;;                 ;; If we are connected to the interrupt bp, we should behave as if
;;                 ;; we are connected to our own bp. If there *is* an interrupt bp and
;;                 ;; we are not connected to it, we should snap to that.
;;                 (if (or (not (:should-connect-to state))
;;                         (= (:should-connect-to state) interrupt))
;;                   (do
;;                     (repl-println "down this branch. state:"
;;                       "\n" (with-out-str (pprint/pprint state)))
;;                     state) ; default behavior is just to exit, so we're good
;;                   (-> state ; otherwise connect to the interrupt
;;                       (dissoc :exit)
;;                       (assoc :should-connect-to interrupt)))
;;                 ;; Otherwise, if lower breakpoint exits, should snap to own breakpoint rather than exiting
;;                 (-> state
;;                     (dissoc :exit)
;;                     (assoc :should-connect-to (:id state))))
;;               state)))))

;; (defn await-connection-signal [{:keys [id] :as state}]
;;   (if-let [connp (:begin-connection-promise
;;                   (find-by-id @breakpoint-registry id))]
;;     ;; (if (realized? connp)
;;     ;;   (throw (Exception. "connection promise already realized"))
;;     ;;   @connp)
;;     @connp
;;     (throw (Exception. "no connection promise found"))))

;; (defn run-breakpoint-receiving-from-off-thread [state]
;;   (repl-println "in run-breakpoint-receiving-from-off-thread."
;;     "\nstate:" (with-out-str
;;                  (pprint/pprint state))
;;     "\n*ns*:" *ns*)
;;   (let [connection-signal (await-connection-signal state)]
;;     (repl-println "in run-breakpoint-receiving-from-off-thread. connection-signal:" connection-signal)
;;     (if (should-exit-signal? connection-signal)
;;       (assoc state :exit true)
;;       (let [completion-signal-ref (atom :initial)
;;             {:keys [end-connection-promise in out err]} connection-signal]
;;         (when (or (not end-connection-promise) (realized? end-connection-promise))
;;           (throw (Exception. "end-connection-promise absent or realized, whole system probably borked")))
;;         (when (not (and in out))
;;           (throw (Exception. "in or out missing, whole system probably borked")))
;;         (binding [*env-store* (:env state)
;;                   *ns* (:ns state)
;;                   *in* in
;;                   *err* err
;;                   *out* out]
;;           (m/repl
;;             :read (repl-read-fn state completion-signal-ref)
;;             :eval env-eval
;;             :prompt #(print "debug-off-thread=> ")))
;;         ;; release d
;;         (repl-println "in run-breakpoint-receiving-from-off-thread. @completion-signal-ref:" @completion-signal-ref)
;;         (deliver end-connection-promise @completion-signal-ref) ;; I guessssss
;;         (as-> state state
;;               (process-completion-signal state @completion-signal-ref)
;;               (if (should-connect-signal? @completion-signal-ref) ; ie, we're just shifting over
;;                 (renew-begin-connection-promise state)
;;                 state)
;;               ;; a receiving bp *never* connects directly to another
;;               (dissoc state :should-connect-to))))))

(defn current-thread []
  System.Threading.Thread/CurrentThread)

;; --------------------------------------------------
;; for building, debugging
(def repl-thread (current-thread))
(def repl-out *out*)

(def verbose (atom true))

(defn repl-println [& args]
  (when @verbose
    (locking repl-out
      (binding [*out* repl-out]
        (println "------------------------------")
        (apply println args)))))

(declare breakpoint-registry)

(defn pbpr []
  (repl-println
    (with-out-str
      (pprint/pprint @breakpoint-registry))))

;; ------------------------------------------------------------

(s/def ::in any?) ; not true

(s/def ::out any?) ; not true

(s/def ::err any?) ; not true

(s/def ::ns any?) ; not true

(s/def ::ure boolean?)

(s/def ::env
  (s/map-of symbol? any?))

(s/def ::breakpoint
  (s/keys
    :req [::id ::thread ::env ::in ::out ::err ::ns ::ure]))

(s/def ::breakpoint-index
  (s/map-of ::id ::breakpoint))

(s/def ::breakpoints
  (s/coll-of ::breakpoint :kind vector?))

(s/def ::id
  #(nil? (ancestors (class %))))

(s/def ::thread
  #(instance? System.Threading.Thread))

(s/def ::stacks
  (s/map-of
    ::thread
    (s/coll-of ::id :kind vector?)))

(s/def ::promise
  #(instance? clojure.lang.IBlockingDeref %))

(s/def ::payload
  any?)

(s/def ::promise-to-keep
  (s/keys
    :req [::promise ::payload]))

(s/def ::outbox
  (s/coll-of ::promise-to-keep :kind vector?))

(s/def ::breakpoint-registry
  (s/keys
    :req [::stacks ::breakpoint-index ::outbox]))

;; ============================================================


;; ------------------------------------------------------------
;; registry

(def empty-registry
  {::stacks {}
   ::breakpoint-index {}
   ::outbox []})

(defonce breakpoint-registry
  (agent empty-registry))

(defn drain-outbox-async []
  (send breakpoint-registry
    (fn [{:keys [::outbox] :as bpr}]
      (doseq [{:keys [::promise ::payload]} outbox]
        (deliver promise payload))
      (assoc bpr ::outbox []))))

(add-watch breakpoint-registry ::drain-outbox
  (fn [k ref old new]
    (when-not (zero? (count (::outbox new)))
      (drain-outbox-async))))

(defn to-outbox [bpr p v]
  (update bpr ::outbox conj
    {::promise p
     ::payload v}))

(defn bpr-sync [f]
  (let [p (promise)]
    (send breakpoint-registry
      (fn [bpr]
        (try
          (let [res (f bpr)]
            (to-outbox res p res))
          (catch Exception e
            (deliver p e)
            bpr))))
    (let [result (deref p)]
      (if (instance? Exception result)
        (throw (Exception. "Exception Encountered" result))
        result))))

;; ------------------------------------------------------------
;; basic access

(defn find-by-id [bpr id]
  (-> bpr (get ::breakpoint-index) (get id)))

(defn update-by-id [bpr id f & args]
  (apply update-in bpr [::breakpoint-index id] f args))

;; promise cleanup should be handled elsewhere
(defn remove-by-id [bpr id]
  (repl-println "in remove-by-id. id:" id)
  (let [{:keys [::thread]} (find-by-id bpr id)]
    (-> bpr
        (update ::breakpoint-index dissoc id)
        (update ::stacks
          (fn [stacks]
            (let [stack (->> stacks
                              (get thread)
                              (remove #{id})
                              seq)]
              (if stack
                (assoc stacks thread (vec stack))
                (dissoc stacks thread))))))))

;; ------------------------------------------------------------

(defn connected? [bpr id]
  (boolean (::connected (find-by-id bpr id))))

(defn connecting? [bpr id]
  (boolean (::connecting (find-by-id bpr id))))

(defn self-connecting? [bpr id]
  (not (or (connected? bpr id) (connecting? bpr id))))

;; ------------------------------------------------------------

(defn available-breakpoints [id]
  (let [bpr @breakpoint-registry
        {::keys [::stacks]} bpr
        {:keys [::connecting]} (find-by-id bpr id)]
    (->> (vals stacks)
         (filter #(= 1 (count %)))
         (map first)
         (map #(find-by-id bpr %))
         (remove #(and (self-connecting? bpr id)
                       (= id (::id %))))
         (remove #(= connecting (::id %)))
         (remove ::connected)
         (remove ::interrupt)
         (map ::id)
         vec)))

(defn print-available-breakpoints [id]
  ;; print as a nice table eventually
  (repl-println "trying this")
  (let [bpr @breakpoint-registry]
    (repl-println "(available-breakpoints id):"
      (with-out-str
        (pprint/pprint (available-breakpoints id))))
    (println
      (clojure.string/join
        "\n"
        (->> (available-breakpoints id)
             (map #(find-by-id bpr %))
             (map-indexed (fn [i {:keys [::thread]}]
                            (String/Format "[{0,-3}] {1}" i (.GetHashCode thread))))
             (cons (String/Format "{0,-5} {1}" "Inx" "Thread")))))))

;; ------------------------------------------------------------

(declare manage-interrupt)

(defn register-breakpoint [{:keys [::id ::thread] :as info}]
  (bpr-sync
    (fn [bpr]
      (-> bpr
          (update ::breakpoint-index assoc id
            (assoc info ::begin-connection-promise (promise)))
          (update ::stacks update thread (fnil conj []) id)
          (manage-interrupt id)))))

(defn reset-by-id
  ([bpr id]
   (letfn [(reset [bpr id]
             (if (find-by-id bpr id)
               (update-by-id bpr id
                 #(-> %
                      (dissoc ::connected ::connecting ::end-connection-promise)
                      (assoc ::begin-connection-promise (promise))))
               bpr))]
     (cond
       (not (find-by-id bpr id))
       bpr
       
       (connected? bpr id)
       (let [{:keys [::connected]} (find-by-id bpr id)
             {:keys [::end-connection-promise]} (find-by-id bpr connected)]
         (-> bpr
             (reset id)
             (reset connected)
             (to-outbox end-connection-promise true)))

       (connecting? bpr id)
       (let [{:keys [::connecting
                     ::end-connection-promise]} (find-by-id bpr id)]
         (-> bpr
             (reset id)
             (reset connecting)
             (to-outbox end-connection-promise true)))

       :else
       (-> bpr (reset id)))))
  ([bpr id & ids]
   (reduce reset-by-id bpr (cons id ids))))

;; bp-2 is expected to be hanging on its begin-connection-promise, 
;; waiting for a connection, and as soon as this transaction completes
;; bp-1 is expected to start hanging on its new end-connection-promise.
;; We should never find ourselves in a situation where an interrupt bp
;; is trying to connect to itself.
(defn base-connect [bpr id-1 id-2]
  (repl-println "in base-connect. id-1:" id-1 "; id-2:" id-2)
  (let [{:keys [::begin-connection-promise]} (find-by-id bpr id-2)]
    (-> bpr
        (update-by-id id-1
          (fn [{:keys [::end-connection-promise] :as bp}]
            (assoc bp
              ::connecting id-2
              ::end-connection-promise (promise))))
        (update-by-id id-2
          #(-> %
               (assoc ::connected id-1)
               (dissoc ::begin-connection-promise)))
        (to-outbox begin-connection-promise true))))

(defn manage-interrupt [bpr id]
  (repl-println "in manage-interrupt. id:" id "\n"
    (with-out-str (pprint/pprint @breakpoint-registry)))
  (let [{:keys [::interrupt
                ::connected
                ::connecting]} (find-by-id bpr id)]
    (if (and interrupt
             (not (or connected connecting))) ; should have this as predicate elsewhere
      (if (find-by-id bpr interrupt)
        (base-connect bpr id interrupt) ; default to connecting to interrupt if it's there
        (remove-by-id bpr id)) ; if the interrupt's gone, so are we
      bpr)))

(defn connect-by-id [id-1 id-2]
  (repl-println "in connect-by-id. id-1:" id-1 "; id-2:" id-2)
  (bpr-sync
    (fn [bpr]
      (let [{:keys [::connected]} (find-by-id bpr id-1)
            a (or connected id-1)]
        (if (= a id-2)
          (-> bpr
              (reset-by-id a) ; will also reset id-1 if connected
              (manage-interrupt a)
              )
          (-> bpr
              (reset-by-id a)
              (base-connect a id-2)
              (manage-interrupt a)))))))

;; from below
;; disconnect but keep spinning
(defn disconnect [id]
  (bpr-sync
    (fn [bpr]
      (let [{:keys [::connected]} (find-by-id bpr id)
            {:keys [::end-connection-promise]} (find-by-id bpr connected)]
        (-> bpr
            (reset-by-id id connected)
            (to-outbox end-connection-promise true))))))

(defn quit [id]
  (repl-println "in quit. id:" id)
  (bpr-sync
    (fn [bpr]
      (if (find-by-id bpr id)
        (cond
          (connected? bpr id)
          (let [{:keys [::connected]} (find-by-id bpr id)
                {:keys [::end-connection-promise]} (find-by-id bpr connected)]
            (-> bpr
                (reset-by-id connected)
                (remove-by-id id)
                (manage-interrupt connected)
                (to-outbox end-connection-promise true)))

          (connecting? bpr id) ; this shouldn't usually happen
          (let [{:keys [::end-connection-promise ::connecting]} (find-by-id bpr id)]
            (-> bpr
                (reset-by-id connecting)
                (remove-by-id id)
                (to-outbox end-connection-promise true)))

          :else
          (-> bpr
              (remove-by-id id)))
        bpr))))

;; good enough for now
(defn kill-all-breakpoints []
  (loop []
    (let [{:keys [::breakpoint-index]} @breakpoint-registry]
      (when-first [id (keys breakpoint-index)]
        (quit id)
        (recur)))))

;; ------------------------------------------------------------
;; read, eval, etc

(defn repl-read-fn [id]
  (fn repl-read [request-prompt request-exit]
    (let [input (m/repl-read request-prompt request-exit)]
      ;; todo: :help, :h

      ;; todo: disable all breakpoints that are instances of
      ;; this code-site. need code-site specific ids
      (cond
        (= :state input)
        (do (pprint/pprint (find-by-id @breakpoint-registry id))
            request-prompt)

        (#{:a :available} input)
        (do (print-available-breakpoints id)
            request-prompt)

        (#{:q :quit request-exit} input)
        (do (quit id)
            request-exit)

        (and (vector? input)
             (#{:c :connect} (first input))
             (= 2 (count input)))
        (let [[_ i] input]
          (connect-by-id id (get (available-breakpoints id) i))
          request-exit)

        :else input))))

(def ^:dynamic *env-store* {})

(defn env-eval [input]
  (clojure.core/eval
    `(let [~@(mapcat
               (fn [k] [k `(get *env-store* (quote ~k))])
               (keys *env-store*))]
       ~input)))

(swap! state/state update :eval-fn-types (fnil conj #{}) (class env-eval))

;; ------------------------------------------------------------
;; control predicates

(defn should-exit? [id]
  (not (find-by-id @breakpoint-registry id)))

(defn own-breakpoint? [id]
  (let [bp (find-by-id @breakpoint-registry id)]
    (and
      (not (or (::connected bp) (::connecting bp)))
      (::ure bp))))

(defn receiving? [id]
  (let [{:keys [::ure ::interrupt]} (find-by-id @breakpoint-registry id)]
    (not (or ure interrupt))))

(defn under-repl-evaluation? []
  (let [st (System.Diagnostics.StackTrace.)
        eval-fn-types (set (get @arcadia.internal.state/state :eval-fn-types))]
    (->> (.GetFrames st)
         (map #(.. % GetMethod DeclaringType))
         (some eval-fn-types)
         boolean)))

(defn on-main-repl-thread? []
  ;; yep
  (= 1 (.ManagedThreadId (current-thread))))

;; ------------------------------------------------------------
;; the macro

(def bpr-log (atom :initial))

(defn breakpoint-repl [id]
  (let [bpr @breakpoint-registry
        {:keys [::env  ::ns ::connected] :as bp} (find-by-id bpr id)
        {:keys [::in ::out ::err]} (if connected
                                     (find-by-id bpr connected)
                                     bp)]
    (binding [*env-store* env
              *ns* ns
              *in* in
              *out* out
              *err* err]
      (repl-println "beginning repl. id:" id)
      (try
        (m/repl
          :read (repl-read-fn id)
          :eval #'env-eval
          :prompt #(print (str "debug:" id  "=> ")))
        (catch Exception e
          (reset! bpr-log e)
          (repl-println (str "something terrible on " id ":\n")
            (.Message e)
            "\n"
            (.StackTrace e)))
        (finally
          (repl-println "ending repl. id:" id))))))

(defn run-breakpoint-receiving-from-off-thread [id]
  (repl-println "in run-breakpoint-receiving-from-off-thread. id:" id)
  (pbpr)
  (let [{:keys [::begin-connection-promise]} (find-by-id @breakpoint-registry id)]
    (deref begin-connection-promise)
    (repl-println "derefed begin-connection-promise. id:" id)
    (breakpoint-repl id)))

(defn connect-to-off-thread-breakpoint [id]
  (repl-println "in connect-to-off-thread-breakpoint. id:" id)
  (pbpr)
  (let [{:keys [::end-connection-promise] :as bp} (find-by-id @breakpoint-registry id)]
    (deref end-connection-promise)
    (repl-println "derefed end-connection-promise. id:" id)))

(defn run-own-breakpoint [id]
  (repl-println "in run-own-breakpoint. id:" id)
  (breakpoint-repl id))

(defmacro capture-env []
  (let [ks (keys &env)]
    (zipmap (for [k ks] `(quote ~k)) ks)))

(def ^:dynamic *interrupt* nil)

(declare interrupt-break-fn)

(defonce id-counter (atom 0))

(defmacro break []
  `(let [id# (swap! id-counter inc)
         ure# (under-repl-evaluation?)
         lower-interrupt?# (and (on-main-repl-thread?)
                                (not (under-repl-evaluation?)))]
     (register-breakpoint
       {::id id#
        ::env (capture-env)
        ::thread System.Threading.Thread/CurrentThread
        ::in *in*
        ::out *out*
        ::err *err*
        ::ure ure#
        ::interrupt *interrupt*
        ::ns (find-ns (quote ~(ns-name *ns*)))})
     (when lower-interrupt?#
       (socket-repl/send-interrupt
         (interrupt-break-fn id#)))
     (repl-println "starting loop. id:" id#)
     (try
       (loop [bail# 0]
         (if (< bail# 10)
           (when-not (should-exit? id#)
             (cond
               (or lower-interrupt?#
                   (receiving? id#)) (run-breakpoint-receiving-from-off-thread id#)
               (own-breakpoint? id#) (run-own-breakpoint id#)
               :else (connect-to-off-thread-breakpoint id#))
             (recur (inc bail#)))
           (repl-println "bailing.")))
       (finally
         (repl-println "in finally quit. id:" id#)
         (quit id#)))))

(defn interrupt-break-fn [id]
  (fn interrupt-break []
    (binding [*interrupt* id]
      (break))))
