(ns arcadia.debug
  (:require [clojure.pprint :as pprint]
            [clojure.main :as m]
            [clojure.spec.alpha :as s]
            [arcadia.internal.thread :as thread]))

;; ;; TODO: figure out difference between environment and context and
;; ;; stick to it

;; ;; ============================================================
;; ;; env

;; (defmacro with-context [ctx-name & body]
;;   (let [symbols (keys &env)]
;;     `(let [~ctx-name ~(zipmap
;;                         (map (fn [sym] `(quote ~sym)) symbols)
;;                         symbols)]
;;        ~@body)))

;; ;; ============================================================
;; ;; Breakpoints
;; ;; Inspired by Michael Fogus's breakpoint macro


;; ;; rather than just *can-break*, consider a way of cancelling
;; ;; all breakpoints, but all enabling or disabling all breakpoints
;; ;; by their labels, or by whether their labels match a predicate
;; (def ^:dynamic *can-break* true)

;; (defn disable-breaks []
;;   (alter-var-root #'*can-break* (constantly false)))

;; (defn enable-breaks []
;;   (alter-var-root #'*can-break* (constantly true)))

;; (def ^:dynamic *breaking* false)

;; (def ^:dynamic *env-store*)

;; (defn- contextual-eval [expr]
;;   (eval
;;     `(let [~@(mapcat
;;                (fn [k] [k `(get *env-store* (quote ~k))])
;;                (keys *env-store*))]
;;        ~expr)))

;; (def ^:dynamic *reenable-breaks*)

;; (defn- readr [prompt exit-code]
;;   (let [input (clojure.main/repl-read prompt exit-code)]
;;     (cond
;;       (#{:tl :resume} input) exit-code
;;       (= :quit input) (do (disable-breaks)
;;                           exit-code)
;;       :else input)))

;; (defn break-repl [label]
;;   (let [prompt (str "debug"
;;                     (when label (str ":" label))
;;                     "=> ")]
;;     (binding [*reenable-breaks* false]
;;       (clojure.main/repl
;;         :prompt #(print prompt)
;;         :read readr
;;         :eval contextual-eval)
;;       (when *reenable-breaks*
;;         (enable-breaks)))))

;; (defmacro break
;;   "Inserts a breakpoint, suspending other evalution and entering a
;;   subrepl with access to local environment. Exit by typing `:tl` or
;;   `:resume` in the repl prompt. If provided, `label` will
;;   show in the breakpoint repl prompt. Note that `label` is evaluated
;;   at runtime."
;;   ([] `(break nil))
;;   ([label]
;;    (let [symbols (keys &env)
;;          ns-name (ns-name *ns*)]
;;      `(when *can-break*
;;         (with-context ctx#
;;           (binding [*env-store* ctx#
;;                     *breaking* true
;;                     *ns* (find-ns (quote ~ns-name))]
;;             (break-repl ~label)))))))

;; ;; ============================================================
;; ;; Breakpoint niceties

