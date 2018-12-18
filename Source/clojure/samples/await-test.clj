; Simple test for await
; Eventually should put in test framework

(def counter (ref 0))

(defn work [state]
  (System.Threading.Thread/Sleep 1000)
  (dosync (commute counter inc))
  true)
  
(def agents (for [x (range 10)] (agent nil)))

(defn doit []
  (doall (map #(send % work) agents))
  (apply await agents)
  [@counter (doall (map deref agents))])
