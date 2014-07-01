(ns test-clj-clr.encoding
  (:require clojure.string)
  (:import [System Convert]))

(defn qwik-encode [s]
  (str s \u0004))

(defn qwik-decode [s]
  (if (not= (.indexOf s "\u0004") -1)
    (first (clojure.string/split s #"\u0004"))
    (throw (str "EOT not found in string " s))))