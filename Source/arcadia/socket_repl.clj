(ns arcadia.socket-repl
  (:require [clojure.core.server :as s]
            [arcadia.internal.editor-callbacks :as cb]
            [arcadia.config :as config]
            [clojure.main :as m]
            [arcadia.internal.state :as state]
            arcadia.literals)
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
  (UnityEngine.Debug/Log "starting repl-read")
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
                   (UnityEngine.Debug/Log "something dreadful happened")
                   (UnityEngine.Debug/Log e)))]
    (UnityEngine.Debug/Log
      (str "leaving repl-read, result:\n"
           (cond
             (= result request-prompt) "<request-prompt>"
             (= result request-exit) "<request-exit>"
             :else (pr-str result))))
    result))

(defn register-eval-type [x]
  (swap! state/state update :eval-fn-types (fnil conj #{}) (class x))
  x)

(defn game-thread-eval
  ([expr] (game-thread-eval expr nil))
  ([expr {:keys [callback-driver]
          :or {callback-driver cb/add-callback}
          :as opts}]
   (UnityEngine.Debug/Log "starting game-thread-eval")
   (let [old-read-eval *read-eval*
         p (promise)]
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
             (throw value)))
         (do 
           (run-interrupts)
           (Thread/Sleep 100) ; yeah it's a spinlock
           (recur)))))))

(defn repl []
  (m/repl
    :init s/repl-init
    :read #'repl-read
    :print identity
    :eval #'game-thread-eval))

(def server-defaults
  {:port 5555
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

     (= false socket-repl) ; lets think about this
     (s/stop-servers))))

(state/add-listener ::config/on-update ::server-reactive #'server-reactive)


