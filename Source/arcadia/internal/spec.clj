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
;; hacky spec disabler (therefore refiner)

(defonce ^:private registry-ref
  (var-get #'clojure.spec/registry-ref))

;; I know, two atoms, unsafe

(defonce ^:private disabled-refs #{})


(defn disable-spec [k]
  (if (#{:clojure.spec/any ::disabled-refs} k)
    (throw (ArgumentException. (str "no: " k)))
    (swap! registry-ref
      (fn [rr]
        (if (contains? rr k)
          (-> rr
            (assoc k (get rr :clojure.spec/any))
            (update ::disabled-refs assoc k (get rr k)))
          (throw
            (ArgumentException.
              (str "key " k " must be in global registry to be disabled"))))))))

(defn disabled? [k]
  (contains? (::disabled-refs @registry-ref) k))

(defn enable-spec [k]
  (when (disabled? k)
    (swap! registry-ref
      (fn [{:keys [::disabled-refs]}]
        (-> rr
          (update ::disabled-refs dissoc k)
          (assoc k (get disabled-refs k)))))))

