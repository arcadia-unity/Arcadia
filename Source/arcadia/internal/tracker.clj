(ns arcadia.internal.tracker
  (:use clojure.pprint)
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

(defn var-symbol [var]
  (symbol
    (name (.Name (.Namespace var)))
    (name (.Symbol var))))

(defn track [v]
  (letfn [(tracking-fn [v f]
            (fn tracker [& args]
              (try
                (locking tracked-vars
                  (swap! tracked-vars update-in [v ::history]
                    (fn [stuff]
                      (conj stuff {::time DateTime/Now
                                   ::args args}))))
                (let [result (apply f args)]
                  (locking tracked-vars
                    (swap! tracked-vars update-in [v ::history]
                      identity
                      ;; (fn [stuff]
                      ;;   (update stuff (dec (count stuff))
                      ;;     assoc ::return result))
                      ))
                  result)
                (catch Exception e
                  (throw (Exception.
                           (str "stupid problem encountered.\n"
                                (with-out-str
                                  (clojure.pprint/pprint
                                    {:v v
                                     :f f
                                     :args args})))))))))]
    (locking tracked-vars
      (let [{:keys [::raw ::wrapped]} (get @tracked-vars v)
            current @v]
        (when-not (= wrapped current)
          (let [tracked (tracking-fn v current)]
            (alter-var-root v (constantly tracked))
            (swap! tracked-vars assoc v
              {::raw current
               ::wrapped tracked
               ::history []}))))
      v)))

(defn track-all [ns]
  (assert (instance? clojure.lang.Namespace ns))
  (->> (vals (ns-map ns))
       (filter #(= ns (:ns (meta %))))
       (filter #(:arglists (meta %)))
       (remove #(:macro (meta %)))
       (map track)
       dorun))

(defn untrack [v]
  (locking tracked-vars
    (when-let [{:keys [::raw ::wrapped]} (get @tracked-vars v)]
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
     (doseq [v# vars#]
       (track v#))
     (try
       (do ~@body)
       (finally
         (doseq [v# vars#]
           (untrack v#))))))

(defmacro with-track-all [nss & body]
  `(let [nss# ~nss]
     (doseq [ns# nss#]
       (track-all ns#))
     (try
       (do ~@body)
       (finally
         (doseq [ns# nss#]
           (untrack-all ns#))))))

(defn history
  ([] (history @tracked-vars))
  ([trackings]
   (let [record-fn (fn [bldg var {:keys [::history]}]
                     (let [vsym (var-symbol var)]
                       (into bldg
                         (map #(assoc % ::var-symbol vsym))
                         history)))]
     (->> (reduce-kv record-fn [] trackings)
          (sort-by ::time)))))

(defn print-history
  ([] (print-history nil))
  ([trackings]
   (clojure.pprint/pprint
     (for [{:keys [::time ::var-symbol]} (if trackings
                                           (history trackings)
                                           (history))]
       (str var-symbol " at " time)))))

(defn print-rev-history
  ([] (print-history nil))
  ([trackings]
   (clojure.pprint/pprint
     (for [{:keys [::time ::var-symbol]} (reverse
                                           (if trackings
                                             (history trackings)
                                             (history)))]
       (str var-symbol " at " time)))))
