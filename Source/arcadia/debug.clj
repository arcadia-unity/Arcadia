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

(defn current-thread []
  System.Threading.Thread/CurrentThread)

;; from https://github.com/razum2um/clj-debugger
(defn- sanitize-env
  [env]
  (into {} (for [[sym bind] env
                 :when (instance? clojure.lang.CljCompiler.Ast.LocalBinding bind)]
             [`(quote ~sym) (.Symbol bind)])))

;; --------------------------------------------------
;; for building, debugging
(def repl-thread (current-thread))
(def repl-out *out*)

(def verbose (atom false))

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

(s/def ::site-id any?) ; not true

(s/def ::env
  (s/map-of symbol? any?))

(s/def ::breakpoint
  (s/keys
    :req [::id ::thread ::env ::in ::out ::err ::ns ::ure ::site-id]))

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
    :req [::stacks ::breakpoint-index ::outbox ::site-blacklist]))

;; ============================================================


;; ------------------------------------------------------------
;; registry

(def empty-registry
  {::stacks {}
   ::breakpoint-index {}
   ::outbox []
   ::site-blacklist #{}})

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
        {:keys [::stacks]} bpr
        {:keys [::connecting]} (find-by-id bpr id)]
    (->> (vals stacks)
         (filter #(= 1 (count %)))
         (map first)
         (map #(find-by-id bpr %))
         (remove #(and (self-connecting? bpr id)
                       (= id (::id %))))
         (remove #(= connecting (::id %)))
         (remove #(and (connecting? bpr id)
                       (not= id (::id %))))
         (remove ::connected)
         (remove ::interrupt)
         (map ::id)
         vec)))

(defn print-available-breakpoints [id]
  ;; print as a nice table eventually
  (let [bpr @breakpoint-registry]
    (repl-println "(available-breakpoints id):"
      (with-out-str
        (pprint/pprint (available-breakpoints id))))
    (println
      (clojure.string/join
        "\n"
        (->> (available-breakpoints id)
             (map #(find-by-id bpr %))
             (map-indexed (fn [i {:keys [::thread ::id]}]
                            (String/Format "{0,-3} {1, -6} {2}" i (.GetHashCode thread) id)))
             (cons (String/Format "{0,-3} {1} {2}" "Inx" "Thread" "Break-ID")))))))

;; won't work for n < 3
(defn trunc [s n]
  (if (< n (count s))
    (str (subs s 0 (- n 3)) "...")
    s))

(defn print-env [id]
  (let [bpr @breakpoint-registry
        {:keys [::env]} (find-by-id bpr id)
        max-len 30
        compr (comparator
                (fn [a b]
                  (let [ak (str (key a))
                        bk (str (key b))]
                    (< (count ak) (count bk))))) ; just sorts by length for now
        sorted-env (sort compr env)
        rows (for [[k bind] sorted-env]
               (let [base-bind (binding [*print-level* 14
                                         *print-length* 4]
                                 (with-out-str
                                   (pprint/pprint bind)))]
                 (map #(trunc (clojure.string/trim %) max-len)
                   [(str k) (str (class bind)) base-bind])))
        lengths (map
                  #(apply max (map count %))
                  (apply map list rows))
        format (apply str
                 (clojure.string/join " "
                   (map-indexed
                     (fn [i len]
                       (str "{" i ",-" len  "}"))
                     lengths)))]
    ;;sorted-env
    (doseq [[a b c] rows]
      (println (String/Format format a b c)))
    ))

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
              (manage-interrupt a))
          (-> bpr
              (reset-by-id a)
              (base-connect a id-2)
              (manage-interrupt a)))))))

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
;; sites

(defn site-disabled? [site-id]
  (contains? (::site-blacklist @breakpoint-registry) site-id))

(defn disable-site [site-id]
  (bpr-sync
    (fn [bpr]
      (update bpr ::site-blacklist conj site-id))))

(defn enable-site [site-id]
  (bpr-sync
    (fn [bpr]
      (update bpr ::site-blacklist disj site-id))))

;; ------------------------------------------------------------
;; read, eval, etc

(defn print-help []
  (println
    (clojure.string/join "\n"
      [":h, :help      - print help"
       ":k, :kill      - kill this breakpoint site"
       ":state         - print breakpoint state"
       ":a, :available - print breakpoints available for connection by inx"
       ":q, :quit      - exit this breakpoint"
       "[:c <inx>]     - (as vector literal) connect to breakpoint at index <inx>"])))

(defn repl-read-fn [id]
  (fn repl-read [request-prompt request-exit]
    (let [input (m/repl-read request-prompt request-exit)]
      (cond
        (= :h input)
        (do (print-help)
            request-prompt)

        ({:k :kill} input)
        (let [{:keys [::site-id]} (find-by-id @breakpoint-registry id)]
          (disable-site site-id)
          (quit id)
          request-exit)
        
        (= :env input)
        (do (print-env id)
            request-prompt)
        
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
  (sanitize-env &env))

(def ^:dynamic *interrupt* nil)

(declare interrupt-break-fn)

(defonce id-counter (atom 0))
(defonce site-id-counter (atom 0))

(defmacro break []
  (let [site-id (swap! site-id-counter inc)]
    `(let [id# (swap! id-counter inc)
          site-id# ~site-id
           ure# (under-repl-evaluation?)
           lower-interrupt?# (and (on-main-repl-thread?)
                                  (not (under-repl-evaluation?)))]
       (when-not (site-disabled? site-id#)
         (register-breakpoint
           {::id id#
            ::site-id site-id#
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
             (quit id#)))))))

(defn interrupt-break-fn [id]
  (fn interrupt-break []
    (binding [*interrupt* id]
      (break))))
