(ns arcadia.internal.spec
  (:refer-clojure :exclude [def])
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [clojure.pprint :as pprint])
  (:import [UnityEngine Debug]))
;; cloying syrupy conveniences for clojure.spec

(defmacro collude [gen el]
  (let [switch (->> `[set? map? seq? vector? list?]
                 (mapcat (fn [p] `[(~p ~gen) ~p]))
                 (cons `cond))]
    `(s/and ~switch (s/coll-of ~el))))

(defmacro qwik-cat [& things]
  (list* `s/cat (interleave things things)))

(defmacro qwik-or [& things]
  (list* `s/or (interleave things things)))

(defmacro qwik-alt [& things]
  (list* `s/alt (interleave things things)))

;; ============================================================
;; arrgh

;; this is actually pointless. remove it.

(def assert-stack (atom [*assert*]))

(defn push-assert [state]
  (alter-var-root #'*assert*
    (constantly
      (peek
        (swap! assert-stack conj state)))))

(defn pop-assert []
  (alter-var-root #'*assert*
    (constantly
      (peek
        (swap! assert-stack pop)))))

;; ============================================================
;; more legible explanations

(defmacro ^:private with-out-str-indented [indent & body]
  `(let [indent# (String.
                   ~(first " ") ;; for our jank repl
                   ~indent)
         s1# (with-out-str (do ~@body))
         s2# (->> s1#
                  clojure.string/split-lines
                  (map (fn [line#]
                         (str indent# line#)))
                  (clojure.string/join "\n"))]
     (if (re-matches #".*\n$" s1#)
       (str s2# "\n")
       s2#)))

(defmacro ^:private def-indented [name wrapped]
  `(defn- ~name [indent# & args#]
     (print
       (with-out-str-indented indent#
         (apply ~wrapped args#)))))

(def-indented print* print)

(def-indented pr* pr)

(def-indented prn* pr)

(def-indented println* println)

(def-indented pprint* pprint/pprint)

(defn explain-out
  "prints an explanation to *out*."
  [ed]
  (let [indent 5
        pp (fn [& args]
             (println
               (str/trimr
                 (with-out-str
                   (apply pprint* indent args)))))
        tp (fn [& args]
             (let [pre (apply str (butlast args))
                   tail (last args)]
               (println
                 (as-> tail s
                       (str/trim
                         (with-out-str
                           (pprint* indent s)))
                       (if (< (count pre) indent)
                         (str (str/join (repeat (- indent (count pre)) " "))
                              s)
                         s)
                       (str pre s)))))]
    (if ed
      (do
        ;;(prn {:ed ed})
        (println "==================================================")
        (doseq [{:keys [path pred val reason via in] :as prob}  (::s/problems ed)]
          (when-not (empty? in)
            (tp "In:" in))
          (tp "val:" val)
          (println "fails")
          (when-not (empty? via)
            (tp " spec:" (last via)))
          (when-not (empty? path)
            (tp " at:" path))
          (println "predicate: ")
          (pr* indent pred)
          (when reason
            (println "Reason:")
            (print reason))
          (newline)
          (when (seq prob) (print "furthermore:"))
          (doseq [[k v] prob]
            (when-not (#{:pred :val :reason :via :in} k)
              (print "\n\t" k " ")
              (pr v)))
          (newline)
          (println "------------------------------"))
        (println )
        (doseq [[k v] ed]
          (when-not (#{::s/problems} k)
            (print k " ")
            (pr v)
            (newline)))
        (println "--------------------------------------------------"))
      (println "Success!"))))

(defmacro legible [& body]
  `(with-redefs [clojure.spec/explain-out explain-out]
     ~@body))
;; ============================================================

(defmacro qwik-conform [[lcl spec src] & body]
  `(let [src# ~src
         spec# ~spec
         lcltemp# (s/conform spec# src#)]
     (if (= ::s/invalid lcltemp#)
       (throw
         (Exception.
           (str "invalid thing:\n"
                (with-out-str
                  (legible
                    (s/explain spec# src#))))))
       (let [~lcl lcltemp#]
         ~@body))))

(defn loud-valid? [spec val]
  (or (s/valid? spec val)
    (Debug/Log
      (with-out-str
        (legible
          (s/explain spec val))))
    false))

