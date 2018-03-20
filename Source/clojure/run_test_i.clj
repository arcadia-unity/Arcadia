(assembly-load-from "clojure.tools.namespace.dll")
(assembly-load-from "clojure.data.generators.dll")
(assembly-load-from "clojure.test.generative.dll")
(assembly-load-from "clojure.test.check.dll")

;;;(System/setProperty "java.awt.headless" "true")
(require
 '[clojure.test :as test]
 '[clojure.tools.namespace.find :as ns])
(def namespaces (remove (read-string (or (System.Environment/GetEnvironmentVariable "clojure.test-clojure.exclude-namespaces") "#{}"))    ;;; System/getProperty  Added the or
                        (ns/find-namespaces-in-dir (System.IO.DirectoryInfo. "clojure/test_clojure"))))                                   ;;; (java.io.File. "test")
(doseq [ns namespaces] (require ns))
(let [summary (apply test/run-tests namespaces)]
  (print summary))