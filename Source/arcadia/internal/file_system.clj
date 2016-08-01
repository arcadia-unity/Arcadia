(ns arcadia.internal.file-system
  (:require [arcadia.internal.array-utils :as au])
  (:import [System.IO FileSystemInfo DirectoryInfo FileInfo Path File]))

(defn path-combine [& paths]
  (reduce #(Path/Combine %1 %2) paths))

(defn path-split [path]
  (vec (. path (Split (au/lit-array System.Char Path/DirectorySeparatorChar)))))

(defn path-supers [path]
  (take-while (complement nil?)
    (iterate #(Path/GetDirectoryName %)
      path)))
