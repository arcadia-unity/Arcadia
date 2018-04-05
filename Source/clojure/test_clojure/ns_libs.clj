;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Frantisek Sodomka, Stuart Halloway

(ns clojure.test-clojure.ns-libs
  (:use clojure.test))

; http://clojure.org/namespaces

; in-ns ns create-ns
; alias import intern refer
; all-ns find-ns
; ns-name ns-aliases ns-imports ns-interns ns-map ns-publics ns-refers
; resolve ns-resolve namespace
; ns-unalias ns-unmap remove-ns


; http://clojure.org/libs

; require use
; loaded-libs

(deftest test-alias
  (is (thrown-with-msg? Exception #"No namespace: epicfail found" (alias 'bogus 'epicfail))))
  
(deftest test-require
         (is (thrown? Exception (require :foo)))
         (is (thrown? Exception (require))))

(deftest test-use
         (is (thrown? Exception (use :foo)))
         (is (thrown? Exception (use))))

(deftest reimporting-deftypes
  (let [inst1 (binding [*ns* *ns*]
                (eval '(do (ns exporter)
                           (defrecord ReimportMe [a])
                           (ns importer)
                           (import exporter.ReimportMe)
                           (ReimportMe. 1))))
        inst2 (binding [*ns* *ns*]
                (eval '(do (ns exporter)
                           (defrecord ReimportMe [a b])
                           (ns importer)
                           (import exporter.ReimportMe)
                           (ReimportMe. 1 2))))]
    (testing "you can reimport a changed class and see the changes"
      (is (= [:a] (keys inst1)))
      (is (= [:a :b] (keys inst2))))
	;fragile tests, please fix
    #_(testing "you cannot import same local name from a different namespace"
      (is (thrown? InvalidOperationException                                                     ;;; clojure.lang.Compiler+CompilerException
                  #"ReimportMe already refers to: exporter.ReimportMe in namespace: importer"    ;;; extra word class removed
                  (binding [*ns* *ns*]
                    (eval '(do (ns exporter-2)
                               (defrecord ReimportMe [a b])
                               (ns importer)
                               (import exporter-2.ReimportMe)
                               (ReimportMe. 1 2)))))))))

(deftest naming-types
  (testing "you cannot use a name already referred from another namespace"
    (is (thrown-with-msg? InvalidOperationException                               ;;; IllegalStateException
                          #"String already refers to: System.String"              ;;; class java.lang.String
                          (definterface String)))
    (is (thrown-with-msg? InvalidOperationException                               ;;; IllegalStateException
                          #"Buffer already refers to: System.Buffer"              ;;;  StringBuffer class java.lang.StringBuffer
                          (deftype Buffer [])))                                   ;;; StringBuffer  -- StringBuffer not imported
    (is (thrown-with-msg? InvalidOperationException                               ;;; IllegalStateException
                          #"Int32 already refers to: System.Int32"                ;;; Integer  class java.lang.Integer
                          (defrecord Int32 [])))))                                ;;; Integer

(deftest resolution
  (let [s (gensym)]
    (are [result expr] (= result expr)
         #'clojure.core/first (ns-resolve 'clojure.core 'first)
         nil (ns-resolve 'clojure.core s)
         nil (ns-resolve 'clojure.core {'first :local-first} 'first)
         nil (ns-resolve 'clojure.core {'first :local-first} s))))
  
(deftest refer-error-messages
  (let [temp-ns (gensym)]
    (binding [*ns* *ns*]
      (in-ns temp-ns)
      (eval '(def ^{:private true} hidden-var)))
    (testing "referring to something that does not exist"
      (is (thrown-with-msg? InvalidOperationException #"nonexistent-var does not exist"      ;;; IllegalAccessError
            (refer temp-ns :only '(nonexistent-var)))))
    (testing "referring to something non-public"
      (is (thrown-with-msg? InvalidOperationException #"hidden-var is not public"            ;;; IllegalAccessError
            (refer temp-ns :only '(hidden-var)))))))   

(deftest test-defrecord-deftype-err-msg
  (is (thrown-with-msg? clojure.lang.Compiler+CompilerException                                                                ;;; Compiler$CompilerException
                        #"defrecord and deftype fields must be symbols, [\w|\p{P}]*\.MyRecord had: :shutdown-fn, compiling:"   ;;;  user\.MyRecord
                        (eval '(defrecord MyRecord [:shutdown-fn]))))
  (is (thrown-with-msg? clojure.lang.Compiler+CompilerException                                                               ;;; Compiler$CompilerException
                        #"defrecord and deftype fields must be symbols, [\w|\p{P}]*\.MyType had: :key1, compiling:"           ;;;  user\.MyRecord
                        (eval '(deftype MyType [:key1])))))              