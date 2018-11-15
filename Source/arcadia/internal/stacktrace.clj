(ns arcadia.internal.stacktrace
  (:import [System.Diagnostics StackFrame StackTrace]
           [System.Reflection MethodInfo]))


(def default-opts
  {:demunge-classnames true
   :collapse-invocations true
   :ditto-classnames true
   :star-ifn true})

;; ------------------------------------------------------------

(defn frame-analysis [^StackFrame frame, opts]
  (-> {:type :stack-frame-analysis
       :frame frame}
      (as-> analysis
            (if-let [method (.GetMethod frame)]
              (assoc analysis
                :method-name (.Name method)
                :methodname-str (.Name method)
                :method-declaring-class (.DeclaringType method)
                :classname-str (.ToString (.DeclaringType method))
                :ifn (isa? (.DeclaringType method) clojure.lang.IFn))
              analysis)
            (if-let [file (.GetFileName frame)]
              (assoc analysis :file file)
              analysis)
            (if-let [line (.GetFileLineNumber frame)]
              (assoc analysis :line line)
              analysis)
            (if-let [column (.GetFileColumnNumber frame)]
              (assoc analysis :column column)
              analysis))))

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
  (break)
  (if (:demunge-classnames opts)
    (for [{:keys [:classname-str] :as frame} frames]
      (if classname-str
        (assoc frame :classname-str
          (-> (clojure.lang.Compiler/demunge classname-str)
              (clojure.string/replace #"--\d+" "")))
        frame))
    frames))

(defn collapse-invocations [{:keys [collapse-invocations]} frames]
  (if collapse-invocations
    (let [invocations #{"invoke" "invokeStatic" "doInvoke" "applyTo" "ApplyToHelper"}]
      (letfn [(step [last-class last-method-name frames]
                (lazy-seq
                  (when-first [{:keys [method-declaring-class
                                       method-name
                                       ifn] :as frame-analysis} frames]
                    (if (and ifn 
                             (= last-class method-declaring-class)
                             (invocations last-method-name)
                             (invocations method-name))
                      (step method-declaring-class method-name (next frames))
                      (cons frame-analysis
                        (step method-declaring-class method-name (next frames)))))))]
        (when-first [{:keys [method-declaring-class method-name] :as frame} frames]
          (cons frame (step method-declaring-class method-name (next frames))))))
    frames))

(defn relative-file-path [s]
  (str
    (.MakeRelativeUri
      (System.Uri. UnityEngine.Application/dataPath)
      (System.Uri. s))))

(defn file-data-string [{:keys [^StackFrame frame]} opts]
  (let [file (.GetFileName frame)
        line (.GetFileLineNumber frame)
        column (.GetFileColumnNumber frame)]
    (when (and file line column)
      (str (relative-file-path file) " (" line ":" column ")"))))

(defn trace-str [^StackTrace st, opts]
  (let [{:keys [star-ifn]} opts
        analysis (->> (map #(frame-analysis % opts) (.GetFrames st))
                      (collapse-invocations opts)
                      (demunge-classnames opts)
                      (ditto-classnames opts))]
    (clojure.string/join "\n"
      (for [{:keys [classname-str, methodname-str, ifn, ^StackFrame frame] :as m} analysis]
        (str
          (when star-ifn (if ifn "* ", " "))
          classname-str
          " "
          methodname-str
          " "
          (file-data-string m opts))))))

(defn exception-layers [e]
  (take-while some? (iterate #(.InnerException ^Exception %) e)))

(defn exception-str ^String [^Exception e, opts]
  (let [es (reverse (exception-layers e))]
    (clojure.string/join "\nvia\n"
      (for [e es]
        (str (class e) ": " (.Message e) "\n"
             (trace-str (StackTrace. e) opts))))))
