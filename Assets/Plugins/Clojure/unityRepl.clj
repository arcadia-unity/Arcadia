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
   :*command-line-args* *command-line-args*
   :*unchecked-math* *unchecked-math*
   :*assert* *assert*})

(defn update-repl-env [repl-env]
  (swap! repl-env #(merge % (env-map))))

(defmacro with-bindings
  "Executes body in the context of thread-local bindings for several vars
  that often need to be set!"
  [repl-env & body]
  `(let [re# ~repl-env]
     (when (not (instance? clojure.lang.Atom re#))
       (throw (ArgumentException. "repl-env must be an atom")))
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
               *assert* (:*assert* @re#)]
       ~@body)))

(defn repl-eval-print [repl-env frm]
  (with-bindings repl-env
    (with-out-str
      (print
        (let [res (eval frm)]
          (update-repl-env repl-env)
          res)))))

(def default-repl-env (doto (atom {}) (update-repl-env)))

(defn incomplete-form-exception? [x]
  (or
    (instance? System.IO.EndOfStreamException x)
    (and (instance? System.ArgumentException x)
      (re-find #"System.ArgumentException: Unmatched delimiter:"
        (.Message x)))))

(defn repl-eval-string 
  ([s] (repl-eval-string s *out*))
  ([s out]
     (binding [*out* out]
       (let [frms (try (load-string (str "'[" s "]"))
                       (catch Exception e
                         (if (incomplete-form-exception? e)
                           (throw
                             (System.IO.EndOfStreamException. "Keep typing, u krzy maven!!!"))
                           (throw e))))]
         (last
           (map #(repl-eval-print default-repl-env #)
             frms))))))
