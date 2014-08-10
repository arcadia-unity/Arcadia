(ns unityRepl
  (:refer-clojure :exclude [with-bindings])
  (:require [clojure.main :as main]))


(def
  ^{:doc "injections should be a collection of forms, or nil. The forms in injections are evaluated prior to each evaluation of a form in the REPL. Note that this is kind of dangerous, you can get yourself in a place that's hard to back out of."}
  injections (atom nil :validator (fn [x] (or (nil? x) (coll? x)))))

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
     (let [e# @re#]
       (binding [*ns* (:*ns* e#)
                 *warn-on-reflection* (:*warn-on-reflection* e#)
                 *math-context* (:*math-context* e#)
                 *print-meta* (:*print-meta* e#)
                 *print-length* (:*print-length* e#)
                 *print-level* (:*print-level* e#)
                 *data-readers* (:*data-readers* e#)
                 *default-data-reader-fn* (:*default-data-reader-fn* e#)
                 *command-line-args* (:*command-line-args* e#)
                 *unchecked-math* (:*unchecked-math* e#)
                 *assert* (:*assert* e#)]
         ~@body))))

(defn repl-eval-print [repl-env frm]
  (with-bindings repl-env
    (with-out-str
      (binding [*err* *out*] ;; not sure about this
        (print
          (let [res (eval
                      `(do ~(when-let [inj (seq @injections)] (cons 'do inj))
                           ~frm))]
            (update-repl-env repl-env)
            res))))))

(def default-repl-env
  (binding [*ns* (find-ns 'user)]
    (doto (atom {}) (unityRepl/update-repl-env))))

(defn repl-eval-string 
  ([s] (repl-eval-string s *out*))
  ([s out]
     (binding [*out* out]
       (repl-eval-print default-repl-env (read-string s)))))
