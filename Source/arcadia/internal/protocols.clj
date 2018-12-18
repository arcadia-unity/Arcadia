(ns arcadia.internal.protocols)

(defprotocol IDeleteableElements
  (delete! [this key]))
