(ns arcadia.internal.editor-callbacks
  (:require [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]))

(def callbacks
  (atom {}))

(defn set-callback [key f]
  (swap! callbacks assoc key f))

(defn remove-callback [key]
  (swap! callbacks dissoc key))

;; Gets run by EditorCallbacks.cs on the main thread
(defn run-callbacks []
  (letfn [(run [_ k f]
            (try
              (f)
              (catch Exception e
                (Debug/Log
                  (str (class e) " encountered while running editor callback " k " :"))
                (Debug/Log e)
                (Debug/Log (str "Removing editor callback " k "."))
                (remove-callback k)))
            nil)]
    (reduce-kv run nil @callbacks)))
