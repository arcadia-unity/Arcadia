(ns arcadia.debug
  (:require [clojure.pprint :as pprint]
            [clojure.main :as m]
            [clojure.spec.alpha :as s]))

;; ;; TODO: figure out difference between environment and context and
;; ;; stick to it

;; ;; ============================================================
;; ;; env

;; (defmacro with-context [ctx-name & body]
;;   (let [symbols (keys &env)]
;;     `(let [~ctx-name ~(zipmap
;;                         (map (fn [sym] `(quote ~sym)) symbols)
;;                         symbols)]
;;        ~@body)))

;; ;; ============================================================
;; ;; Breakpoints
;; ;; Inspired by Michael Fogus's breakpoint macro


;; ;; rather than just *can-break*, consider a way of cancelling
;; ;; all breakpoints, but all enabling or disabling all breakpoints
;; ;; by their labels, or by whether their labels match a predicate
;; (def ^:dynamic *can-break* true)

;; (defn disable-breaks []
;;   (alter-var-root #'*can-break* (constantly false)))

;; (defn enable-breaks []
;;   (alter-var-root #'*can-break* (constantly true)))

;; (def ^:dynamic *breaking* false)

;; (def ^:dynamic *env-store*)

;; (defn- contextual-eval [expr]
;;   (eval
;;     `(let [~@(mapcat
;;                (fn [k] [k `(get *env-store* (quote ~k))])
;;                (keys *env-store*))]
;;        ~expr)))

;; (def ^:dynamic *reenable-breaks*)

;; (defn- readr [prompt exit-code]
;;   (let [input (clojure.main/repl-read prompt exit-code)]
;;     (cond
;;       (#{:tl :resume} input) exit-code
;;       (= :quit input) (do (disable-breaks)
;;                           exit-code)
;;       :else input)))

;; (defn break-repl [label]
;;   (let [prompt (str "debug"
;;                     (when label (str ":" label))
;;                     "=> ")]
;;     (binding [*reenable-breaks* false]
;;       (clojure.main/repl
;;         :prompt #(print prompt)
;;         :read readr
;;         :eval contextual-eval)
;;       (when *reenable-breaks*
;;         (enable-breaks)))))

;; (defmacro break
;;   "Inserts a breakpoint, suspending other evalution and entering a
;;   subrepl with access to local environment. Exit by typing `:tl` or
;;   `:resume` in the repl prompt. If provided, `label` will
;;   show in the breakpoint repl prompt. Note that `label` is evaluated
;;   at runtime."
;;   ([] `(break nil))
;;   ([label]
;;    (let [symbols (keys &env)
;;          ns-name (ns-name *ns*)]
;;      `(when *can-break*
;;         (with-context ctx#
;;           (binding [*env-store* ctx#
;;                     *breaking* true
;;                     *ns* (find-ns (quote ~ns-name))]
;;             (break-repl ~label)))))))

;; ;; ============================================================
;; ;; Breakpoint niceties

;; (defn pareditable-print-table
;;   ([rows]
;;    (pareditable-print-table (keys (first rows)) rows))
;;   ([ks rows]
;;    (let [t (with-out-str (pprint/print-table ks rows))]
;;      (print
;;        (if (odd? (count (re-seq #"\|" t)))
;;          (str t "|")
;;          t)))))

;; (defn penv-func [env]
;;   (let [maxlen 40
;;         shorten (fn [s]
;;                   (if (< maxlen (count s))
;;                     (str (subs s 0 maxlen) "...")
;;                     s))
;;         lvs (->> (for [[k v] env
;;                        :when (not (re-matches #"fn__\d+" (str k)))] ;; perhaps filter out weird functions
;;                    {:local k
;;                     :value (shorten (pr-str v))
;;                     :class (shorten (pr-str (class v)))})
;;                  (sort-by :local))]
;;     (pareditable-print-table
;;       lvs)))

;; ;; print env (context?)
;; (defmacro penv []
;;   `(with-context env# ;; ENV OR CTX???
;;      (penv-func env#)))

;; going to start with same-thread breakpoints and build toward multithread


;; for now
(defn under-repl-evaluation? []
  true)

(defn breakpoint-on-this-thread? [state]
  true)

(defmacro break []
  `(let [ure?# (under-repl-evaluation?)
         initial-state# {}]
     (loop [state# initial-state#
            bail# 0]
       (if (< 20 bail#)
         (do (println "bailing")
             state#)
         (when-not (should-exit-signal? state#)
           (if ure?#
             (if (breakpoint-on-this-thread? state#)
               (recur (run-breakpoint-on-this-thread state#) (inc bail#))
               (recur (connect-to-off-thread-breakpoint state#) (inc bail#)))
             (recur (run-breakpoint-receiving-from-off-thread state#) (inc bail#))))))))

(defn should-exit-signal? [signal]
  (and (map? signal)
       (= true (get signal :exit))))

(defn should-connect-signal? [signal]
  (and (map? signal)
       (= true (get signal :connect))))

(defn process-completion-signal [state signal]
  (cond
    (should-exit-signal? signal)
    (assoc state :exit true)

    ;; (should-connect-signal? signal)
    ;; (assoc state :connection (the-connection signal))
    
    :else state))

(defn run-breakpoint-on-this-thread [state]
  (let [completion-signal-ref (atom :initial)
        read (fn read [request-prompt request-exit]
               (let [input (m/repl-read request-prompt request-exit)]
                 (cond
                   (or (= :tl input)
                       (identical? request-exit input))
                   (do (reset! completion-signal-ref {:exit true})
                       request-exit)

                   :else input)))]
    (m/repl
      :read read
      :prompt #(print "debug=> "))
    (process-completion-signal state @completion-signal-ref)))

(defn connect-to-off-thread-breakpoint [state]
  (throw (Exception. "not implemented"))
  (comment
    (let [completion-signal-ref (atom :initial)]
      (obtain-connection state completion-signal-ref)
      (process-completion-signal state @completion-signal-ref))))

(defn run-breakpoint-receiving-from-off-thread [state]
  (throw (Exception. "not implemented"))
  (comment
    (let [connection-signal (await-connection-signal state)]
      (if (should-exit-signal? connection-signal)
        (assoc state :exit true)
        (let [completion-signal-ref (atom :initial)
              read (fn read [prompt exit]
                     (let [input (...)]
                       ...))
              eval (fn eval [input])]
          (binding [*in* (:in-channel connection-signal)]
            (m/repl
              :read read
              :eval eval))
          (process-completion-signal state @completion-signal-ref))))))
