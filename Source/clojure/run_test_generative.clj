(assembly-load-from "clojure.tools.namespace.dll")
(assembly-load-from "clojure.data.generators.dll")
(assembly-load-from "clojure.test.generative.dll")
(assembly-load-from "clojure.test.check.dll")

(when-not (System.Environment/GetEnvironmentVariable "clojure.test.generative.msec")  ;;; System/getProperty
  (System.Environment/SetEnvironmentVariable "clojure.test.generative.msec" "60000")) ;;; System/setProperty 
(require '[clojure.test.generative.runner :as runner])
(runner/-main "clojure/test_clojure")