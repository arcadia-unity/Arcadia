(ns arcadia.internal.protocols)

(defprotocol IDeleteableElements
  (delete! [this key]))

(defprotocol ISnapshotable
  (snapshot [self])
  (snapshotable? [self]))

(extend-protocol ISnapshotable

  System.Object
  (snapshotable? [self] false)
  
  System.ValueType
  (snapshotable? [self] false)
  
  nil
  (snapshotable? [self] false))

(defmulti mutable
  (fn mutable-dispatch [{t :arcadia.core/mutable-type}] t))

(defprotocol IMutable
  (mut!
    [_]
    [_ _]
    [_ _ _]
    [_ _ _ _]
    [_ _ _ _ _]
    [_ _ _ _ _ _]
    [_ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]
    [_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _]))


