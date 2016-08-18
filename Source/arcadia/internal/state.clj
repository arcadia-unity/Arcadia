(ns arcadia.internal.state
  (:require [clojure.spec :as s]
            [arcadia.internal.map-utils :as mu]))

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
  (swap! state assoc-in [listener-group-key listener-key] listener))

(defn remove-listener
  ([listener-group-key]
   (swap! state dissoc listener-group-key))
  ([listener-group-key listener-key]
   (swap! state mu/dissoc-in [listener-group-key listener-key])))

(defn run-listeners
  ([listener-group-key]
   (reduce (fn [_ listener]
             (listener)
             nil)
     nil
     (mu/valsr (get @state listener-group-key))))
  ([listener-group-key data]
   (reduce (fn [_ listener]
             (listener data)
             nil)
     nil
     (mu/valsr (get @state listener-group-key))))
  ([listener-group-key data & args]
   (let [args2 (cons data args)]
     (reduce (fn [_ listener]
               (apply listener args2)
               nil)
       nil
       (mu/valsr (get @state listener-group-key))))))

(defn listeners [listener-group-key]
  (get @state listener-group-key))
