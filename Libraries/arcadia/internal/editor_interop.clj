(ns arcadia.internal.editor-interop
  (:import [System.IO File]))

(defn touch-dlls [^System.String folder]
  (doseq [dll (Directory/GetFiles folder "*.dll")]
    (File/SetLastWriteTime dll DateTime/Now)))