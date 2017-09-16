(ns ^{:doc "Fix for obscure https bug (#175, #245) on Windows and Linux
           
           Root SSL certificates are not installed by default on Windows,
           and Linux, so making web requests to HTTPS URLs will fail. This
           breaks our package manager. The solution is a command line tool
           called mozroots that downloads the root SLL certificates that
           Mozilla uses in Firefox. We load the assembly and simulate a 
           command line session passing --import --sync as arguments."}
  arcadia.internal.mozroots
  (:import UnityEditor.EditorApplication
           Arcadia.UnityStatusHelper
           System.Reflection.BindingFlags
           System.IO.Path))

(def path-to-mozroots
  (cond
    UnityStatusHelper/IsUnityEditorOsx
    ;; e.g. "/Applications/Unity/Unity.app"
    (Path/Combine
      EditorApplication/applicationPath 
      "Contents/MonoBleedingEdge/lib/mono/4.5/mozroots.exe")
    UnityStatusHelper/IsUnityEditorWin
    ;; e.g. "D:/Programs/Unity-Win/Editor/Unity.exe"
    (Path/Combine
      (Path/GetDirectoryName EditorApplication/applicationPath) 
      "Data/MonoBleedingEdge/lib/mono/4.5/mozroots.exe")
    UnityStatusHelper/IsUnityEditorLinux
    ;; e.g. "/home/selfsame/unity-editor-2017.1.0xf3Linux/Editor/Unity"
    (Path/Combine
      (Path/GetDirectoryName EditorApplication/applicationPath)
      "Data/MonoBleedingEdge/lib/mono/4.5/mozroots.exe")
    :else
    (throw (Exception. "Path to mozroots could not be determined for this platform"))))

(assembly-load-file path-to-mozroots)

(import 'Mono.Tools.MozRoots)

(let [MozRoots (resolve 'MozRoots)]

  (def parse-options-method
    (.GetMethod MozRoots "ParseOptions"
      (enum-or BindingFlags/NonPublic BindingFlags/Static)))

  (def parse-opts
    (.GetMethod MozRoots "ParseOptions"
      (enum-or BindingFlags/NonPublic BindingFlags/Static)))

  (def process
    (.GetMethod MozRoots "Process"
      (enum-or BindingFlags/NonPublic BindingFlags/Static))))

(def cert-data-file
  "Assets/Arcadia/Infrastructure/certdata.txt")

(def arguments
  (into-array
    Object
    [(into-array ["--file" cert-data-file "--import" "--sync"])]))

(defn import-sync-mozroots []
  (.Invoke parse-opts nil arguments)
  (.Invoke process nil (make-array Object 0)))
