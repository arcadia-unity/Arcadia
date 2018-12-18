(ns arcadia.internal.state
  (:require [clojure.spec.alpha :as s]
            [arcadia.internal.map-utils :as mu])
  (:import [UnityEngine Debug]))

;; ============================================================
;; logging

(defonce log-state
  (atom {:log []
         :logging false}))

(defn reset-log []
  (swap! log-state assoc :log []))

(defn logging-on []
  (swap! log-state assoc :logging true))

(defn logging-off []
  (swap! log-state assoc :logging false))

(defn- logger [_ _ old new]
  (when (:logging @log-state)
    (swap! log-state update :log conj
      {:time System.DateTime/Now
       :old old
       :new new})))

;; ============================================================
;; state

;; Site of all application state in Arcadia
(defonce state
  (let [state (atom {})]
    (add-watch state ::logger #'logger)
    state))

;; ============================================================
;; convenience

(defn updater [kw]
  (fn [f & args]
    (apply swap! state update kw f args)))

;; ==================================================
;; listeners

(s/fdef add-listener
  :args (s/cat
          :listener-group-key qualified-keyword?
          :listener-key qualified-keyword?
          :listener ifn?))

(defn add-listener [listener-group-key listener-key listener]
  (swap! state assoc-in [::listeners listener-group-key listener-key] listener))

(defn remove-listener
  ([listener-group-key]
   (swap! state mu/dissoc-in [::listeners listener-group-key]))
  ([listener-group-key listener-key]
   (swap! state mu/dissoc-in [::listeners listener-group-key listener-key])))

(defn run-listeners-body [listener-group-key f]
  (reduce-kv (fn [_ key listener]
               (try
                 (f listener)
                 (catch Exception e
                   (Debug/Log
                     (str "Exception encountered for " key
                          " in listener group " listener-group-key ":"))
                   (Debug/Log e)
                   (Debug/Log
                     (str "Removing listener " key
                          " in listener group " listener-group-key))
                   (remove-listener listener-group-key key)
                   nil)))
    nil
    (get-in @state [::listeners listener-group-key])))

(defn run-listeners
  ([listener-group-key]   
   (run-listeners-body listener-group-key #(%)))
  ([listener-group-key data]
   (run-listeners-body listener-group-key #(% data)))
  ([listener-group-key data & args]
   (run-listeners-body listener-group-key #(apply % data args))))

(defn listeners
  ([]
   (get @state ::listeners))
  ([listener-group-key]
   (get-in @state [::listeners listener-group-key])))
