(ns awesome-namespace)

(def foo :foo!)

(def fum :fum!)

(def fi :fi!)

(def flun :gruntles)

(defn a-function [& args]
  (zipmap args (reverse args)))

(defn a-nother-function [& args]
  (a-function (reverse args))) 

(def start-counter (atom 0))

(defprotocol IStart
  (Start [this]))

(defscript ScriptTest1 []
  IStart
  (Start [this]
    (reset! start-counter
      (inc @start-counter))))
