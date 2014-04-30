(ns unityRepl
  (:refer-clojure :exclude [with-bindings])
  (:require [clojure.main :as main]))

(defn env-map []
  {:*ns* *ns*
   :*warn-on-reflection* *warn-on-reflection*
   :*math-context* *math-context*
   :*print-meta* *print-meta*
   :*print-length* *print-length*
   :*print-level* *print-level*
   :*data-readers* *data-readers*
   :*default-data-reader-fn* *default-data-reader-fn*
   ;; :*compile-path* (or (Environment/GetEnvironmentVariable "CLOJURE_COMPILE_PATH") ".") ;;;(System/getProperty "clojure.compile.path" "classes")
                    
   :*command-line-args* *command-line-args*
   :*unchecked-math* *unchecked-math*
   :*assert* *assert*
   ;;          :*1 *1
   ;;          :*2 *2
   ;;          :*3 *3
   ;;          :*e *3
   })

(defn update-repl-env [repl-env]
  (swap! repl-env #(merge % (env-map))))

(defn eval-in-ns
  ([namespace frm]
     (let [old-ns *ns*
           p (promise)]
       (in-ns namespace)
       (deliver p (eval frm))
       (in-ns (ns-name old-ns))
       @p))
  ([namespace frm repl-env]
     (let [old-ns *ns*
           p (promise)]
       (in-ns namespace)
       (deliver p (eval frm))
       (clojure.unityRepl/update-repl-env repl-env)
       (in-ns (ns-name old-ns))
       @p)))

(defmacro with-bindings
  "Executes body in the context of thread-local bindings for several vars
  that often need to be set!"
  [repl-env & body]
  `(let [re# ~repl-env]
     (when (not (instance? clojure.lang.Atom re#))
       (throw (IllegalArgumentException. "repl-env must be an atom")))
     (binding [*ns* (:*ns* @re#)
               *warn-on-reflection* (:*warn-on-reflection* @re#)
               *math-context* (:*math-context* @re#)
               *print-meta* (:*print-meta* @re#)
               *print-length* (:*print-length* @re#)
               *print-level* (:*print-level* @re#)
               *data-readers* (:*data-readers* @re#)
               *default-data-reader-fn* (:*default-data-reader-fn* @re#)
               *command-line-args* (:*command-line-args* @re#)
               *unchecked-math* (:*unchecked-math* @re#)
               *assert* (:*assert* @re#)
               ;; *compile-path* (System/getProperty "clojure.compile.path" "classes")
               ;; *1 (:*1 @re#)
               ;; *2 (:*2 @re#)
               ;; *3 (:*3 @re#)
               ;; *e (:*3 @re#)
               ]
       ~@body)))

(defn repl-eval [repl-env frm]
  (with-bindings repl-env
    (eval-in-ns
     (ns-name *ns*)
     frm
     repl-env)))

(def default-repl-env (doto (atom {}) (update-repl-env)))

;; OK so far, here's the kludgey function:

(defn repl-eval-string [s]
  ;; this probably isn't the right place for general
  ;;  exception handling, figure out something better
  @(future
     (try
       (repl-eval default-repl-env (load-string (str "'" s)))
       (catch Exception e
         ;; and this definitely isn't how to handle 
         ;; *err* (see clojure.main/repl-caught):
         (with-out-str
           (binding [*err* *out*]
             (main/repl-caught e)))))))
