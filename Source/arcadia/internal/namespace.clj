(ns arcadia.internal.namespace
  (:require [clojure.spec :as s]))

(s/fdef quickquire
  :args (s/cat :ns-sym symbol?))

(defn quickquire
  "Bit like require, but fails with fewer allocations"
  [ns-sym]
  (when-not (contains? (loaded-libs) ns-sym)
    (require ns-sym)))
