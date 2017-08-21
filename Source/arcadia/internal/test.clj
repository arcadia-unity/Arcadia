(ns arcadia.internal.test
  (:require [clojure.test :as test]))

(defn run-tests [& args]
  (binding [test/*test-out* *out*]
    (apply test/run-tests args)))
