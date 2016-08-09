(ns arcadia.internal.spec
  (:refer-clojure :exclude [def])
  (:require [clojure.spec :as s])
  (:import [UnityEngine Debug]))
;; cloying syrupy conveniences for clojure.spec

(defmacro collude [gen el]
  (let [switch (->> `[set? map? seq? vector? list?]
                 (mapcat (fn [p] `[(~p ~gen) ~p]))
                 (cons `cond))]
    `(s/and ~switch (s/coll-of ~el ~gen))))

(defmacro qwik-cat [& things]
  (list* `s/cat (interleave things things)))

(defmacro qwik-or [& things]
  (list* `s/or (interleave things things)))

(defmacro qwik-alt [& things]
  (list* `s/alt (interleave things things)))

(defmacro qwik-conform [[lcl spec src] & body]
  `(let [src# ~src
         spec# ~spec
         lcltemp# (s/conform spec# src#)]
     (if (= ::s/invalid lcltemp#)
       (throw (Exception.
                (str "invalid thing:\n"
                  (with-out-str (s/explain spec# src#)))))
       (let [~lcl lcltemp#]
         ~@body))))

(defn loud-valid? [spec val]
  (or (s/valid? spec val)
    (Debug/Log
      (with-out-str
        (s/explain spec val)))
    false))

;; ============================================================
;; arrgh

(def assert-stack (atom [*assert*]))

(defn push-assert [state]
  (set! *assert*
    (peek
      (swap! assert-stack conj state))))

(defn pop-assert []
  (set! *assert*
    (peek
      (swap! assert-stack pop))))

;; ============================================================
;; hacky spec disabler (therefore refiner)

;; (defonce ^:private own-private-any
;;   (fn own-private-any [& xs] true))

;; (defonce ^:private registry-ref
;;   (var-get #'clojure.spec/registry-ref))

;; (defn disable-spec [k]
;;   (if (= ::disabled-refs k)
;;     (throw (ArgumentException. (str "no: " k)))
;;     (swap! registry-ref
;;       (fn [rr]
;;         (if (contains? rr k)
;;           (-> rr
;;             (update ::disabled-refs assoc k (get rr k))
;;             (assoc k own-private-any))
;;           rr)))))

;; (defn disabled? [k]
;;   (let [rr @registry-ref]
;;     (and (contains? (::disabled-refs rr) k)
;;          (= own-private-any (get rr k)))))

;; (defn- enable-spec-inner [{:keys [::disabled-refs] :as rr}, k]
;;   (as-> rr rr
;;     (update rr ::disabled-refs disj k)
;;     (if (= own-private-any (get rr k))
;;       (assoc rr k (get disabled-refs k))
;;       rr)))

;; (defn enable-spec [k]
;;   (when (disabled? k)
;;     (swap! registry-ref enable-spec-inner k)))

;; (defn enable-all-specs []
;;   (swap! registry-ref
;;     (fn [{:keys [::disabled-refs] :as m}]
;;       (reduce enable-spec-inner m (keys disabled-refs)))))

;; (defn ns-specs
;;   ([] (ns-specs *ns*))
;;   ([ns]
;;    (let [n (name (.Name ns))]
;;      (filter
;;        #(= n (namespace %))
;;        (keys @registry-ref)))))
