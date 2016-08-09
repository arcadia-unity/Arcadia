(ns arcadia.internal.file-system
  (:require [arcadia.internal.array-utils :as au]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]
            [arcadia.internal.macro :as am])
  (:import [System.IO FileSystemInfo DirectoryInfo FileInfo Path File]))

(as/push-assert false) ;; this is stupid

(s/def ::path string?)

(s/def ::info #(instance? FileSystemInfo %))

(s/def ::info-path (as/qwik-or ::info ::path))

;; ============================================================

(declare info)

(defn path ^String [x]
  (cond
    (instance? FileSystemInfo x) (.FullName x)
    (string? x) (if-let [fsi (info x)]
                  (path fsi)
                  (path (FileInfo. x)))
    :else (throw (ArgumentException.
                   (str "expects FileSystemInfo or string, got "
                     (class x))))))

(defn path-combine [& paths]
  (letfn [(as-path [x]
            (if (instance? FileSystemInfo x)
              (.FullName x)
              x))
          (step
            ([] "")
            ([p] p)
            ([p1 p2]
             (Path/Combine p1 p2)))]
    (transduce (map as-path) step paths)))

(defn path-split [p]
  (vec (. p (Split (au/lit-array System.Char Path/DirectorySeparatorChar)))))

(defn path-supers [path]
  (take-while (complement nil?)
    (iterate #(Path/GetDirectoryName %)
      path)))

(def empty-set #{}) ;;omg

(defn info-children [info]
  {:pre [(as/loud-valid? ::info info)]}
  (condp instance? info
    FileInfo empty-set
    DirectoryInfo (set (.GetFileSystemInfos info))))

(defn file-info [x]
  (cond
    (instance? FileInfo x) x
    (string? x) (try
                  (FileInfo. x)
                  (catch System.ArgumentException e))
    :else (throw (System.ArgumentException. "Expects FileInfo or String."))))

(defn directory-info [x]
  (cond
    (instance? DirectoryInfo x) x
    (string? x) (try
                  (DirectoryInfo. x)
                  (catch System.ArgumentException e))
    :else (throw (System.ArgumentException. "Expects DirectoryInfo or String."))))

;; !!!!THIS SOMETIMES RETURNS NIL!!!!
(defn info ^FileSystemInfo [x]
  {:pre [(as/loud-valid? ::info-path x)]
   :post [(as/loud-valid? (s/or ::info ::info :nil nil?) %)]}
  (cond
    (instance? FileSystemInfo x) x
    ;; Yes I hate it too
    (string? x) (let [fi (file-info x)] 
                  (if (and fi (.Exists fi))
                    fi
                    (let [di (directory-info x)] 
                      (when (and di (.Exists di))
                        di))))
    :else (throw (System.ArgumentException. "Expects FileSystemInfo or String."))))

(defn info-seq [root]
  (tree-seq
    #(instance? DirectoryInfo %)
    (fn [^DirectoryInfo fsi]
      (.GetFileSystemInfos fsi))
    (info root)))

(defn path-seq [root]
  (map (fn [^FileSystemInfo fsi] (.FullName fsi))
    (info-seq root)))

(defn delete [root]
  (am/condcast-> root root
    String (delete (info root))
    DirectoryInfo (.Delete root true)
    FileInfo (.Delete root)
    FileSystemInfo (delete (.FullName root))))

(as/pop-assert)
