;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; Tests added for ClojureCLR -- miscellaneoue

(ns clojure.test-clojure.clr.added
  (:use clojure.test
        [clojure.test.generative :exclude (is)]
        clojure.template)
  (:require [clojure.data.generators :as gen]
            [clojure.test-helper :as helper]))

(deftest test-bit-not
  (are [x y] (= x y)
       -1 (bit-not 0)
	   Int64/MinValue (bit-not 0x7FFFFFFFFFFFFFFF)))