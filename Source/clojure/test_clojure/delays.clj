;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test-clojure.delays
  (:use clojure.test)
  );;;(:import [System.Threading Barrier Thread ThreadStart])                      ;;; [java.util.concurrent CyclicBarrier]



;; DM: Added
;; Copied from reducers.clj, modified compile-if to compile-when
(defmacro ^:private compile-when
  [exp & body]
  (when (try (eval exp)
           (catch Exception _ false))                      ;;; Throwable
    `(do ~@body)))

(deftest calls-once
  (let [a (atom 0)
        d (delay (swap! a inc))]
    (is (= 0 @a))
    (is (= 1 @d))
    (is (= 1 @d))
    (is (= 1 @a))))

(compile-when
 (Type/GetType "System.Threading.Barrier")

 ;; DM: Need to conditionally import these names
(import '[System.Threading Barrier Thread ThreadStart])
(deftest calls-once-in-parallel

  (let [a (atom 0)
        d (delay (swap! a inc))
        threads 100
         ^Barrier barrier (Barrier. (+ threads 1))]    ;;; ^CyclicBarrier   CyclicBarrier. 
    (is (= 0 @a))
    (dotimes [_ threads]
        (->
            (Thread.
                (gen-delegate ThreadStart []                                                                 ;;; fn
                    (.SignalAndWait barrier)                           ;;;  .await
                    (dotimes [_ 10000]
                        (is (= 1 @d)))
                    (.SignalAndWait barrier)))                         ;;;  .await
        (.Start)))                                  ;;; .start
    (.SignalAndWait barrier)                        ;;;  .await
    (.SignalAndWait barrier)                        ;;;  .await
    (is (= 1 @d))
    (is (= 1 @d))
    (is (= 1 @a))))
)

(deftest saves-exceptions
  (let [f #(do (throw (Exception. "broken"))
               1)
        d (delay (f))
        try-call #(try
                    @d
                    (catch Exception e e))
        first-result (try-call)]
    (is (instance? Exception first-result))
    (is (identical? first-result (try-call)))))

#_(deftest saves-exceptions-in-parallel                                       ;;; seems to take forewever
  (let [f #(do (throw (Exception. "broken"))
               1)
        d (delay (f))
        try-call #(try
                    @d
                    (catch Exception e e))
        threads 100
         ^Barrier barrier (Barrier. (+ threads 1))]                           ;;; ^CyclicBarrier      CyclicBarrier. 
    (dotimes [_ threads]
        (->
            (Thread.
                (gen-delegate ThreadStart []                                  ;;; fn
                    (.SignalAndWait barrier)                                  ;;; .await
                    (let [first-result (try-call)]
                        (dotimes [_ 10000]
                            (is (instance? Exception (try-call)))
                            (is (identical? first-result (try-call)))))
                    (.SignalAndWait barrier)))                                ;;; .await
            (.Start)))                                                        ;;; .start
    (.SignalAndWait barrier)                                                  ;;; .await
    (.SignalAndWait barrier)                                                  ;;; .await
    (is (instance? Exception (try-call)))
    (is (identical? (try-call) (try-call)))))