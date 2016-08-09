(ns arcadia.internal.tracker
  (:require [arcadia.internal.map-utils :as mu]
            [arcadia.internal.macro :as am]))

;; No step debugger, must make do with stuff like this.
;; Won't play nicely with other wrappers such as
;; clojure.spec/instrument (they have the same problem; I guess one
;; would need keyable wrapper support baked into clojure to do this
;; right).

;; If you want to use multiple wrappers, consider chiasmus:

;; Should be ok:
;; (instrument x) (track x) (untrack x) (unstrument x)

;; Probably won't be ok:
;; (instrument x) (track x) (unstrument x) (untrack x)

;; To reduce odds of this bug, use with-tracking.

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

(defmacro with-track [vars & body]
  `(let [vars# ~vars]
     (doseq [v# vars#] (track v#))
     (let [res# (do ~@body)]
       (doseq [v# vars#] (untrack v#))
       res#)))

(defmacro with-track-all [nss & body]
  `(let [nss# ~nss]
     (doseq [ns# nss#] (track ns#))
     (let [res# (do ~@body)]
       (doseq [ns# nss#] (untrack ns#))
       res#)))
