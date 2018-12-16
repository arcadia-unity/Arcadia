(ns arcadia.internal.socket-repl
  (:require [clojure.core.server :as s]
            [arcadia.internal.editor-callbacks :as cb]
            [arcadia.internal.config :as config]
            [clojure.main :as m]
            [arcadia.internal.state :as state]
            [arcadia.internal.stacktrace :as stacktrace]
            arcadia.data)
  (:import [System.Threading Thread]))

;; Think this sleaziness has to be a macro, for `set!`
;; Getting these from clojure.main
(def tracked-binding-symbols
  `(*ns* *warn-on-reflection* *math-context* *print-meta* *print-length* *print-level* *data-readers* *default-data-reader-fn* *compile-path* *command-line-args* *unchecked-math* *assert* *1 *2 *3 
     *e))

(defmacro set-tracked-bindings [map-expr]
  (let [m-sym (gensym "m_")
        settings (for [sym tracked-binding-symbols]
                   `(set! ~sym (get ~m-sym (var ~sym))))]
    `(let [~m-sym ~map-expr]
       ~@settings
       nil)))

(defonce interrupts
  (agent []))

(add-watch interrupts :unity-logger
  (fn [k ref old new]
    (when (not= old new)
      (UnityEngine.Debug/Log
        (str "interrupts changed."
             "\nold: " old
             "\n---------------------"
             "\nnew: " new
             "\n---------------------")))))

(defn send-interrupt [f]
  (send interrupts conj f))

(defn block-and-drain []
  (let [p (promise)]
    (send interrupts
      #(do (deliver p %)
           []))
    (deref p)))

(defn run-interrupts []
  (doseq [work (block-and-drain)]
    (work)))

;; need to take a hatchet to it
(defn data-available? [channel]
  (let [base-reader-info (.GetField (class channel)
                           "_baseReader"
                           (enum-or BindingFlags/Instance
                             BindingFlags/NonPublic))
        base-reader (.GetValue base-reader-info channel)]
    (.DataAvailable (.BaseStream base-reader))))

(defn repl-read [request-prompt request-exit]
  (let [result (try
                 (loop []
                   (if (data-available? *in*)
                     (or ({:line-start request-prompt :stream-end request-exit}
                          (m/skip-whitespace *in*))
                         (let [input (read {:read-cond :allow} *in*)]
                           (m/skip-if-eol *in*)
                           (case input
                             :repl/quit request-exit
                             input)))
                     (do
                       (run-interrupts)
                       (Thread/Sleep 200)
                       (recur))))
                 (catch Exception e
                   (println "something dreadful happened")
                   (println (.Message e))
                   (println (.StackTrace e))
                   (UnityEngine.Debug/Log "something dreadful happened")
                   (UnityEngine.Debug/Log e)
                   (throw (Exception. "wrapper" e))))]
    result))

(defn register-eval-type [x]
  (swap! state/state update :eval-fn-types (fnil conj #{}) (class x))
  x)

;; without this, symbols to lose a level of quotation,
;; such that a configuration.edn with
;; {:repl/injections (when true
;;                    (println 'sassafras))}
;; will yield an injection that tries to evaluate 'sassafras.
;; This is because we currently use edn/read-string to ingest
;; configuration.edn, rather than read-string.
(defn clean-quotes [expr]
  (read-string (pr-str expr)))

(defn game-thread-eval
  ([expr] (game-thread-eval expr nil))
  ([expr {:keys [callback-driver]
          :or {callback-driver cb/add-callback}
          :as opts}]
   (let [old-read-eval *read-eval*
         p (promise)
         injection (clean-quotes ((config/config) :repl/injections))
         expr `(do ~injection ~expr)]
     (callback-driver
       (register-eval-type
         (bound-fn []
           (deliver p ;; needs to go through agent now
             (try
               (Arcadia.Util/MarkScenesDirty)
               (let [v (eval expr)]
                 {::success true
                  ::value v
                  ::bindings (get-thread-bindings)
                  ::printed  (binding [*read-eval* old-read-eval]
                               (with-out-str
                                 (prn v)))})
               (catch Exception e
                 {::success false
                  ::value e
                  ::bindings (get-thread-bindings)}))))))
     (loop []
       (if (realized? p)
         (let [{:keys [::success ::value ::bindings ::printed]} @p]
           (set-tracked-bindings bindings)
           (if success
             (do
               ;; Simulating `print` part of `clojure.main/repl` here, because
               ;; unity prevents even printing things in the scene graph
               ;; off the main thread.

               ;; NOTE! this removes ability to customize `print` option in
               ;; repl. Suggestions for cleaner implementation welcome.
               (print printed)
               value)
             (throw (Exception. "carrier" value))))
         (do 
           (run-interrupts)
           (Thread/Sleep 100) ; yeah it's a spinlock
           (recur)))))))

;; separate function from repl-caught because this is used by NRepl.cs
(defn error-string [e]
  (let [error-opts (merge stacktrace/default-opts (:error-options (config/config)))]
    (stacktrace/exception-str e, error-opts)))

;; Our seemingly less broken variant of clojure.main/repl-caught,
;; which oddly throws away the trace
(defn repl-caught [e]
  (binding [*out* *err*]
    (println (error-string e))))

(defn repl []
  (m/repl
    :init s/repl-init
    :read #'repl-read
    :print identity
    :caught #'repl-caught
    :eval #'game-thread-eval))

(def server-defaults
  {:port 37220
   :name "default-server"
   :accept `repl})

;; see also clojure.core.server/start-servers, etc
(defn start-server
  ([] (start-server nil))
  ([opts]
   (s/start-server
     (merge server-defaults opts))))

;; ============================================================
;; control the server from config

;; we could hook this up elsewhere too

(defn server-reactive
  ([]
   (server-reactive (config/config)))
  ([{:keys [socket-repl]
     ;; socket repl on by default if we're in the editor
     :or {socket-repl Arcadia.UnityStatusHelper/IsInEditor}}]
   (cond
     socket-repl
     (let [opts (when (map? socket-repl)
                  socket-repl)]
       (start-server opts))

     (not socket-repl)
     (s/stop-servers))))

(state/add-listener ::config/on-update ::server-reactive #'server-reactive)


