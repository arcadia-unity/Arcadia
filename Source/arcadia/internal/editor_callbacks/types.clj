(ns arcadia.internal.editor-callbacks.types
  (:import [Arcadia EditorCallbacks EditorCallbacks+IntervalData]))

(defrecord RepeatingCallbacks [map, arr])

(defn build-repeating-callbacks [m]
  (RepeatingCallbacks. m (into-array (vals m))))

(defn remove-repeating-callback [^RepeatingCallbacks rcs, k]
  (build-repeating-callbacks (dissoc (.map rcs) k)))

(defn add-repeating-callback [^RepeatingCallbacks rcs, k, f, interval]
  (build-repeating-callbacks
    (assoc (.map rcs) k
      (EditorCallbacks+IntervalData. f interval k))))

