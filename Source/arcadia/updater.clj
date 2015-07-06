(ns arcadia.updater
  (:use arcadia.core))

(def disabled
  (atom #{}))

(def updater-fns
  (atom {}))

(defn put! [k updr]
  (swap! updater-fns assoc k updr))

(defn disable! [k]
  (swap! disabled conj k))

(defn enable! [k]
  (swap! disabled disj k))

(defn toggle! [k]
  (swap! disabled
    #((if (% k) disj conj) % k)))

(defn run-updaters! []
  (let [ds @disabled]
   (doseq [[k f] @updater-fns
           :when (not (ds k))]
     (f))))

(defcomponent Updater []
  (Update [this]
    (run-updaters!)))
