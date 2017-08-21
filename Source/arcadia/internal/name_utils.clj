(ns arcadia.internal.name-utils
  (:require [clojure.string :as string]))

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn title-case [s]
  (-> s
      name
      (string/replace #"[-_]" " ")
      (string/replace #"([a-z])([A-Z])" "$1 $2")
      (string/replace #" [a-z]" string/upper-case)
      (string/replace #"^[a-z]" string/upper-case)))