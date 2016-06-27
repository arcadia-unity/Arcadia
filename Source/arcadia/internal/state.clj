(ns arcadia.internal.state)

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
