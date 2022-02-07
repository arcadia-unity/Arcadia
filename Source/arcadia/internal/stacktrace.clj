(ns arcadia.internal.stacktrace
  (:import [System.Diagnostics StackFrame StackTrace]
           [System.Reflection MethodInfo ParameterInfo]))

;; see https://github.com/arcadia-unity/Arcadia/wiki/Stacktraces-and-Error-Reporting

(def default-opts
  {:hide-parameters false
   :hide-outer-exceptions false
   ;; if `true`, hides stack trace of `clojure.lang.Compiler+CompilerException` and
   ;; other extraneous layers of nested exceptions
   :abbreviate-junk-exception-layers true

   :ditto-classnames false
   :demunge-classnames true
   :star-ifn true
   ;; If collapsing invocations, should also hide ifn parameters.
   ;; Otherwise, show parameters unless EITHER
   ;; :hide-parameters OR :hide-ifn-parameters is flipped on.
   :collapse-invocations true 
   :quiet-invocations false
   :hide-ifn-parameters false
   
   ;; if false, all other flags are moot and we print the
   ;; raw exception
   :format true})

;; ------------------------------------------------------------

(defn frame-analysis [^StackFrame frame, opts]
  (let [analysis {:type :stack-frame-analysis
                  :frame frame
                  :file (.GetFileName frame)
                  :line (.GetFileLineNumber frame)
                  :column (.GetFileColumnNumber frame)}]
    (if-let [method (.GetMethod frame)]
      (assoc analysis
        :method-name (.Name method)
        :method-declaring-class (.DeclaringType method)
        :classname-str (when-let [t (.DeclaringType method)] (.ToString t))
        :ifn (isa? (.DeclaringType method) clojure.lang.IFn)
        :parameters (.GetParameters method))
      analysis)))

