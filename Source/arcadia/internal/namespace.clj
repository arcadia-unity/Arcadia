(ns arcadia.internal.namespace)

(defn quickquire
  "Bit like require, but fails with fewer allocations"
  [ns-sym]
  (when-not (contains? (loaded-libs) ns-sym)
    (require ns-sym)))
