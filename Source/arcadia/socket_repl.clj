(ns arcadia.socket-repl
  (:require [clojure.core.server :as s]
            [arcadia.internal.editor-callbacks :as cb]
            [clojure.main :as m]))

;; Think this sleaziness has to be a macro, for `set!`
;; Getting these from clojure.main
(def tracked-binding-symbols
  (set
    `(*ns* *warn-on-reflection* *math-context* *print-meta* *print-length* *print-level* *data-readers* *default-data-reader-fn* *compile-path* *command-line-args* *unchecked-math* *assert* *1 *2 *3 
       *e)))

(defmacro set-tracked-bindings [map-expr]
  (let [m-sym (gensym "m_")
        settings (for [sym tracked-binding-symbols]
                   `(set! ~sym (get ~m-sym (var ~sym))))]
    `(let [~m-sym ~map-expr]
       ~@settings
       nil)))

(defn game-thread-eval [expr]
  (let [old-read-eval *read-eval*
        p (promise)]
    (cb/add-callback
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
        (throw value)))))

(defonce print-buddy-log (atom nil))

(defn print-buddy [thing]
  (reset! print-buddy-log thing)
  nil)

(defn repl []
  (m/repl
    :init s/repl-init
    :read s/repl-read
    :print #'print-buddy
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
