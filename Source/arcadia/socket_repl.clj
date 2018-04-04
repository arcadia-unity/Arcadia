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

(def channel-snoozle-hole
  (atom nil))

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
         ;; Wrapping in another exception is necessary because
         ;; `throw` seems to mutate stack trace of exceptions
         (throw
           (Exception. "carrier" value)))))))

;; Our seemingly less broken variant of clojure.main/repl-caught,
;; which oddly throws away the trace
(defn repl-caught [e]
  (let [ex (clojure.main/repl-exception e)
        tr (clojure.main/get-stack-trace ex)]
    (binding [*out* *err*]
      (println (str (-> ex class .Name)
                    " " (.Message ex) "\n"
                    (->> tr
                         (drop-last 6) ; trim off redundant stuff at end of stack trace. maybe bad idea
                         (map clojure.main/stack-element-str)                         
                         (clojure.string/join "\n")))))))

;; ============================================================
;; exposing client *in* *out* channels for breakpoint support

;; bit scary, reading a private var 
(defn servers []
  (var-get #'clojure.core.server/servers))

;; client-id -> {:in ..., :out ...}
(def primary-repl-channels-ref (atom {}))

;; bit sloppy for now, just gets in and out of first client on the default server
(defn primary-repl-channels []
  (let [client-id (-> (servers)
                      (get "default-server")
                      (get :sessions)
                      keys
                      first)]
    (get @primary-repl-channels-ref client-id)))

(defn gc-primary-repl-channels []
  (if-let [{:keys [sessions]} (get (servers) "default-server")]
    (swap! primary-repl-channels-ref
      (fn [prcs]
        (->> prcs
             (filter #(contains? sessions (key %)))
             (into {}))))
    (reset! primary-repl-channels {})))

(def repl-log (atom :initial))

(defn repl []
  (when-let [{:keys [server client]} s/*session*]
    (gc-primary-repl-channels)
    (reset! repl-log {:*session* s/*session*})
    (when (= server "default-server")
      (swap! primary-repl-channels-ref
        #(assoc % client {:in *in*, :out *out*}))))
  (m/repl
    :init #(do
             ;; (reset! channel-snoozle-hole
             ;;   {:in *in*
             ;;    :out *out*})
             (s/repl-init))
    :read s/repl-read
    :print identity ; effectively disabling print in `clojure.main/repl`; see `game-thread-eval`
    :caught #'repl-caught
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


