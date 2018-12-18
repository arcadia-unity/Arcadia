(assembly-load-from "clojure.tools.namespace.dll")
(assembly-load-from "clojure.data.generators.dll")
(assembly-load-from "clojure.test.generative.dll")
(assembly-load-from "clojure.test.check.dll")

;;;(System/setProperty "java.awt.headless" "true")
(require
 '[clojure.test :as test]
 '[clojure.tools.namespace.find :as ns])
(def namespaces (remove (read-string (System.Environment/GetEnvironmentVariable "clojure.test-clojure.exclude-namespaces"))  ;;; System/getProperty
                        (ns/find-namespaces-in-dir (System.IO.DirectoryInfo. "clojure/test_clojure"))))                      ;;; (java.io.File. "test")
(doseq [ns namespaces] (require ns))
(let [summary (apply test/run-tests namespaces)]
  (Environment/Exit (if (test/successful? summary) 0 -1)))   ;;; System/exit