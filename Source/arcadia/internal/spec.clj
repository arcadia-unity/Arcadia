(ns arcadia.internal.spec
  (:require [clojure.spec :as s]))
;; cloying syrupy conveniences for clojure.spec

(defmacro collude [gen el]
  (let [switch (->> `[set? map? seq? vector? list?]
                 (mapcat (fn [p] `[(~p ~gen) ~p]))
                 (cons `cond))]
    `(s/and ~switch (s/coll-of ~el ~gen))))
