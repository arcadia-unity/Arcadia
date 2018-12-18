(ns arcadia.internal.nudge
  (:require [arcadia.internal.editor-callbacks :as ec]
            [arcadia.internal.state :as state]
            [arcadia.internal.config :as config]
            [arcadia.internal.asset-watcher :as aw])
  (:import [UnityEditor EditorApplication]))

;; The repl sometimes becomes unresponsive or very slow when Unity
;; loses focus. Since Unity loses focus whenever we're using the repl,
;; this is a major problem. The bug is definitely on the Unity
;; side. It seems that Unity suspends much of its operation when focus
;; is lost, and at a deep enough level that this affects at least the
;; Editor callback loop upon which the repl depends.

;; Through experimentation, it seems that Unity can be tricked into
;; not suspending its operation if visible components of its user
;; interface are periodically repainted. For this "nudge" to work, it
;; seems that at least one o the nudged UI components must be
;; displaying pixels on the monitor. If they are all fully occluded by
;; other windows, the repl may still be subject to freezing or
;; slowdown.

;; To enable the repainting trick, call `start-nudge`, or set the
;; `:nudge` option in your configuration.edn file to `true`. `false`
;; will turn it off.

(defn nudge []
  (EditorApplication/RepaintProjectWindow)
  (UnityEditor.SceneView/RepaintAll))

(defn start-nudge []
  (ec/add-repeating-callback ::nudge #'nudge 2000))

(defn stop-nudge []
  (ec/remove-repeating-callback ::nudge))

;; ============================================================
;; reactive

(defn- update-reactive [{:keys [nudge]}]
  (if nudge
    (start-nudge)
    (stop-nudge)))

(update-reactive (config/config))

(state/add-listener ::config/on-update ::update-reactive #'update-reactive)
