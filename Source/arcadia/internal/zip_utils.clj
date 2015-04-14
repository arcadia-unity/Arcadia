(ns arcadia.internal.zip-utils
  (:require [clojure.zip :as zip]))

;; zip it right up
(defn zip-up-to-right [loc]
  (if-let [r (zip/right loc)]
    r
    (when-let [u (zip/up loc)]
      (recur u))))

;; test this! digs into zipper implementation
(defn zip-constructor [loc]
  (let [m (meta loc)
        branch?-fn (:zip/branch? m)
        children-fn (:zip/children m)
        make-node-fn (:zip/make-node m)]
    (fn [x]
      (zip/zipper
        branch?-fn
        children-fn
        make-node-fn
        x))))

;; zip-constructor is the tricky one, will require digging into zipper implementation guts
;; if you REALLY want to stay within the api I guess you could zip up
;; to the top and then replace with the node from below, thereby
;; inheriting the same zipper functions. Lot of gratuitous loc construction though.
(defn zip-detach [loc]
  ((zip-constructor loc) (zip/node loc)))

;; not sure this works for all possible zippers, assumes certain zipper/target-tree
;; stability I guess. *depth-first*, preorder traversal.
(defn zip-prewalk [loc f]
  (loop [loc2 (zip-detach loc)]
    (let [loc3 (zip/edit loc2 f)]
      (if-let [loc4 (or (zip/down loc3) (zip-up-to-right loc3))]
        (recur loc4)
        (zip/replace loc (zip/root loc3))))))
