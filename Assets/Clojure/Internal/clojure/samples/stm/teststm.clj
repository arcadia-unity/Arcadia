;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.
;
;	Author: Shawn Hoover

; Simple test of the STM.
;  (f1)


(ns clojure.teststm)


(defn sleep [time-ms] (System.Threading.Thread/Sleep time-ms))

(def a (ref #{}))
(def b (ref #{}))

(def cycle-continue true)

(defn make-future [id]
  (future
   (try
     (loop [n 0]
       ;; join the party
       (dosync
        (alter a conj id)
        (alter b conj id))

       ;; validate the refs
       (dosync
        (let [current-a @a
              current-b @b]
          (if (not (= current-a current-b))
            (throw (Exception. (str (format "\n%s\n%s" current-a current-b)))))))

       ;; leave
       (dosync
        (alter a disj id)
        (alter b disj id))

       (if cycle-continue
         (recur (inc n))))
     (catch Exception ex
       (def cycle-continue false)
       (sleep 100)
       (println ex)))))

; (f1 3 30)
; should see 30 dots, then done, unless an error occurs.  Then you will see an error message printed.

(defn f1 [nagts dur]
  (future
    (do
      (def a (ref #{}))
      (def b (ref #{}))
      (def cycle-continue true)
      (let [n-agents nagts
            duration dur
            futures (doall (map make-future (range n-agents)))]
        (loop [i 0]
          (sleep 1000)
          (print ".") (flush)
          (if (and (<= i duration) cycle-continue)
            (recur (inc i)))))
      (println "done")
      (def cycle-continue false))))
