(ns unity.repl
  (:refer-clojure :exclude [with-bindings])
  (:require [clojure.main :as main])
  (:import
    [UnityEngine Debug]
    [System.IO EndOfStreamException]
    [System.Collections Queue]
    [System.Net IPEndPoint IPAddress]
    [System.Net.Sockets UdpClient]
    [System.Threading Thread ThreadStart]
    [System.Text Encoding]))

(def
  ^{:doc "The form in injection is evaluated prior to each evaluation of a form in the REPL, in a try-catch."}
  injection (atom nil))

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

(defn repl-eval-print [repl-env s]
  (with-bindings repl-env
    (let [frm (read-string s)] ;; need some stuff in here about read-eval maybe
      (with-out-str
        (binding [*err* *out*] ;; not sure about this
          (prn
            (let [res (eval
                        `(do ~(when-let [inj @injection]
                                `(try ~inj (catch Exception e# e#)))
                             ~frm))]
              (update-repl-env repl-env)
              res)))))))

(def default-repl-env
  (binding [*ns* (find-ns 'user)]
    (doto (atom {}) (unity.repl/update-repl-env))))

(defn repl-eval-string
  ([s] (repl-eval-string s *out*))
  ([s out]
     (binding [*out* out]
       (repl-eval-print default-repl-env s))))

(def work-queue (Queue/Synchronized (Queue.)))
(def server-running (atom false))

(defn eval-queue []
  (while (> (.Count work-queue) 0)
    (let [[code callback-fn] (.Dequeue work-queue)
          result (try
                    (repl-eval-string code)
                  (catch EndOfStreamException e
                    "")
                  (catch Exception e
                    (str e)))]
      (callback-fn result))))


(let [bytes->str (fn [bytes]
                   (.GetString Encoding/UTF8
                               bytes
                               0
                               (.Length bytes)))
      str->bytes (fn [string]
                   (.GetBytes Encoding/UTF8
                              string))]
  (defn- listen-and-block [^UdpClient socket]
    (let [sender (IPEndPoint. IPAddress/Any 0)
          incoming-bytes (.Receive socket (by-ref sender))
          incoming-code (bytes->str incoming-bytes)]
      (.Enqueue work-queue [incoming-code
                            (fn [result]
                              (let [bytes (str->bytes (str result
                                                           "\n"
                                                           (ns-name (:*ns* @default-repl-env))
                                                           "=> "))]
                                (.Send socket
                                       bytes
                                       (.Length bytes)
                                       sender)))]))))

(defn start-server [^long port]
  (if @server-running
    (throw (Exception. "REPL Already Running"))
    (do
      (reset! server-running true)
      (let [socket (UdpClient. (IPEndPoint. IPAddress/Any port))]
        (.Start (Thread. (gen-delegate ThreadStart []
          (Debug/Log "Starting UDP REPL...")
          (while @server-running
            (listen-and-block socket))
          (Debug/Log "Stopping UDP REPL...")
          (.Close socket))))))))

(defn stop-server []
  (reset! server-running false))


(defmacro run-on-main-thread
  "Run code on unity main thread
   @TODO figure out a better namespace for this macro
   @NOTE This macro relies on eval-queue being run in main
   thread. Currently ClojureRepl.cs takes care of running eval-queue"
  [& body]
  (let [result (promise)]
    (.Enqueue work-queue
              [(pr-str (cons 'do body))
               (partial deliver result)])
    @result))
