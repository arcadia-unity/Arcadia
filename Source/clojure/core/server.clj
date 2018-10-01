;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Socket server support"
      :author "Alex Miller"}
  clojure.core.server
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.main :as m])
  (:import [System.Net.Sockets Socket SocketException TcpListener TcpClient] [System.Net Dns IPAddress]))                ;;; [java.net InetAddress Socket ServerSocket SocketException]
                                                                                                                         ;;; [java.util.concurrent.locks ReentrantLock]

(set! *warn-on-reflection* true)

(def ^:dynamic *session* nil)

;; lock protects servers
(defonce ^:private lock (Object.))             ;;; ReentrantLock.  -- no CLR equivalent
(defonce ^:private servers {})

(defmacro ^:private with-lock
   [lock-expr & body]
  `(let [lockee# ~lock-expr]                   ;;;   ~(with-meta lock-expr {:tag 'java.util.concurrent.locks.ReentrantLock})
      (monitor-enter lockee#)                  ;;;  (.lock lockee#)
	  (try
        ~@body
        (finally
          (monitor-exit lockee#)))))           ;;; (.unlock lockee#)
 
(defmacro ^:private thread
  [^String name daemon & body]
  `(doto (System.Threading.Thread.  ^System.Threading.ThreadStart (gen-delegate System.Threading.ThreadStart [] ~@body))          ;;; (doto (Thread. (fn [] ~@body) ~name)
    (.set_Name ~name) (.set_IsBackground ~daemon)                                                   ;;; (.setDaemon ~daemon)
    (.Start)))                                                                                      ;;; .start

(defn- required
  "Throw if opts does not contain prop."
  [opts prop]
  (when (nil? (get opts prop))
    (throw (ex-info (str "Missing required socket server property " prop) opts))))

(defn- validate-opts
  "Validate server config options"
  [{:keys [name port accept] :as opts}]
  (doseq [prop [:name :port :accept]] (required opts prop))
  (when (or (not (integer? port)) (not (< -1 port 65535)))
    (throw (ex-info (str "Invalid socket server port: " port) opts))))

(defn- accept-connection
  "Start accept function, to be invoked on a client thread, given:
    conn - client socket
    name - server name
    client-id - client identifier
    in - in stream
    out - out stream
    err - err stream
    accept - accept fn symbol to invoke
    args - to pass to accept-fn"
  [^TcpClient conn name client-id in out err accept args]                                 ;;; ^Socket
  (try  (.set_AutoFlush ^System.IO.StreamWriter out true)                                 ;;; Added .set_Autoflush command
    (binding [*in* in
              *out* out
              *err* err
              *session* {:server name :client client-id}]
      (with-lock lock
        (alter-var-root #'servers assoc-in [name :sessions client-id] {}))
      (require (symbol (namespace accept)))
      (let [accept-fn (resolve accept)]
        (apply accept-fn args)))
    (catch SocketException _disconnect)
    (finally
      (with-lock lock
        (alter-var-root #'servers update-in [name :sessions] dissoc client-id))
      (.Close ^System.IO.TextReader in) (.Close conn))))                                                                 ;;; .close  DM: Added (.Close in)

(defn start-server
  "Start a socket server given the specified opts:
    :address Host or address, string, defaults to loopback address
    :port Port, integer, required
    :name Name, required
    :accept Namespaced symbol of the accept function to invoke, required
    :args Vector of args to pass to accept function
    :bind-err Bind *err* to socket out stream?, defaults to true
    :server-daemon Is server thread a daemon?, defaults to true
    :client-daemon Are client threads daemons?, defaults to true
   Returns server socket."
  [opts]
  (validate-opts opts)
  (let [{:keys [address port name accept args bind-err server-daemon client-daemon]
         :or {bind-err true
              server-daemon true
              client-daemon true}} opts
         address (if (instance? System.Net.IPAddress address) address (aget (.AddressList (let [^String a (or address "localhost")] (Dns/GetHostEntry a))) 0))                          ;;; Add IPAddress check,  also: (InetAddress/getByName address)  ;; nil returns loopback  Added let to get type info
         socket (TcpListener. address port)]                                                                         ;;; ( ServerSocket. port 0 address)
    (with-lock lock
      (alter-var-root #'servers assoc name {:name name, :socket socket, :sessions {}}))
    (thread
      (str "Clojure Server " name) server-daemon
      (try   (.Start socket)                                                                                         ;;; DM: Added (.Start socket)
        (loop [client-counter 1]
          (when (.IsBound (.Server socket))                                                                          ;;;  (not (.isClosed socket))   -- IsBound isn't right, but I dont' ha
            (try
              (let [conn (.AcceptTcpClient socket)                                                                   ;;; .accept
                    in (clojure.lang.LineNumberingTextReader. (System.IO.StreamReader. (.GetStream conn)))           ;;; LineNumberingPushbackReader  java.io.InputStreamReader.  .getInputStream
                    out  (System.IO.StreamWriter. (.GetStream conn))                                                 ;;; java.io.BufferedWriter.  java.io.OutputStreamWriter.  .getOutputStream
                    client-id (str client-counter)]
                (thread
                  (str "Clojure Connection " name " " client-id) client-daemon
                  (accept-connection conn name client-id in out (if bind-err out *err*) accept args)))
              (catch SocketException _disconnect))
            (recur (inc client-counter))))
        (finally
          (with-lock lock
            (alter-var-root #'servers dissoc name)))))
    socket))

(defn stop-server
  "Stop server with name or use the server-name from *session* if none supplied.
  Returns true if server stopped successfully, nil if not found, or throws if
  there is an error closing the socket."
  ([]
   (stop-server (:server *session*)))
  ([name]
   (with-lock lock
     (let [server-socket ^TcpListener (get-in servers [name :socket])]               ;;; ^ServerSocket
       (when server-socket
         (alter-var-root #'servers dissoc name)
         (.Stop server-socket)                                                       ;;; .close
         true)))))

(defn stop-servers
  "Stop all servers ignores all errors, and returns nil."
  []
  (with-lock lock
    (doseq [name (keys servers)]
      (future (stop-server name)))))

(defn- parse-props
  "Parse clojure.server.* from properties to produce a map of server configs."
  [props]
  (reduce
    (fn [acc [^String k ^String v]]
      (let [[k1 k2 k3] (str/split k #"\.")]
        (if (and (= k1 "clojure") (= k2 "server"))
          (conj acc (merge {:name k3} (edn/read-string v)))
          acc)))
    [] props))

(defn start-servers
  "Start all servers specified in the system properties."
  [system-props]
  (doseq [server (parse-props system-props)]
    (start-server server)))

(defn repl-init
  "Initialize repl in user namespace and make standard repl requires."
  []
  (in-ns 'user)
  (apply require clojure.main/repl-requires))

(defn repl-read
  "Enhanced :read hook for repl supporting :repl/quit."
  [request-prompt request-exit]
  (or ({:line-start request-prompt :stream-end request-exit}
        (m/skip-whitespace *in*))
      (let [input (read {:read-cond :allow} *in*)]
        (m/skip-if-eol *in*)
        (case input
          :repl/quit request-exit
          input))))

(defn repl
  "REPL with predefined hooks for attachable socket server."
  []
  (m/repl
    :init repl-init
    :read repl-read))