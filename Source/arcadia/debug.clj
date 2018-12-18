(ns arcadia.debug
  (:require [clojure.pprint :as pprint]
            [arcadia.internal.state :as state]
            [clojure.main :as m]
            [clojure.spec.alpha :as s]
            [arcadia.internal.socket-repl :as socket-repl]
            [arcadia.internal.thread :as thread]
            [arcadia.internal.map-utils :as mu])
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
;;   encountered during execution of Unity events are examples of this.

;; Caveats
;; - The main repl thread is currently hard-coded to be Unity's main thread.
;; - Currently no support for multiple repls, attempting to use with multiple
;;   repls could lead to deadlock or other weird behavior.

(defn current-thread []
  System.Threading.Thread/CurrentThread)

;; from https://github.com/razum2um/clj-debugger
(defn- sanitize-env
  [env]
  (into {} (for [[sym bind] env
                 :when (instance? clojure.lang.CljCompiler.Ast.LocalBinding bind)]
             [`(quote ~sym) (.Symbol bind)])))

;; system should evade STM
(defn- send! [a f & args]
  (if (clojure.lang.LockingTransaction/isRunning)
    (thread/start-thread
      #(apply send a f args))
    (apply send a f args))
  a)

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
  ;; (repl-println
  ;;   (with-out-str
  ;;     (pprint/pprint @breakpoint-registry)))
  )

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
  (send! breakpoint-registry
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

(defn exceptions [^Exception e]
  (when e (lazy-seq (cons e (exceptions (.InnerException e))))))

(defn full-exception-string [e]
  (clojure.string/join "\n--------------------\n" (exceptions e)))

(defn bpr-sync [f]
  (let [p (promise)]
    (send! breakpoint-registry
      (fn [bpr]
        (try
          (let [res (f bpr)]
            (to-outbox res p res))
          (catch Exception e
            (deliver p e)
            bpr))))
    (let [result (deref p)]
      (if (instance? Exception result)
        (do
          (spit "bpr-sync-exception.txt"
            (full-exception-string result))
          (throw
            (Exception.
              (str "Exception encountered in arcadia.debug/bpr-sync with message: " (.Message result))
              result)))
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
         (sort-by ::name)
         vec)))

(defn- fmt [fmt & args]
  (let [^String fmt fmt
        ^|System.Object[]| args (into-array System.Object (map str args))]
    (String/Format fmt args)))

(defn frame-descriptions [^System.Diagnostics.StackTrace st]
  (for [f (.GetFrames st)]
    (if-let [m (.GetMethod f)]
      (str (.ReflectedType m) "." (.Name m))
      "NO_CLASS.NO_METHOD")))

(defn table [rows]
  (let [rows (map #(map str %) rows)
        col-max (->> rows
                     (apply map list)
                     (map #(apply max (map count %))))
        format (->> col-max
                    (map-indexed (fn [i max] (str "{" i ",-" max "}")))
                    (clojure.string/join " "))]
    (->> rows
         (map #(apply fmt format %))
         (clojure.string/join "\n"))))

(defn breakpoint-summary-vec [bpr id]
  (let [{:keys [::thread ::id ::stack ::name]} (find-by-id bpr id)]
    [name (.GetHashCode thread) id (first (frame-descriptions stack))]))

(defn print-available-breakpoints [id]
  ;; print as a nice table eventually
  (let [bpr @breakpoint-registry]
    (->> (available-breakpoints id)
         (map-indexed (fn [i id] (cons i (breakpoint-summary-vec id))))
         (cons ["Inx" "Name" "Thread" "Break-ID" "Frame"])
         table
         println)))

(defn print-this [id]
  (let [bpr @breakpoint-registry]
    (->> [["Name" "Thread" "Break-ID" "Frame"]
          (breakpoint-summary-vec bpr id)]
         table
         println)))

;; won't work for n < 3
(defn trunc [s n]
  (if (< n (count s))
    (str (subs s 0 (- n 3)) "...")
    s))

(defn print-env
  ([id]
   (print-env id nil))
  ([id {:keys [short]}]
   (let [bpr @breakpoint-registry
         {:keys [::env]} (find-by-id bpr id)
         max-len 30
         rows (for [[k bind] (sort env)
                    :when (if short
                            (not (re-matches #".*__\d+.*" (str k)))
                            true)]
                (let [base-bind (binding [*print-level* 14
                                          *print-length* 4]
                                  (with-out-str
                                    (print bind)))]
                  (map #(trunc (clojure.string/trim %) max-len)
                    [(str k) (str (class bind)) base-bind])))]
     (->> rows
          (cons ["Name" "Type" "Value"])
          table
          println))))

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
  (->>
    [[":h, :help", "print help"]
     [":k, :kill", "kill this breakpoint site"]
     [":state", "print breakpoint state"]
     [":this", "print summary of this breakpoint"]
     [":a, :available", "print breakpoints available for connection by inx"]
     [":e, :env", "print environment with gensym'd locals removed"]
     [":e+, :env+", "print full environment"]
     [":q, :quit", "exit this breakpoint"]
     ["[:c <inx>]", "(as vector literal) connect to breakpoint at index <inx>"]]
    (map (fn [[a b]] [a " - " b]))
    (cons ["Command" "" "Description"])
    table
    println))

(defn repl-read-fn [id]
  (fn repl-read [request-prompt request-exit]
    (let [input (m/repl-read request-prompt request-exit)]
      (cond
        (= :h input)
        (do (print-help)
            request-prompt)

        (#{:k :kill} input)
        (let [{:keys [::site-id]} (find-by-id @breakpoint-registry id)]
          (disable-site site-id)
          (quit id)
          request-exit)

        (= :this input)
        (do (print-this id)
            request-prompt)

        (#{:e :env} input)
        (do (print-env id {:short true})
            request-prompt)

        (#{:e+ :env+} input)
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
          (connect-by-id id (nth (available-breakpoints id) i))
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
          :caught #'socket-repl/repl-caught ; preserves errors
          :prompt (if-let [[_ n] (find bp ::name)]
                    #(print (str "debug:" id ":" n "=> "))
                    #(print (str "debug:" id  "=> "))))
        (catch Exception e
          (reset! bpr-log e)
          (repl-println (str "something terrible on " id ":\n")
            (.Message e)
            "\n"
            (.StackTrace e)))
        (finally
          (repl-println "ending repl. id:" id))))))

(defn run-breakpoint-receiving-from-off-thread [id]
  (let [{:keys [::begin-connection-promise]} (find-by-id @breakpoint-registry id)]
    (deref begin-connection-promise)
    (repl-println "derefed begin-connection-promise. id:" id)
    (breakpoint-repl id)))

(defn connect-to-off-thread-breakpoint [id]
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

;; ------------------------------------------------------------
;; dsl for break macro

(s/def ::when any?)

(s/def ::break-args
  (s/cat
    :name (s/? symbol?)
    :args (s/keys* :opt-un [::when])))

(defn parse-break-args [break-args]
  (let [prs (s/conform ::break-args break-args)]
    (if (= ::s/invalid prs)
      (throw
        (Exception.
          (str "Invalid arguments to arcadia.debug/break. Spec explanation:\n"
               (with-out-str (s/explain ::break-args break-args)))))
      (let [{:keys [name args]} prs]
        (assoc args :name name)))))

(defmacro break  
  "Multithreaded breakpoint macro.

When a `(break)` form is encountered on any thread, that thread's
  execution will 'hang' as if it were running a REPL and awaiting
  further input. To connect to a breakpoint on an off-REPL thread,
  open a breakpoint in the REPL by evaluating a `(break)` form, then
  use the special syntax `[:c <index of breakpoint to connect to>]` at
  the top level of the new REPL context. For example:

```
my.namespace=> (future (let [x (+ 1 2)] (break)))

my.namespace=> (break)

;; print available breakpoints:
debug:6=> :a 
Inx Thread Break-ID
0   30     7

;; connect to the breakpoint at `Inx` 0:
debug:6=> [:c 0]
;; evaluate some code from that context:
debug:7=> x
3

;; quit this breakpoint, popping up to the previous breakpoint level:
debug:7=> :q
debug:8=>

;; pop up to top REPL level:
debug:8=> :q
my.namespace=>
```

For a summary of the in-breakpoint DSL, evaluate `:h` from within a
  breakpoint.

At the moment the `break` macro takes a single keyword-arguments
  option, `:when`. When the breakpoint is encountered, the `:when`
  expression will be evaluated if present. If the result is truthy,
  the breakpoint will be entered as normal, otherwise it will be
  skipped.

```
my.namespace=> (defn some-func [x]
                  (break :when (< x 5))
                  x)
#'arcadia.debug/some-func
my.namespace=> (some-func 7)
7
my.namespace=> (some-func 3)
debug:13=> x
3
```

`break` always returns `nil`.

This macro is intended for use with Arcadia's socket REPL. It may work
  with other REPLs in some circumstances, but probably won't for
  breakpoints triggered by Unity events that run on Unity's main
  thread. In fact, if the REPL is also evaluating on the main thread,
  such a breakpoint will almost certainly crash that REPL (since its
  thread is blocked), and effectively crash Unity itself.
"
  [& break-args]
  (let [site-id (swap! site-id-counter inc)
        {:keys [name]
         :as opts} (parse-break-args break-args)]
    `(~@(if-let [[_ w] (find opts :when)]
          `[when ~w]
          `[do])
      (when-not (site-disabled? ~site-id)
        (let [env# (capture-env)
              id# (swap! id-counter inc)
              ure# (under-repl-evaluation?)
              lower-interrupt?# (and (on-main-repl-thread?)
                                     (not (under-repl-evaluation?)))]
          (register-breakpoint
            {::id id#
             ::site-id ~site-id
             ::env env#
             ::name (quote ~name)
             ::stack (System.Diagnostics.StackTrace.)
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
            (loop []
              (when-not (should-exit? id#)
                (cond
                  (or lower-interrupt?#
                      (receiving? id#)) (run-breakpoint-receiving-from-off-thread id#)
                  (own-breakpoint? id#) (run-own-breakpoint id#)
                  :else (connect-to-off-thread-breakpoint id#))
                (recur)))
            (finally
              (repl-println "in finally quit. id:" id#)
              (quit id#)))))
      nil)))

(defn interrupt-break-fn [id]
  (fn interrupt-break []
    (binding [*interrupt* id]
      (break))))
