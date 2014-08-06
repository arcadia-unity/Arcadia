(ns test-behaviour
  (:import [UnityEngine Debug]))

(defprotocol IStart
  (Start [this]))

(defprotocol IUpdate
  (Update [this]))

(other-deftype SomeBehaviour [^:unsynchronized-mutable ^System.String myName ^:unsynchronized-mutable ^float speed]
  IStart
  (Start [this]
    (Debug/Log (str "My name is " myName)))

  IUpdate
  (Update [this]
    (Debug/Log (str "My name is " myName " and I move at the speed of " speed))))

(other-deftype SlamHut)