;; (defn pareditable-print-table
;;   ([rows]
;;    (pareditable-print-table (keys (first rows)) rows))
;;   ([ks rows]
;;    (let [t (with-out-str (pprint/print-table ks rows))]
;;      (print
;;        (if (odd? (count (re-seq #"\|" t)))
;;          (str t "|")
;;          t)))))

;; (defn penv-func [env]
;;   (let [maxlen 40
;;         shorten (fn [s]
;;                   (if (< maxlen (count s))
;;                     (str (subs s 0 maxlen) "...")
;;                     s))
;;         lvs (->> (for [[k v] env
;;                        :when (not (re-matches #"fn__\d+" (str k)))] ;; perhaps filter out weird functions
;;                    {:local k
;;                     :value (shorten (pr-str v))
;;                     :class (shorten (pr-str (class v)))})
;;                  (sort-by :local))]
;;     (pareditable-print-table
;;       lvs)))

;; ;; print env (context?)
;; (defmacro penv []
;;   `(with-context env# ;; ENV OR CTX???
;;      (penv-func env#)))

;; going to start with same-thread breakpoints and build toward multithread

(defn current-thread []
  System.Threading.Thread/CurrentThread)

;; ------------------------------------------------------------
;; the store!
;; let's see how far we can get with a lockless atom
(defonce breakpoint-registry
  (atom []
    :validator (fn [bpr]
                 (cond
                   (not (vector? bpr))
                   (throw (Exception.
                            (str "breakpoint registry must be vector, instead is " bpr)))

                   (not= (count (set (map :id bpr)))
                         (count bpr))
                   (throw (Exception. "duplicate ids in breakpoint-registry!"))

                   :else true))))

(defn index-by [v pred]
  (loop [i 0]
    (when (< i (count v))
      (if (pred (get v i))
        i
        (recur (inc i))))))

;; this one isn't stupid
(defn find-by-id [bpr id]
  (first (filter #(= (:id %) id) bpr)))

(defn update-by-id [bpr id f & args]
  (if-let [i (index-by bpr #(= (:id %) id))]
    (apply update bpr i f args)
    (throw (Exception. "No breakpoint found with id " id))))

(defn available-breakpoints []
  (->> @breakpoint-registry
       (remove :connecting)
       ;; this seems janky:
       (remove #(realized? (:begin-connection-promise %)))
       vec))

(defn id-for-available-breakpoint [i]
  (let [abs (available-breakpoints)]
    (if (< i (count abs))
      (:id (get abs i))
      (throw (Exception. (str "index of breakpoint out of range (max range " (count abs) ""))))))

(defn register-breakpoint [state]
  (peek
    (swap! breakpoint-registry
      (fn [breg]
        (let [begin-connection-promise (promise)]
          (conj breg
            {:thread (:thread state)
             :id (:id state)
             :begin-connection-promise begin-connection-promise}))))))

(defn unregister-breakpoint [{:keys [id thread] :as state}]
  (swap! breakpoint-registry
    (fn [breg]
      (if-let [{:keys [begin-connection-promise]} (find-by-id breg id)]
        (do (when-not (realized? begin-connection-promise)
              (deliver begin-connection-promise
                {:exit true}))
            (vec (remove #(= (:id %) id) breg)))
        breg))))

(defn kill-all-breakpoints []
  (locking breakpoint-registry
    (doseq [{:keys [begin-connection-promise]} @breakpoint-registry]
      (deliver begin-connection-promise {:exit true}))      
    (reset! breakpoint-registry [])))

(defn renew-begin-connection-promise [{:keys [id] :as state}]
  (let [begin-connection-promise (promise)]
    (swap! breakpoint-registry update-by-id id
      (fn [bp]
        (-> bp
            (dissoc :connecting) ;; :connecting might not be needed at all
            (assoc :begin-connection-promise begin-connection-promise))))
    (assoc state :begin-connection-promise begin-connection-promise)))

;; ------------------------------------------------------------

(def breaklog
  (atom :initial))

(def exception-log (atom :initial))

;; --------------------------------------------------
;; for building, debugging
(def repl-thread)
(def repl-out)

(def verbose (atom false))

(defn repl-println [& args]
  (when @verbose
    (locking repl-out
      (binding [*out* repl-out]
        (println "------------------------------")
        (apply println args)))))

;; --------------------------------------------------

;; for now
(defn under-repl-evaluation? []
  (when-not (bound? #'repl-thread)
    (throw (Exception. "remember to bind #'repl-thread!")))
  ;; currently unsound, of course
  (identical? (current-thread) repl-thread))

(defn breakpoint-on-this-thread? [state]
  ;; here is a crux
  (or (not (:should-connect-to state))
      (let [{:keys [thread]} (find-by-id @breakpoint-registry (:should-connect-to state))]
        (= thread (current-thread)))))

(defmacro capture-env []
  (let [ks (keys &env)]
    (zipmap (for [k ks] `(quote ~k)) ks)))

(defmacro break []
  `(let [env# (capture-env)
         ure?# (under-repl-evaluation?)
         ns# (find-ns (quote ~(ns-name *ns*)))
         sentinel# (System.Object.)
         initial-state# {:env env#
                         :ns ns#
                         :id sentinel#
                         :thread (current-thread)}]
     (register-breakpoint initial-state#)
     (repl-println "entering loop")
     (try
       (loop [state# initial-state#
              bail# 0]
         (repl-println "looping. state#:\n" (with-out-str (pprint/pprint state#)))
         ;; this stuff could all be tucked away into a function rather than a macro body
         (if (< 20 bail#)
           (do (repl-println "bailing")
               state#)
           (if-not (should-exit-signal? state#)
             (if ure?#
               (if (breakpoint-on-this-thread? state#)
                 (recur (run-breakpoint-on-this-thread state#) (inc bail#))
                 (recur (connect-to-off-thread-breakpoint state#) (inc bail#)))
               (recur (run-breakpoint-receiving-from-off-thread state#) (inc bail#)))
             state#)))
       (catch Exception e#
         (repl-println (class e#) "encountered! message:" (.Message e#))
         (reset! exception-log e#))
       (finally
         (repl-println "exiting loop")
         (unregister-breakpoint initial-state#)))))

(defn should-exit-signal? [signal]
  (and (map? signal)
       (= true (get signal :exit))))

(defn should-connect-signal? [signal]
  (and (map? signal)
       (:should-connect-to signal)))

(defn process-completion-signal [state signal]
  (let [state' (cond
                 (should-exit-signal? signal)
                 (assoc state :exit true)

                 (should-connect-signal? signal)
                 (assoc state :should-connect-to (:should-connect-to signal))
                 
                 :else state) ]
    (repl-println "in process-completion-signal on thread " (current-thread)
      "\nsignal:" signal
      "\nstate:" (with-out-str (pprint/pprint state))
      "\nstate':" (with-out-str (pprint/pprint state')))
    state'))

;; bit sketchy
(def ^:dynamic *env-store*)

(defn repl-read-fn [completion-signal-ref]
  (fn repl-read [request-prompt request-exit]
    (let [input (m/repl-read request-prompt request-exit)]
      (cond
        (or (= :tl input)
            (identical? request-exit input))
        (do (repl-println "in repl-read, exit case. thread:" (current-thread)
              "\ninput:" input)
            (reset! completion-signal-ref {:exit true})
            request-exit)

        (and (vector? input)
             (#{:c :connect} (first input))
             (= 2 (count input)))
        (let [[_ i] input]
          (reset! completion-signal-ref
            {:should-connect-to (id-for-available-breakpoint i)})
          request-exit)

        :else input))))

(defn env-eval [input]
  (clojure.core/eval
    `(let [~@(mapcat
               (fn [k] [k `(get *env-store* (quote ~k))])
               (keys *env-store*))]
       ~input)))

(defn run-breakpoint-on-this-thread [state]
  (repl-println "in run-breakpoint-on-this-thread. state:"
    (with-out-str (pprint/pprint state)))
  (let [completion-signal-ref (atom :initial)]
    (binding [*env-store* (:env state)]
      (m/repl
        :read (repl-read-fn completion-signal-ref)
        :eval env-eval
        :prompt #(print "debug=> ")))
    (process-completion-signal state @completion-signal-ref)))

;; doesn't actually need to be a completion-signal ref for this one
(defn obtain-connection [state completion-signal-ref]
  (let [{:keys [should-connect-to]} state]
    (when-not should-connect-to
      (throw (Exception. "missing should-connect-to")))
    (let [bpr (swap! breakpoint-registry
                (fn [bpr]
                  ;; TODO: accommodate possibility breakpoint has been terminated in meantime
                  (update-by-id bpr should-connect-to
                    (fn [bp]
                      (if (:connecting bp)
                        (throw (Exception. "breakpoint already connected")) ; come up with something more graceful
                        (assoc bp :connecting (:thread state)))))))
          {:keys [begin-connection-promise] :as bp} (find-by-id bpr should-connect-to)]
      (if (realized? begin-connection-promise)
        (repl-println "connection-promise already realized, whole system is probably in a bad state")
        (let [end-connection-promise (promise)]
          (deliver begin-connection-promise ; here's where we make the connection
            {:end-connection-promise end-connection-promise
             :in *in*
             :out *out*})
          ;; wait for disconnect, or whatever
          (reset! completion-signal-ref @end-connection-promise))))))

(defn connect-to-off-thread-breakpoint [state]
  (repl-println "in connect-to-off-thread-breakpoint. state:\n"
    (with-out-str
      (pprint/pprint state)))
  (let [completion-signal-ref (atom :initial)]
    (obtain-connection state completion-signal-ref) ;; blocking
    (repl-println "made it past obtain-connection in connect-to-off-thread-breakpoint")
    (let [completion-signal @completion-signal-ref]
      (as-> state state
            (process-completion-signal state completion-signal)
            (if (should-exit-signal? completion-signal)
              ;; if lower breakpoint exits, should snap to own breakpoint rather than exiting
              (-> state
                  (dissoc :exit)
                  (assoc :should-connect-to (:id state)))
              state)))))

(defn await-connection-signal [{:keys [id] :as state}]
  (if-let [connp (:begin-connection-promise
                  (find-by-id @breakpoint-registry id))]
    (if (realized? connp)
      (throw (Exception. "connection promise already realized"))
      @connp)
    (throw (Exception. "no connection promise found"))))

(defn run-breakpoint-receiving-from-off-thread [state]
  (repl-println "in run-breakpoint-receiving-from-off-thread."
    "\nstate:" (with-out-str
                 (pprint/pprint state))
    "\n*ns*:" *ns*)
  (let [connection-signal (await-connection-signal state)]
    (repl-println "in run-breakpoint-receiving-from-off-thread. connection-signal:" connection-signal)
    (if (should-exit-signal? connection-signal)
      (assoc state :exit true)
      (let [completion-signal-ref (atom :initial)
            {:keys [end-connection-promise in out]} connection-signal]
        (when (or (not end-connection-promise) (realized? end-connection-promise))
          (throw (Exception. "end-connection-promise absent or realized, whole system probably borked")))
        (when (not (and in out))
          (throw (Exception. "in or out missing, whole system probably borked")))
        (binding [*env-store* (:env state)
                  *ns* (:ns state)
                  *in* in
                  *out* out]
          (m/repl
            :read (repl-read-fn completion-signal-ref)
            :eval env-eval
            :prompt #(print "debug-off-thread=> ")))
        ;; release d
        (repl-println "in run-breakpoint-receiving-from-off-thread. @completion-signal-ref:" @completion-signal-ref)
        (deliver end-connection-promise @completion-signal-ref) ;; I guessssss
        (as-> state state
              (process-completion-signal state @completion-signal-ref)
              (if (should-connect-signal? @completion-signal-ref)
                (renew-begin-connection-promise state)
                state)
              ;; kind of kludgey
              (dissoc state :should-connect-to))))))
