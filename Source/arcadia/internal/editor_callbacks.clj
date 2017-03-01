(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]))

(defonce callbacks
  (atom {}))

(defn set-callback
  ([key f]
   (set-callback key f nil))
  ([key f {:keys [::run-once] :as opts}]
   (swap! callbacks assoc key
     (as-> {::callback f} m
           (if run-once
             (assoc m ::run-once true)
             m)))))

(defn remove-callback [key]
  (swap! callbacks dissoc key))

(defn run-callbacks-inner [_ k {:keys [::callback ::run-once]}]
  (try
    (callback)
    (catch Exception e
      (Debug/Log
        (str (class e) " encountered while running editor callback " k " :"))
      (Debug/Log e)
      (Debug/Log (str "Removing editor callback " k "."))
      (remove-callback k))
    (finally
      (when run-once
        (remove-callback k))))
  nil)

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  (reduce-kv run-callbacks-inner nil @callbacks))