(defn ditto-index [^String s1, ^String s2, opts]
  (let [len (min (count s1) (count s2))
        i (dec
            (loop [i (int 0)]
              (if (< i len)
                (if (= (.get_Chars s1 i) (.get_Chars s2 i))
                  (recur (inc i))
                  i)
                i)))]
    (loop [i (int i)]
      (if (< 0 i)
        (if (#{\/ \.} (.get_Chars s2 i))
          i
          (recur (dec i)))
        i))))

(defn ditto-string [^String s1, ^String s2, opts]
  (let [i (ditto-index s1 s2 opts)]
    (if (< 4 i)
      (str (String. \space i) (subs s2 i))
      s2)))

(defn ditto-classnames [opts frames]
  (if (:ditto-classnames opts)
    (->> (partition 2 1 frames)
         (keep (fn [[frame1 frame2]]
                 (when frame2
                   (assoc frame2
                     :classname-str (ditto-string
                                      (:classname-str frame1)
                                      (:classname-str frame2)
                                      opts)))))
         (cons (first frames)))
    frames))

(defn demunge-classnames [opts frames]
  (if (:demunge-classnames opts)
    (for [{:keys [:classname-str] :as frame} frames]
      (if classname-str
        (assoc frame :classname-str
          (-> (clojure.lang.Compiler/demunge classname-str)
              (clojure.string/replace #"--\d+" "")))
        frame))
    frames))

(defn invocation? [{:keys [ifn method-name]}]
  (boolean
    (and ifn (#{"invoke" "invokeStatic" "doInvoke" "applyTo" "ApplyToHelper"} method-name))))

(defn collapse-invocations [{:keys [collapse-invocations]} frames]
  (if collapse-invocations
    (letfn [(step [{last-class :method-declaring-class :as last-frame}, frames]
              (lazy-seq
                (when-first [{:keys [method-declaring-class ifn] :as frame-analysis} frames]
                  (if (and (= last-class method-declaring-class)
                           (invocation? last-frame)
                           (invocation? frame-analysis))
                    (step frame-analysis (next frames))
                    (cons frame-analysis (step frame-analysis (next frames)))))))]
      (when-first [frame frames]
        (cons frame (step frame (next frames)))))
    frames))

(defn relative-file-path [s]
  (str
    (.MakeRelativeUri
      (System.Uri. (System.IO.Directory/GetCurrentDirectory))
      (System.Uri. s))))

(defn file-data-string [{:keys [file, line, column]} opts]
  (when (and file line column)
    (str (relative-file-path file) " (" line ":" column ")")))

(defn parameters-string [{:keys [collapse-invocations
                                 ifn
                                 parameters]},
                         {:keys [hide-parameters
                                 collapse-invocations
                                 hide-ifn-parameters]
                          :as opts}]
  (when parameters
    ;; see comment in default-opts for explanation of this boolean test
    (let [hide? (or hide-parameters
                    (and hide-ifn-parameters ifn)
                    (and collapse-invocations ifn))]
      (when-not hide?
        (let [pstrs (for [^ParameterInfo pinf parameters]
                      (let [t (.ParameterType pinf)]
                        (if (= t System.Object)
                          "object"
                          t)))]
          (str "(" (clojure.string/join ", " pstrs) ")"))))))

(defn trace-str [^StackTrace st, opts]
  (let [{:keys [star-ifn quiet-invocations]} opts
        analysis (->> (map #(frame-analysis % opts) (.GetFrames st))
                      (collapse-invocations opts)
                      (demunge-classnames opts)
                      (ditto-classnames opts))]
    (clojure.string/join "\n"
      (for [{:keys [classname-str, method-name, ifn,
                    hide-parameters] :as frame-analysis} analysis]
        (clojure.string/join " "
          (remove nil?
            [(when star-ifn (if ifn "*", " "))
             (str
               (or classname-str "NO_CLASS")
               (if method-name
                 (when-not (and (or (:collapse-invocations opts) quiet-invocations)
                                (invocation? frame-analysis))
                   (str "." method-name))
                 (str ".NO_METHOD")))
             (parameters-string frame-analysis opts)
             (file-data-string frame-analysis opts)]))))))

(defn exception-layers [e]
  (take-while some? (iterate #(.InnerException ^Exception %) e)))

;; TODO: put in utils
(defn- truncate-string
  ([^String s, max-length]
   (if (< max-length (.Length s))
     (.Substring s 0 max-length)
     s))
  ([^String s, max-length, ^String ellipsis]
   (if (< max-length (.Length s))
     (str (.Substring s 0 max-length) ellipsis)
     s)))

(def opts-log (atom nil))

(defn exception-str ^String [^Exception e, opts]
  (reset! opts-log opts)
  (if (:format opts)
    (let [es (reverse (exception-layers e))
          es-count (count es)]
      (->> (if (:hide-outer-exceptions opts) (take 1 es) es)
           (map-indexed
             (fn [i, ^Exception e]
               (let [ex-info? (instance? clojure.lang.IExceptionInfo e)
                     compiler-exception? (instance? clojure.lang.Compiler+CompilerException e)
                     ;; repl thing. this should be an ex-info but isn't, my fault
                     outer-repl-carrier-exception? (and (= i (dec es-count))
                                                        (= "carrier" (.Message e))
                                                        (= System.Exception (class e)))
                     suppress-trace? (and
                                       (:abbreviate-junk-exception-layers opts)
                                       ;; print the innermost exception regardless
                                       (< 0 i)
                                       ;; is it junk? add more as they come up
                                       (or compiler-exception?
                                           outer-repl-carrier-exception?))
                     message (if (and suppress-trace?
                                      ;; this is only redundant for now
                                      compiler-exception?)
                               ;; compiler exceptions very cutely include the stack trace of
                               ;; their InnerException in their message
                               (first (clojure.string/split-lines (.Message e)))
                               (.Message e))
                     parts [(str (class e) ": " message)
                            (when ex-info?
                              "\t Data:"
                              (binding [*print-level* 2
                                        *print-length* 4]
                                (truncate-string
                                  (with-out-str (println (.Data e)))
                                  20)))
                            (if suppress-trace?
                              "(StackTrace suppressed)"
                              (trace-str (StackTrace. e true) opts))]]
                 (->> parts
                      (filter some?)
                      (clojure.string/join "\n")))))
           (clojure.string/join "\nvia\n")))
    (str e)))
