(ns arcadia.internal.tracker
  (:require [arcadia.internal.map-utils :as mu]
            [arcadia.internal.macro :as am]))

;; no step debugger, must make do with stuff like this.
;; sneaking suspicion this doesn't play nice with clojure.spec/instrument yet
;; therefore also that clojure.spec/instrument has a slightly jank implementation

;; to do this right (ie in a way that composes with other things like
;; it, such as clojure.spec/instrument) should build out wrapper
;; instances a bit. here's a first step for wrapping clojure.lang.IFn:

;; (defn- wrapped-arities [side-fx fsym]
;;   (letfn [(base [args]
;;             `(~args ~(cons fsym args)))]
;;     (->> (am/arities-forms base
;;            {::am/max-args 21
;;             ::am/cases {21 (fn [[args]]
;;                              `([this# ~@args]
;;                                ~side-fx
;;                                (apply ~fsym ~@args)))}})
;;          (map #(cons 'invoke %))
;;          (cons `(applyTo [this# arglist#]
;;                   ~side-fx
;;                   (apply ~fsym arglist#)))
;;          vec)))

;; "Map for tracked vars to :raw/:wrapped fns"
(defonce tracked-vars
  (atom {}))

(defn track [v]
  (letfn [(tracking-fn [v f]
            (fn tracker [& args]
              (locking tracked-vars
                (swap! tracked-vars update v assoc :last DateTime/Now))
              (apply f args)))]
    (locking tracked-vars
      (let [{:keys [raw wrapped]} (get @tracked-vars v)
            current @v]
        (when-not (= wrapped current)
          (let [tracked (tracking-fn v current)]
            (alter-var-root v (constantly tracked))
            (swap! tracked-vars assoc v {:raw current :wrapped tracked}))))
      v)))

(defn track-all [ns]
  (assert (instance? clojure.lang.Namespace ns))
  (->> (vals (ns-map ns))
       (filter #(= ns (:ns (meta %))))
       (filter #(:arglists (meta %)))
       (remove #(:macro (meta %)))
       (map track)
       dorun))

(defn history []
  (->> (-> @tracked-vars
           (mu/map-vals :last)
           (mu/map-keys (comp :name meta)))
       (remove #(nil? (val %)))
       (sort-by val)
       (map #(zipmap [::name ::last-call] %))))

(defn untrack [v]
  (locking tracked-vars
    (when-let [{:keys [raw wrapped]} (get @tracked-vars v)]
      (let [current @v]
        (when (= wrapped current)
          (alter-var-root v (constantly raw))))
      (swap! tracked-vars dissoc v))
    v))

(defn untrack-all
  ([]
   (doseq [v (keys @tracked-vars)]
     (untrack v)))
  ([ns]
   (doseq [v (->> (keys @tracked-vars)
                  (filter #(= ns (:ns (meta %)))))]
     (untrack v))))
