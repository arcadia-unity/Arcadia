(ns ^{:doc "To get around Unity's frequent resetting of the VM and the
           impact that has on editor-repl connections, we spawn an external
           repl proxy server that copies bytes between editors and arcadia in
           normal usage and does not crash the editor when Unity resets the VM."}
  arcadia.internal.repl-proxy
  (:require [clojure.string :as string])
  (:import Arcadia.UnityStatusHelper
           UnityEditor.EditorApplication
           System.IO.Path
           [System.Diagnostics
            Process ProcessStartInfo]))

(defn correct-slashes [s]
  (if UnityStatusHelper/IsUnityEditorWin
    (string/replace s #"/" "\\")
    s))

(def path-to-mono
  (cond
    UnityStatusHelper/IsUnityEditorOsx
    ;; e.g. "/Applications/Unity/Unity.app"
    (Path/Combine
      EditorApplication/applicationPath 
      "Contents/MonoBleedingEdge/bin/mono")
    UnityStatusHelper/IsUnityEditorWin
    ;; e.g. "D:/Programs/Unity-Win/Editor/Unity.exe"
    (Path/Combine
      (Path/GetDirectoryName EditorApplication/applicationPath) 
      "Data/MonoBleedingEdge/bin/mono.exe")
    UnityStatusHelper/IsUnityEditorLinux
    ;; e.g. "/home/selfsame/unity-editor-2017.1.0xf3Linux/Editor/Unity"
    (Path/Combine
      (Path/GetDirectoryName EditorApplication/applicationPath)
      "Data/MonoBleedingEdge/bin/mono")))

(def path-to-proxy-exe
  "Arcadia/Infrastructure/ReplProxyServer.exe")

(defn launch [internal-port external-port]
  (let [psi (ProcessStartInfo.)
        proc (Process.)]
    (set! (.FileName psi) (correct-slashes path-to-mono))
    (set! (.RedirectStandardOutput psi) true)
    (set! (.RedirectStandardError psi) true)
    (set! (.UseShellExecute psi) false)
    (set! (.WorkingDirectory psi) (correct-slashes UnityEngine.Application/dataPath))
    (set! (.CreateNoWindow psi) true)
    (set! (.Arguments psi) (str (correct-slashes path-to-proxy-exe)
                                " localhost " internal-port
                                " localhost " external-port))
    (set! (.StartInfo proc) psi)
    (.Start proc)))