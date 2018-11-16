(ns arcadia.internal.stacktrace
  (:import [System.Diagnostics StackFrame StackTrace]
           [System.Reflection MethodInfo]))


(def default-opts
  {:demunge-classnames true
   :collapse-invocations true
   :ditto-classnames true
   :star-ifn true
   :quiet-invocations true
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
        :methodname-str (.Name method)
        :method-declaring-class (.DeclaringType method)
        :classname-str (.ToString (.DeclaringType method))
        :ifn (isa? (.DeclaringType method) clojure.lang.IFn))
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
      (System.Uri. UnityEngine.Application/dataPath)
      (System.Uri. s))))

(defn file-data-string [{:keys [file, line, column]} opts]
  (when (and file line column)
    (str (relative-file-path file) " (" line ":" column ")")))

(defn trace-str [^StackTrace st, opts]
  (let [{:keys [star-ifn quiet-invocations]} opts
        analysis (->> (map #(frame-analysis % opts) (.GetFrames st))
                      (collapse-invocations opts)
                      (demunge-classnames opts)
                      (ditto-classnames opts))]
    (clojure.string/join "\n"
      (for [{:keys [classname-str, methodname-str, ifn] :as frame-analysis} analysis]
        (clojure.string/join " "
          (remove nil?
            [(when star-ifn (if ifn "*", " "))
             classname-str
             (when (and (not quiet-invocations) (invocation? frame-analysis) ifn) methodname-str)
             (file-data-string frame-analysis opts)]))))))

(defn exception-layers [e]
  (take-while some? (iterate #(.InnerException ^Exception %) e)))

(defn exception-str ^String [^Exception e, opts]
  (let [es (reverse (exception-layers e))]
    (clojure.string/join "\nvia\n"
      (if (:format opts)
        (for [^Exception e es]
          (str (class e) ": " (.Message e) "\n"
               (trace-str (StackTrace. e true) opts))) ; important that this is type-hinted, changes behavior
        es))))
