(ns ^{:doc "Fix for obscure https bug (#175) on Windows and Linux
           
           Root SSL certificates are not installed by default on Windows,
           and Linux, so making web requests to HTTPS URLs will fail. This
           breaks our package manager. The solution is a command line tool
           called mozroots that downloads the root SLL certificates that
           Mozilla uses in Firefox. We load the assembly and simulate a 
           command line session passing --import --sync as arguments."}
  arcadia.internal.mozroots
  (:import UnityEditor.EditorApplication
           Arcadia.UnityStatusHelper
           System.Reflection.BindingFlags))

(def path-to-mozroots
  (let [editor-root EditorApplication/applicationPath]
    (cond
      UnityStatusHelper/IsUnityEditorOsx
      (str editor-root "/Contents/MonoBleedingEdge/lib/mono/4.5/mozroots.exe")
      UnityStatusHelper/IsUnityEditorWin
      (str editor-root "???? /Editor/Data/Mono/bin/mozroots")
      UnityStatusHelper/IsUnityEditorLinux
      (str editor-root "../Data/MonoBleedingEdge/lib/mono/4.5/mozroots.exe"))))

(assembly-load-file path-to-mozroots)

(import Mono.Tools.MozRoots)

(defn import-sync-mozroots []
  (let [parse-opts (.GetMethod MozRoots "ParseOptions"
                               (enum-or BindingFlags/NonPublic BindingFlags/Static))
        process (.GetMethod MozRoots "Process"
                            (enum-or BindingFlags/NonPublic BindingFlags/Static))]
    (.Invoke parse-opts nil (into-array Object [(into-array ["--import" "--sync"])]))
    (.Invoke process nil (into-array Object []))))

;; windows
;; Program Files/Unity/Editor/Data/Mono/bin/mozroots