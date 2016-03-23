(ns arcadia.repl
  (:refer-clojure :exclude [with-bindings])
  (:require [clojure.main :as main]
            [arcadia.config :refer [configuration]])
  (:import
    [UnityEngine Debug]
    [System.IO EndOfStreamException]
    [System.Collections Queue]
    [System.Net IPEndPoint IPAddress]
    [System.Net.Sockets UdpClient SocketException]
    [System.Threading Thread ThreadStart]
    [System.Text Encoding]))

;; ============================================================
;; macros

(defmacro with-packed-bindings [pack dynsyms & body]
  (assert (every? symbol? dynsyms))
  (let [packsym (gensym "env__")
        bndgs (->> dynsyms
                (mapcat
                  (fn [k] [k `(get ~packsym ~(keyword (name k)) ~k)]))
                vec)]
    `(let [~packsym ~pack]
       (binding ~bndgs
         ~@body))))

(defmacro pack-bindings [binding-symbols]
  (assert (every? symbol? binding-symbols))
  (zipmap
    (map #(keyword (name %)) binding-symbols)
    binding-symbols))

(defonce env-binding-symbols
  `[*ns*
    *math-context*
    *print-meta* 
    *print-length*
    *print-level* 
    *data-readers*
    *default-data-reader-fn*
    *command-line-args*
    *assert*])

(defmacro with-env-bindings
  "Executes body in the context of thread-local bindings for several vars
  that often need to be set!"
  [repl-env & body]
  `(with-packed-bindings ~repl-env ~env-binding-symbols
     ~@body))

(defmacro pack-env-bindings []
  `(pack-bindings ~env-binding-symbols))

(defonce config-binding-symbols
  `[*ns*
    ~'*debug* ;; is this namespace qualified or not?
    *warn-on-reflection*
    *unchecked-math*])

(defmacro with-config-compiler-bindings [& body]
  `(with-packed-bindings (:compiler @configuration) ~config-binding-symbols
     ~@body))

(defmacro pack-config-bindings []
  `(pack-bindings ~config-binding-symbols))

;; ============================================================
;; state

(def ip-map
  (atom {}))

;; ============================================================
;; details

;; not AT ALL sure we should be trumping these dynamic vars with configuration
;; in fact not sure why we are at all
(defn env-map []
  (pack-env-bindings))

(defn eval-to-string [frm]
  (with-out-str
    (binding [*err* *out*]
      (prn
        (eval
          `(do
             ~(when-let [inj (read-string
                               (pr-str (@configuration :injections)))]
                `(try
                   ~inj
                   (catch Exception e#
                     (Debug/Log (str e#)))))
             ~frm))))))

 ; need some stuff in here about read-eval maybe
(defn repl-eval-print [repl-env s]
  (with-config-compiler-bindings ; maybe... very dubious about the wisdom of this
    (with-env-bindings repl-env
        (let [frm (read-string s)]
          {:result (try
                     (eval-to-string frm)
                     (catch Exception e
                       (str e)))
           :env (env-map)}))))

(def work-queue (Queue/Synchronized (Queue.)))

(def server-running (atom false))

(defn byte-str [& xs]
  (.GetBytes Encoding/UTF8 (apply str xs)))

(defn eval-queue []
  (while (> (.Count work-queue) 0)
    (let [ip-map* @ip-map]
      (try
        (let [[code socket destination] (.Dequeue work-queue)    ; get work from queue
              env1 (or (ip-map* destination)                     ; lookup env for destination ip
                     (assoc (env-map) :*ns* (find-ns 'user)))    ; default to user
              {:keys [result], env2 :env} (binding [*out* *out*] ; guard from downstream bindings
                                            (repl-eval-print env1 code))
              send (fn [s]                                       ; return result with prompt
                     (let [bytes (byte-str s "\n" (ns-name (:*ns* env2)) "=> ")]
                       (.Send socket bytes (.Length bytes) destination)))]
          (swap! ip-map assoc destination env2)                  ; update ip-map with new env
          (Debug/Log "Sending result")
          (let [res (try
                      (send result)
                      (catch SocketException e
                        (Debug/Log (str "SocketException encountered:\n" e))
                        (send (.ToString e))))]
            (Debug/Log "Sent result")
            res))
        (catch Exception e
          (Debug/Log (str e)))))))

(defn- listen-and-block [^UdpClient socket]
  (let [sender (IPEndPoint. IPAddress/Any 0)
        incoming-bytes (.Receive socket (by-ref sender))]
    (when (> (.Length incoming-bytes) 0)
      (let [incoming-code (.GetString Encoding/UTF8 incoming-bytes 0 (.Length incoming-bytes))]
        (Debug/Log (str "bytes received: " incoming-code))
        (.Enqueue work-queue [incoming-code socket sender])))))

(defn start-server [^long port]
  (Debug/Log @server-running)
  (if @server-running
    (throw (Exception. "REPL Already Running")))
  (reset! server-running true)
  (let [socket (UdpClient. (IPEndPoint. IPAddress/Any port))]
    (set! (.. socket Client SendBufferSize) (* 1024 5000)) ;; 5Mb
    (set! (.. socket Client ReceiveBufferSize) (* 1024 5000)) ;; 5Mb
    (.Start (Thread. (gen-delegate ThreadStart []
                                   (if (@configuration :verbose)
                                     (Debug/Log "Starting REPL..."))
                                   (while @server-running
                                     (listen-and-block socket))
                                   ;; TODO why does this line not execute?
                                   (if (@configuration :verbose)
                                     (Debug/Log "REPL Stopped")))))
    socket))

(defn stop-server [^UdpClient socket]
  (if (@configuration :verbose)
    (Debug/Log "Stopping REPL..."))
  (reset! server-running false)
  (.Close socket))
