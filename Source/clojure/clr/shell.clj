;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns 
  ^{:author "Shawn Hoover",
    :doc "Conveniently launch a sub-process providing its stdin and collecting
its stdout. Ported from clojure.java.shell, written by Chris Houser and Stuart
Halloway."}
  clojure.clr.shell
(:use [clojure.clr.io :only (copy string->encoding)]
      [clojure.string :only (join)])
(:import (System.Diagnostics Process ProcessStartInfo)
         (System.IO MemoryStream StringWriter)
         (System.Text Encoding)))

(def ^:dynamic *sh-dir* nil)
(def ^:dynamic *sh-env* nil)

(defmacro with-sh-dir
  "Sets the directory for use with sh, see sh for details."
  {:added "1.2"}
  [dir & forms]
  `(binding [*sh-dir* ~dir]
     ~@forms))

(defmacro with-sh-env
  "Sets the environment for use with sh, see sh for details."
  {:added "1.2"}
  [env & forms]
  `(binding [*sh-env* ~env]
     ~@forms))

(defn- parse-args
  [args]
  (let [default-encoding "UTF-8" ;; see sh doc string
        default-opts {:out-enc default-encoding
                      :in-enc default-encoding
                      :dir *sh-dir*
                      :env *sh-env*}
        [cmd opts] (split-with string? args)]
    [cmd (merge default-opts (apply hash-map opts))]))

(defn- stream-to-bytes
  [in]
  (with-open [bout (MemoryStream.)]
    (copy in bout)
    (.ToArray bout)))

(defn- stream-to-string
  ([in] (stream-to-string in (.WebName Encoding/Default)))
  ([in enc]
     (with-open [sout (StringWriter.)]
       (copy in sout :encoding enc)
       (str sout))))

(defn- stream-to-enc
  [stream enc]
  (if (= enc :bytes)
    (stream-to-bytes stream)
    (stream-to-string stream enc)))

(defn- make-process-info [[cmd & args] out-enc env dir]
  (let [info
        (doto (ProcessStartInfo. cmd)
          (.set_UseShellExecute false) ; Required for redirecting IO
          (.set_Arguments (join " " args))
          (.set_RedirectStandardInput true)
          (.set_RedirectStandardOutput true)
          (.set_RedirectStandardError true)
          (.set_StandardOutputEncoding (string->encoding out-enc)))]
    (when dir
      (.set_WorkingDirectory info (str dir)))
    (doseq [[k v] env]
      (.Add (.EnvironmentVariables info) k v))
    info))

(defn sh
  "Passes the given strings to a new Process to launch a sub-process.

  Options are

  :in      may be given followed by a String or byte array specifying input
           to be fed to the sub-process's stdin.
  :in-enc  option may be given followed by a String, used as a character
           encoding name (for example \"UTF-8\" or \"ISO-8859-1\") to
           convert the input string specified by the :in option to the
           sub-process's stdin.  Defaults to UTF-8.
           If the :in option provides a byte array, then the bytes are passed
           unencoded, and this option is ignored.
  :out-enc option may be given followed by :bytes or a String. If a
           String is given, it will be used as a character encoding
           name (for example \"UTF-8\" or \"ISO-8859-1\") to convert
           the sub-process's stdout to a String which is returned.
           If :bytes is given, the sub-process's stdout will be stored
           in a byte array and returned.  Defaults to UTF-8.
  :env     override the process env with a map.
  :dir     override the process dir with a String or FileInfo.

  You can bind :env or :dir for multiple operations using with-sh-env
  and with-sh-dir.

  sh returns a map of
    :exit => sub-process's exit code
    :out  => sub-process's stdout (as byte[] or String)
    :err  => sub-process's stderr (String via platform default encoding)"
  {:added "1.2"}
  [& args]
  (let [[cmd opts] (parse-args args)
        {:keys [in in-enc out-enc]} opts
        ^ProcessStartInfo process-info (make-process-info cmd out-enc (:env opts) (:dir opts))
        proc (Process/Start process-info)]
    (future
     (with-open [stdin (.StandardInput proc)]
       (when in
         (copy in stdin))))
    (with-open [stdout (.StandardOutput proc)
                stderr (.StandardError proc)]
      (let [out (future (stream-to-enc stdout out-enc))
            err (future (stream-to-string stderr))]
        (.WaitForExit proc)
        {:exit (.ExitCode proc) :out @out :err @err}))))

(comment

(println (sh "ls" "-l"))
(println (sh "ls" "-l" "/no-such-thing"))
(println (sh "sed" "s/[aeiou]/oo/g" :in "hello there\n"))
(println (sh "cat" :in "x\u25bax\n"))
(println (sh "cat" :in (.GetBytes Encoding/UTF8 "x\u25bax\n")))
(println (sh "echo" "x\u25bax"))
(println (sh "echo" "x\u25bax" :out-enc "ISO-8859-1")) ; reads 4 single-byte chars
(println (sh "cmd" "/c echo %a% %b%" :env {"a" "hello" "b" "world"}))
(println (-> (sh "echo" "hello" :out-enc :bytes) :out seq))
(println (sh "cat" "myimage.png" :out-enc :bytes)) ; reads binary file into bytes[]
(println (sh "cmd" "/c dir"))
(println (sh "cmd" "/c dir 1>&2"))
(println (sh "cmd" "/c dir" :dir "/"))
)