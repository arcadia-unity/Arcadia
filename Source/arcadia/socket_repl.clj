(ns arcadia.socket-repl
  (:require [clojure.core.server :as s]
            [arcadia.internal.editor-callbacks :as cb]
            [arcadia.config :as config]
            [clojure.main :as m]
            [arcadia.internal.state :as state]
            arcadia.literals))

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

(defn game-thread-eval
  ([expr] (game-thread-eval expr nil))
  ([expr {:keys [callback-driver]
          :or {callback-driver cb/add-callback}
          :as opts}]
   (let [old-read-eval *read-eval*
         p (promise)]
     (callback-driver
       (bound-fn []
         (deliver p
           (try
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
                ::bindings (get-thread-bindings)}))))) 
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
         (throw value))))))

(defn repl []
  (m/repl
    :init s/repl-init
    :read s/repl-read
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
  ([{:keys [socket-repl]}]
   (cond
     socket-repl
     (let [opts (when (map? socket-repl)
                  socket-repl)]
       (start-server opts))

     (= false socket-repl) ; lets think about this
     (s/stop-servers))))

(state/add-listener ::config/on-update ::server-reactive #'server-reactive)


