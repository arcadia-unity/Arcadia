(ns arcadia.debug
  (:require [clojure.pprint :as pprint]))

;; TODO: figure out difference between environment and context and
;; stick to it

;; ============================================================
;; env

(defmacro with-context [ctx-name & body]
  (let [symbols (keys &env)]
    `(let [~ctx-name ~(zipmap
                        (map (fn [sym] `(quote ~sym)) symbols)
                        symbols)]
       ~@body)))

;; ============================================================
;; Breakpoints
;; Inspired by Michael Fogus's breakpoint macro


;; rather than just *can-break*, consider a way of cancelling
;; all breakpoints, but all enabling or disabling all breakpoints
;; by their labels, or by whether their labels match a predicate
(def ^:dynamic *can-break* true)

(defn disable-breaks []
  (alter-var-root #'*can-break* (constantly false)))

(defn enable-breaks []
  (alter-var-root #'*can-break* (constantly true)))

(def ^:dynamic *breaking* false)

(def ^:dynamic *env-store*)

(defn- contextual-eval [expr]
  (eval
    `(let [~@(mapcat
               (fn [k] [k `(get *env-store* (quote ~k))])
               (keys *env-store*))]
       ~expr)))

(defn- readr [prompt exit-code]
  (let [input (clojure.main/repl-read prompt exit-code)]
    (if (#{:tl :resume} input)
      exit-code
      input)))

(defn break-repl [label]
  (let [prompt (str "debug"
                    (when label (str ":" label))
                    "=> ")]
    (clojure.main/repl
      :prompt #(print prompt)
      :read readr
      :eval contextual-eval)))

(defmacro break
  "Inserts a breakpoint, suspending other evalution and entering a
  subrepl with access to local environment. Exit by typing `:tl` or
  `:resume` in the repl prompt. If provided, `label` will
  show in the breakpoint repl prompt. Note that `label` is evaluated
  at runtime."
  ([] `(break nil))
  ([label]
   (let [symbols (keys &env)
         ns-name (ns-name *ns*)]
     `(when *can-break*
        (with-context ctx#
          (binding [*env-store* ctx#
                    *breaking* true
                    *ns* (find-ns (quote ~ns-name))]
            (break-repl ~label)))))))

;; ============================================================
;; Breakpoint niceties

(defn pareditable-print-table
  ([rows]
   (pareditable-print-table (keys (first rows)) rows))
  ([ks rows]
   (let [t (with-out-str (pprint/print-table ks rows))]
     (print
       (if (odd? (count (re-seq #"\|" t)))
         (str t "|")
         t)))))

(defn penv-func [env]
  (let [maxlen 40
        shorten (fn [s]
                  (if (< maxlen (count s))
                    (str (subs s 0 maxlen) "...")
                    s))
        lvs (->> (for [[k v] env
                       :when (not (re-matches #"fn__\d+" (str k)))] ;; perhaps filter out weird functions
                   {:local k
                    :value (shorten (pr-str v))
                    :class (shorten (pr-str (class v)))})
                 (sort-by :local))]
    (pareditable-print-table
      lvs)))

;; print env (context?)
(defmacro penv []
  `(with-context env# ;; ENV OR CTX???
     (penv-func env#)))
