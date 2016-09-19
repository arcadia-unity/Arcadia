(ns arcadia.repl
  (:refer-clojure :exclude [with-bindings])
  (:require [clojure.main :as main]
            [clojure.string :as str]
            [arcadia.config :as config]
            [arcadia.internal.functions :as af]
            [clojure.pprint :as pprint])
  (:import
    [UnityEngine Debug]
    [System.IO EndOfStreamException]
    [System.Collections Queue]
    [System.Net IPEndPoint IPAddress]
    [System.Net.Sockets UdpClient SocketException]
    [System.Threading Thread ThreadStart]
    [System.Text Encoding]))

;; ============================================================

;; utter kludge hack to help with poorly-understood bug where types
;; defined in namespace 'user are only accessible if (ns user) has
;; been evaluated in current session

(ns user)
(ns arcadia.repl)

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
    *assert*
    *1
    *2
    *3
    *e])

(defmacro with-env-bindings
  "Executes body in the context of thread-local bindings for several vars
  that often need to be set!"
  [repl-env & body]
  `(with-packed-bindings ~repl-env ~env-binding-symbols
     ~@body))

(defmacro pack-env-bindings []
  `(pack-bindings ~env-binding-symbols))

;; ==================================================
;; config bindings

;; (defonce config-binding-symbols
;;   `[*ns*
;;     ~'*debug* ;; is this namespace qualified or not?
;;     *warn-on-reflection*
;;     *unchecked-math*])

;; (defmacro with-config-compiler-bindings [& body]
;;   `(with-packed-bindings (:compiler (config/config)) ~config-binding-symbols
;;      ~@body))

;; (defmacro pack-config-bindings []
;;   `(pack-bindings ~config-binding-symbols))


;; ============================================================
;; stack trace cleanup

(def ^:dynamic *short-stack-trace* true)

;; as seen in spec.test
(defonce stack-frame-regex
  (re-pattern
    (clojure.string/join
      (flatten
        ["^\\s*at"
         "\\s+(\\S+)\\.(\\S+)" ;; class & method
         ".*(\\(.*\\))"        ;; params
         "(?:\\s+(\\[.*\\]))?" ;; weirdness
         "(?:\\s+in\\s+)?"
         ["(?:"
          "(\\S+?)"            ;; file
          "\\:"
          "(?:line\\D+)?"      ;; line
          "(\\d+)"             ;; line number
          ")?"]
         ".*$"]))))

(defn parse-stack-trace [stack-trace]
  (for [frame (clojure.string/split-lines stack-trace)]
    (when-let [[_ class method args _ file line] (first
                                                   (re-seq stack-frame-regex
                                                     frame))]
      {::class class
       ::method method
       ::args args
       ::file file
       ::line line
       ::original frame})))

(defn cleanse-stack-trace [stack-trace]
  (let [st (->> (parse-stack-trace stack-trace)
                (remove nil?)
                (filter ::class)
                (remove
                  (fn [{:keys [::class]}]
                    (or (re-matches #"arcadia/repl.*" class)
                        (re-matches #"clojure.lang.Compiler.*" class)
                        (re-matches #"clojure/core\$eval.*" class))))
                (map ::original))]
    (str (str/join "\n" st)
         "\n("
         (- (count (re-seq #"\n" stack-trace))
            (count st))
         " stack frames omitted)")))

(defn exception-string [^Exception e]
  (str (class e) ": " (.Message e) "\n"
       (if *short-stack-trace*
         (cleanse-stack-trace (.StackTrace e))
         (.StackTrace e))))

;; ============================================================
;; state

(defonce ip-map
  (atom {}))

;; ============================================================
;; details

;; not AT ALL sure we should be trumping these dynamic vars with configuration
;; in fact not sure why we are at all
(defn env-map []
  (pack-env-bindings))

(defn read-string* [s]
  (when-not (str/blank? s)
    (read-string s)))

;; :P
(def ^:dynamic *pprint-output* false)

(defn eval-to-string [frm]
  (with-out-str
    (binding [*err* *out*]
      ((if *pprint-output* pprint/pprint prn)
        (let [value (eval
                      `(do
                         ~(when-let [inj (read-string*
                                           (pr-str ((config/config) :repl/injections)))]
                            `(try
                               ~inj
                               (catch Exception e#
                                 (UnityEngine.Debug/Log (str e#)))))
                         ~frm))]
          (set! *3 *2)
          (set! *2 *1)
          (set! *1 value)
          value)))))

 ; need some stuff in here about read-eval maybe
(defn repl-eval-print [repl-env s]
  (with-env-bindings repl-env
    (let [result (try
                   (eval-to-string
                     (read-string* s))
                   (catch Exception e
                     (set! *e e)
                     (exception-string e)))]
      {:result result
       :env (env-map)})))

(defonce work-queue (Queue/Synchronized (Queue.)))

(defonce server-running (atom false))

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
          (try
            (send result)
            (catch SocketException e
              (Debug/Log (str "SocketException encountered:\n" e))
              (send (.ToString e)))))
        (doseq [view (UnityEngine.Resources/FindObjectsOfTypeAll UnityEditor.GUIView)]
          (.Repaint view))
        (catch Exception e
          (Debug/Log (str e)))))))

(defn- listen-and-block [^UdpClient socket]
  (let [sender (IPEndPoint. IPAddress/Any 0)
        incoming-bytes (.Receive socket (by-ref sender))]
    (when (> (.Length incoming-bytes) 0)
      (let [incoming-code (.GetString Encoding/UTF8 incoming-bytes 0 (.Length incoming-bytes))]
        (.Enqueue work-queue [incoming-code socket sender])))))

(defn start-server [^long port]
  (if @server-running
    (Debug/Log "REPL already running")
    (Debug/Log "Starting REPL"))
  (when-not @server-running
    (reset! server-running true)
    (let [socket (UdpClient. (IPEndPoint. IPAddress/Any port))]
      (set! (.. socket Client SendBufferSize) (* 1024 5000)) ;; 5Mb
      (set! (.. socket Client ReceiveBufferSize) (* 1024 5000)) ;; 5Mb
      (.Start (Thread. (gen-delegate ThreadStart []
                                     (if ((config/config) :verbose)
                                       (Debug/Log "Starting REPL..."))
                                     (while @server-running
                                       (listen-and-block socket))
                                     ;; TODO why does this line not execute?
                                     (if ((config/config) :verbose)
                                       (Debug/Log "REPL Stopped")))))
      socket)))

(defn stop-server [^UdpClient socket]
  (if ((config/config) :verbose)
    (Debug/Log "Stopping REPL..."))
  (locking server-running
    (when @server-running
      (reset! server-running false)
      (.Close socket))))
