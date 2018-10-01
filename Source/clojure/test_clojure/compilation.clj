;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Frantisek Sodomka

(assembly-load-from "clojure.test_clojure.compilation.line_number_examples.clj.dll")           ;;; DM:Added
(assembly-load-from "clojure.test_clojure.compilation.load_ns.clj.dll")                        ;;; DM:Added
(ns clojure.test-clojure.compilation
  (:import (clojure.lang Compiler Compiler+CompilerException))                                 ;;; Compiler$CompilerException
  (:require [clojure.test.generative :refer (defspec)]
		    [clojure.data.generators :as gen]
			[clojure.test-clojure.compilation.line-number-examples :as line]
			clojure.string)                                                                    ;;; DM:Added -- seem to have an order dependency that no longer works.
  (:use clojure.test
        [clojure.test-helper :only (should-not-reflect should-print-err-message)]))

; http://clojure.org/compilation

; compile
; gen-class, gen-interface


(deftest test-compiler-metadata
  (let [m (meta #'when)]
    (are [x y]  (= x y)
        (list? (:arglists m)) true
        (> (count (:arglists m)) 0) true

        (string? (:doc m)) true
        (> (.Length (:doc m)) 0) true             ;;; .length

        (string? (:file m)) true
        (> (.Length (:file m)) 0) true            ;;; .length

       (integer? (:line m)) true
       (> (:line m) 0) true

        (integer? (:column m)) true
        (> (:column m) 0) true

        (:macro m) true
        (:name m) 'when )))

;;;(deftest test-embedded-constants
;;;  (testing "Embedded constants"
;;;    (is (eval `(= Boolean/TYPE ~Boolean/TYPE)))
;;;    (is (eval `(= Byte/TYPE ~Byte/TYPE)))
;;;    (is (eval `(= Character/TYPE ~Character/TYPE)))
;;;    (is (eval `(= Double/TYPE ~Double/TYPE)))
;;;    (is (eval `(= Float/TYPE ~Float/TYPE)))
;;;    (is (eval `(= Integer/TYPE ~Integer/TYPE)))
;;;    (is (eval `(= Long/TYPE ~Long/TYPE)))
;;;    (is (eval `(= Short/TYPE ~Short/TYPE)))))

(deftest test-compiler-resolution
  (testing "resolve nonexistent class create should return nil (assembla #262)"
    (is (nil? (resolve 'NonExistentClass.)))))

(deftest test-no-recur-across-try
  (testing "don't recur to function from inside try"
    (is (thrown? Compiler+CompilerException
                 (eval '(fn [x] (try (recur 1)))))))
  (testing "don't recur to loop from inside try"
    (is (thrown? Compiler+CompilerException
                 (eval '(loop [x 5]
                          (try (recur 1)))))))
  (testing "don't recur to loop from inside of catch inside of try"
    (is (thrown? Compiler+CompilerException
                 (eval '(loop [x 5]
                          (try
                            (catch Exception e
                              (recur 1))))))))
  (testing "don't recur to loop from inside of finally inside of try"
    (is (thrown? Compiler+CompilerException
                 (eval '(loop [x 5]
                          (try
                            (finally
                              (recur 1))))))))
  (testing "don't get confused about what the recur is targeting"
    (is (thrown? Compiler+CompilerException
                 (eval '(loop [x 5]
                          (try (fn [x]) (recur 1)))))))
  (testing "don't allow recur across binding"
    (is (thrown? Compiler+CompilerException
                 (eval '(fn [x] (binding [+ *] (recur 1)))))))
  (testing "allow loop/recur inside try"
    (is (= 0 (eval '(try (loop [x 3]
                           (if (zero? x) x (recur (dec x)))))))))
  (testing "allow loop/recur fully inside catch"
    (is (= 3 (eval '(try
                      (throw (Exception.))
                      (catch Exception e
                        (loop [x 0]
                          (if (< x 3) (recur (inc x)) x))))))))
  (testing "allow loop/recur fully inside finally"
    (is (= "012" (eval '(with-out-str
                          (try
                            :return-val-discarded-because-of-with-out-str
                            (finally (loop [x 0]
                                       (when (< x 3)
                                         (print x)
                                         (recur (inc x)))))))))))
  (testing "allow fn/recur inside try"
    (is (= 0 (eval '(try
                      ((fn [x]
                         (if (zero? x)
                           x
                           (recur (dec x))))
                       3)))))))

;; disabled until build box can call java from mvn
#_(deftest test-numeric-dispatch
  (is (= "(int, int)" (TestDispatch/someMethod (int 1) (int 1))))
  (is (= "(int, long)" (TestDispatch/someMethod (int 1) (long 1))))
  (is (= "(long, long)" (TestDispatch/someMethod (long 1) (long 1)))))

(deftest test-CLJ-671-regression
  (testing "that the presence of hints does not cause the compiler to infinitely loop"
    (letfn [(gcd [x y]
              (loop [x (long x) y (long y)]
                (if (== y 0)
                  x
                  (recur y ^Int64 (rem x y)))))]            ;;; ^Long
      (is (= 4 (gcd 8 100))))))

;; ensure proper use of hints / type decls

(defn hinted
  (^String [])
  (^Exception [a])                                                ;;; ^Integer
  (^System.Collections.IList [a & args]))                         ;;; ^java.util.List

;; fn names need to be fully-qualified because should-not-reflect evals its arg in a throwaway namespace

(deftest recognize-hinted-arg-vector
  (should-not-reflect #(.Substring (clojure.test-clojure.compilation/hinted) 0))                                  ;;; .substring
  (should-not-reflect #(.Data (clojure.test-clojure.compilation/hinted "arg")))                                   ;;; .floatValue
  (should-not-reflect #(.Count (clojure.test-clojure.compilation/hinted :many :rest :args :here))))               ;;; .size

(deftest CLJ-1232-qualify-hints
  (let [arglists (-> #'clojure.test-clojure.compilation/hinted meta :arglists)]
    (is (= 'String (-> arglists first meta :tag)))                                                         ;;; java.lang.String
    (is (= 'Exception (-> arglists second meta :tag)))))                                                   ;;; java.lang.Integer

(deftest CLJ-1232-return-type-not-imported
  (is (thrown-with-msg? Compiler+CompilerException #"Unable to resolve typename: Closeable"               ;;; Compiler$CompilerException  classname
                        (eval '(defn a ^Closeable []))))
  (is (thrown-with-msg? Compiler+CompilerException #"Unable to resolve typename: Closeable"               ;;; Compiler$CompilerException  classname
                        (eval '(defn a (^Closeable []))))))
 
 (defn ^String hinting-conflict ^Exception [])                                                                     ;;; ^Integer

(deftest calls-use-arg-vector-hint
  (should-not-reflect #(.Data (clojure.test-clojure.compilation/hinting-conflict)))                               ;;; .floatValue
  (should-print-err-message #"(?s)Reflection warning.*"
    #(.Substring (clojure.test-clojure.compilation/hinting-conflict) 0)))                                         ;;; .substring

(deftest deref-uses-var-tag
  (should-not-reflect #(.Substring clojure.test-clojure.compilation/hinting-conflict 0))                          ;;; .substring
  (should-print-err-message #"(?s)Reflection warning.*"
    #(.Data clojure.test-clojure.compilation/hinting-conflict)))                                                  ;;; .floatValue

(defn ^String legacy-hinting [])

(deftest legacy-call-hint
  (should-not-reflect #(.Substring (clojure.test-clojure.compilation/legacy-hinting) 0)))                          ;;; .substring

(defprotocol HintedProtocol
  (hintedp ^String [a]
           ^Exception [a b]))                                                                                      ;;; ^Integer

(deftest hinted-protocol-arg-vector
  (should-not-reflect #(.Substring (clojure.test-clojure.compilation/hintedp "") 0))                              ;;; .substring
  (should-not-reflect #(.Data (clojure.test-clojure.compilation/hintedp :a :b))))                                 ;;; .floatValue
   
(defn primfn
  (^long [])
  (^double [a]))

(deftest primitive-return-decl
  (should-not-reflect #(loop [k 5] (recur (clojure.test-clojure.compilation/primfn))))
  (should-not-reflect #(loop [k 5.0] (recur (clojure.test-clojure.compilation/primfn 0))))

  (should-print-err-message #"(?s).*k is not matching primitive.*"
    #(loop [k (clojure.test-clojure.compilation/primfn)] (recur :foo))))

#_(deftest CLJ-1154-use-out-after-compile
  ;; This test creates a dummy file to compile, sets up a dummy
  ;; compiled output directory, and a dummy output stream, and
  ;; verifies the stream is still usable after compiling.
  (spit "test/dummy.clj" "(ns dummy)")
  (try
    (let [compile-path (System/getProperty "clojure.compile.path")
          tmp (java.io.File. "tmp")
          new-out (java.io.OutputStreamWriter. (java.io.ByteArrayOutputStream.))]
      (binding [clojure.core/*out* new-out]
        (try
          (.mkdir tmp)
          (System/setProperty "clojure.compile.path" "tmp")
          (clojure.lang.Compile/main (into-array ["dummy"]))
          (println "this should still work without throwing an exception" )
          (finally
            (if compile-path
              (System/setProperty "clojure.compile.path" compile-path)
              (System/clearProperty "clojure.compile.path"))
            (doseq [f (.listFiles tmp)]
              (.delete f))
            (.delete tmp)))))
    (finally
      (doseq [f (.listFiles (java.io.File. "test"))
              :when (re-find #"dummy.clj" (str f))]
        (.delete f)))))

(deftest CLJ-1184-do-in-non-list-test
  (testing "do in a vector throws an exception"
    (is (thrown? Compiler+CompilerException                                          ;;; Compiler$CompilerException
                 (eval '[do 1 2 3]))))
  (testing "do in a set throws an exception"
    (is (thrown? Compiler+CompilerException                                          ;;; Compiler$CompilerException
                 (eval '#{do}))))

  ;; compile uses a separate code path so we have to call it directly
  ;; to test it
  (letfn [(compile [s]         (System.IO.Directory/CreateDirectory "test/clojure")               ;;; DM: Added the CreateDirectory
            (spit "test/clojure/bad_def_test.clj" (str "(ns test.clojure.bad-def-test)\n" s))     ;;; DM: Added test. to ns
            (try
             (binding [*compile-path* "test"]
               (clojure.core/compile 'test.clojure.bad-def-test))                                 ;;; DM: Added test. to name
             (finally
               (doseq [f (.GetFiles (System.IO.DirectoryInfo. "test/clojure"))                    ;;; .listFiles java.io.File.
                       :when (re-find #"bad_def_test" (str f))]
                 (.Delete f)))))]
    (testing "do in a vector throws an exception in compilation"
      (is (thrown? Compiler+CompilerException (compile "[do 1 2 3]"))))                           ;;; Compiler$CompilerException
    (testing "do in a set throws an exception in compilation"
      (is (thrown? Compiler+CompilerException (compile "#{do}"))))))                              ;;; Compiler$CompilerException

(defn gen-name []
  ;; Not all names can be correctly demunged. Skip names that contain
  ;; a munge word as they will not properly demunge.
  (let [munge-words (remove clojure.string/blank?
                            (conj (map #(clojure.string/replace % "_" "")
                                       (vals Compiler/CHAR_MAP)) "_"))]
    (first (filter (fn [n] (not-any? #(>= (.IndexOf n %) 0) munge-words))                            ;;; indexOf
                   (repeatedly #(name (gen/symbol (constantly 10))))))))

(defn munge-roundtrip [n]
  (Compiler/demunge (Compiler/munge n)))

(defspec test-munge-roundtrip
  munge-roundtrip
  [^{:tag clojure.test-clojure.compilation/gen-name} n]
  (assert (= n %)))
  
(deftest test-fnexpr-type-hint
  (testing "CLJ-1378: FnExpr should be allowed to override its reported class with a type hint."
    ;;;(is (thrown? Compiler$CompilerException
    ;;;             (load-string "(.submit (java.util.concurrent.Executors/newCachedThreadPool) #())")))
    ;;;(is (try (load-string "(.submit (java.util.concurrent.Executors/newCachedThreadPool) ^Runnable #())")
    ;;;         (catch Compiler$CompilerException e nil))))
	(is (thrown? Microsoft.Scripting.ArgumentTypeException
	             (try (load-string "(System.Threading.Thread. #())")
				   (catch Compiler+CompilerException e (throw (.InnerException e))))))
	(is (thrown? InvalidCastException
	             (try (load-string "(System.Threading.Thread. ^System.Threading.ThreadStart #())") 
				   (catch Compiler+CompilerException e (throw (.InnerException e))))))			   	 
			 ))

(defn ^{:tag 'long} hinted-primfn [^long x] x)
(defn unhinted-primfn [^long x] x)
(deftest CLJ-1533-primitive-functions-lose-tag
  (should-not-reflect #(Math/Abs (clojure.test-clojure.compilation/hinted-primfn 1)))                ;;; Math/abs
  (should-not-reflect #(Math/Abs ^long (clojure.test-clojure.compilation/unhinted-primfn 1))))       ;;; Math/abs



(defrecord Y [a])
#clojure.test_clojure.compilation.Y[1]
(defrecord Y [b])

(binding [*compile-path* "."]              ;;; "target/test-classes"
  (compile 'clojure.test-clojure.compilation.examples))

#_(deftest test-compiler-line-numbers                   ;;; DM: TODO :: Improve Compiler source information.  And then do https://github.com/clojure/clojure/commit/715754d3f69e85b07fa56047f0d43d400ab36fce
  (let [fails-on-line-number? (fn [expected function]
                                 (try
                                   (function)
                                   nil
                                   (catch Exception t                                                                    ;;; Throwable
                                     (let [frames (filter #(= "line_number_examples.clj" (.GetFileName %))               ;;; .getFileName
                                                          (.GetFrames (System.Diagnostics.StackTrace. t true)))          ;;; (.getStackTrace t))
                                           _ (if (zero? (count frames))
                                               (Console/WriteLine (.ToString t))                                         ;;; (.printStackTrace t)
                                               )
                                           actual (.GetFileLineNumber ^System.Diagnostics.StackFrame (first frames))]    ;;; .getLineNumber ^StackTraceElement
                                       (= expected actual)))))]
    (is (fails-on-line-number?  13 line/instance-field))
    (is (fails-on-line-number?  19 line/instance-field-reflected))
    (is (fails-on-line-number?  25 line/instance-field-unboxed))
    #_(is (fails-on-line-number?  32 line/instance-field-assign))
    (is (fails-on-line-number?  40 line/instance-field-assign-reflected))
    #_(is (fails-on-line-number?  47 line/static-field-assign))
    (is (fails-on-line-number?  54 line/instance-method))
    (is (fails-on-line-number?  61 line/instance-method-reflected))
    (is (fails-on-line-number?  68 line/instance-method-unboxed))
    (is (fails-on-line-number?  74 line/static-method))
    (is (fails-on-line-number?  80 line/static-method-reflected))
    (is (fails-on-line-number?  86 line/static-method-unboxed))
    (is (fails-on-line-number?  92 line/invoke))
    (is (fails-on-line-number? 101 line/threading))
    (is (fails-on-line-number? 112 line/keyword-invoke))
    (is (fails-on-line-number? 119 line/invoke-cast))))

(deftest CLJ-979
  (is (= clojure.test_clojure.compilation.examples.X
         (class (clojure.test-clojure.compilation.examples/->X))))
  (is (.b (clojure.test_clojure.compilation.Y. 1)))
  (is (= clojure.test_clojure.compilation.examples.T
         (class (clojure.test_clojure.compilation.examples.T.))
         (class (clojure.test-clojure.compilation.examples/->T)))))

(deftest clj-1208
  ;; clojure.test-clojure.compilation.load-ns has not been loaded
  ;; so this would fail if the deftype didn't load it in its static
  ;; initializer as the implementation of f requires a var from
  ;; that namespace
  (is (= 1 (.f (clojure.test_clojure.compilation.load_ns.x.)))))

(deftest clj-1568
  (let [compiler-fails-at?
          (fn [row col source]
            (try
              (Compiler/load (System.IO.StringReader. source) "clj-1568.example" (name (gensym "clj-1568.example-")) "clj-1568.example")       ;;; java.io.StringReader, added extra arg
              nil
              (catch Compiler+CompilerException e                                                                                              ;;; Compiler$CompilerException
                (re-find (re-pattern (str ".*:" row ":" col "\\)\\z"))                                                                          ;;; "^.*:" row ":" col "\\)$"
                         (.Message e)))))]                                                                                                     ;;; .getMessage
    (testing "with error in the initial form"
      (are [row col source] (compiler-fails-at? row col source)
           ;; note that the spacing of the following string is important
           1  4 "   (.foo nil)"
           2 18 "
                 (/ 1 0)"))
    (testing "with error in an non-initial form"
      (are [row col source] (compiler-fails-at? row col source)
           ;; note that the spacing of the following string is important
           3 18 "(:foo {})

                 (.foo nil)"
           4 20 "(ns clj-1568.example)


                   (/ 1 0)"))))

(deftype CLJ1399 [munged-field-name])

(deftest clj-1399
  ;; throws an exception on failure
  (is (eval `(fn [] ~(CLJ1399. 1)))))

(deftest CLJ-1250-this-clearing
  (testing "clearing during try/catch/finally"
    (let [closed-over-in-catch (let [x :foo]
                                 (fn []
                                   (try
                                     (throw (Exception. "boom"))
                                     (catch Exception e
                                       x)))) ;; x should remain accessible to the fn

          a (atom nil)
          closed-over-in-finally (fn []
                                   (try
                                     :ret
                                     (finally
                                       (reset! a :run))))]
      (is (= :foo (closed-over-in-catch)))
      (is (= :ret (closed-over-in-finally)))
      (is (= :run @a))))
  (testing "no clearing when loop not in return context"
    (let [x (atom 5)
          bad (fn []
                (loop [] (Environment/GetEnvironmentVariables))                                  ;;; (System/getProperties)
                (swap! x dec)
                (when (pos? @x)
                  (recur)))]
      (is (nil? (bad))))))

(deftest CLJ-1586-lazyseq-literals-preserve-metadata
  (should-not-reflect (eval (list '.Substring (with-meta (concat '(identity) '("foo")) {:tag 'String}) 0))))         ;;; .substring

(deftest CLJ-1456-compiler-error-on-incorrect-number-of-parameters-to-throw
  #_(is (thrown? RuntimeException (eval '(defn foo [] (throw)))))                                                    ;;; not an error for us.  no arg signifies Rethrow
  (is (thrown? Exception (eval '(defn foo [] (throw RuntimeException any-symbol)))))                                 ;;; RuntimeException
  (is (thrown? Exception (eval '(defn foo [] (throw (RuntimeException.) any-symbol)))))                              ;;; RuntimeException
  (is (var? (eval '(defn foo [] (throw (ArgumentException.)))))))                                                    ;;; IllegalArgumentException

(deftest clj-1809
  (is (eval `(fn [y#]
               (try
                 (finally
                   (let [z# y#])))))))

;; See CLJ-1846
(deftest incorrect-primitive-type-hint-throws
  ;; invalid primitive type hint
  (is (thrown-with-msg? Compiler+CompilerException #"Cannot coerce System.Int64 to System.Int32"                     ;;; Compiler$CompilerException  "Cannot coerce long to int
        (load-string "(defn returns-long ^long [] 1) (Math/Sign ^int (returns-long))")))                             ;;; Integer/bitCount
  ;; correct casting instead
  (is (= 1 (load-string "(defn returns-long ^long [] 1) (Math/Sign (int (returns-long)))"))))                        ;;; Integer/bitCount

;; See CLJ-1825
(def zf (fn rf [x] (lazy-seq (cons x (rf x)))))
(deftest test-anon-recursive-fn
  (is (= [0 0] (take 2 ((fn rf [x] (lazy-seq (cons x (rf x)))) 0))))
  (is (= [0 0] (take 2 (zf 0)))))

;; See CLJ-1845
(deftest direct-linking-for-load
  (let [called? (atom nil)
        logger (fn [& args]
                 (reset! called? true)
                 nil)]
    (with-redefs [load logger]
      ;; doesn't actually load clojure.repl, but should
      ;; eventually call `load` and reset called?.
      (require 'clojure.repl :reload))
    (is @called?)))

;;;(deftest clj-1714                        -- not relevant
;;;  (testing "CLJ-1714 Classes shouldn't have their static initialisers called simply by type hinting or importing"
;;;    ;; ClassWithFailingStaticInitialiser will throw if its static initialiser is called
;;;    (is (eval '(fn [^compilation.ClassWithFailingStaticInitialiser c])))
;;;    (is (eval '(import (compilation ClassWithFailingStaticInitialiser))))))