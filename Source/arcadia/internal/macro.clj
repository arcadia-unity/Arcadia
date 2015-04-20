(ns arcadia.internal.macro)

(def alphabet
  (mapv str
    (sort "qwertyuiopasdfghjklzxcvbnm")))

;; ungry-bettos!

(defn classy-arg-strs
  ([]
   (for [n (cons nil (drop 2 (range)))
         c alphabet]
     (str c n)))
  ([n]
   (take n (classy-arg-strs))))

(defn classy-args
  ([] (map symbol (classy-arg-strs)))
  ([n] (map symbol (classy-arg-strs n))))
