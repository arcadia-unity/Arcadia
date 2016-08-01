(ns arcadia.internal.leiningen
  (:require [arcadia.internal.filewatcher :as fw]
            [arcadia.internal.asset-watcher :as aw]
            [arcadia.internal.state :as state]
            [clojure.spec :as s]
            [arcadia.internal.spec :as as]))

;; ============================================================
;; implementation

;; ------------------------------------------------------------
;; state

(defn- state []
  (@state/state ::leiningen))

(def ^:private update-state
  (state/updater ::leiningen))

;; ------------------------------------------------------------
;; state grammar, since we love that now

(s/def ::dependency (as/collude [] string?))

(s/def ::dependencies (as/collude #{} ::dependency))

(s/def ::defproject (s/keys :req-un [::dependencies]))

(s/def ::path string?)

(s/def ::project (s/keys :req-un [::path ::defproject]))

(s/def ::projects (as/collude #{} ::project))

(s/def ::leiningen (s/keys :req-un [::projects]))

;; ------------------------------------------------------------

(defn- compute-raw-dependencies [data]
  (into [] (comp (map :defproject) (map :dependencies) cat) data))

;; ============================================================
;; public facing

(declare gather-data compute-projects compute-raw-dependencies update-state)

(defn refresh!
  "Hits disk to update Arcadia state with Leiningen-relevent information, keyed to ::leiningen. Returns total Arcadia state immediately after update completes."
  []
  (let [data (gather-data)
        projects (compute-projects data)
        dependencies (compute-raw-dependencies data)]
    (update-state
      (fn [x]
        (assoc x :projects projects)))))

(defn projects
  "Returns vector of information for directories under Assets that Arcadia currently considers Leiningen projects. Does not hit disk.

Elements of the returned vector have the following structure:

{:path  <string representation of absolute path to directory of leiningen project>
 :loadpath <vector of strings representing absolute path(s) to leiningen project root(s)>
 :name <string; name of the leiningen project>}
"
  [])

(defn dependencies
  "Return vector of what Arcadia currently considers to be dependency coordinates specified in Leiningen project files. Does not hit disk."
  [])


;; ============================================================
;; hook up listeners. should be idempotent.


;; ((::fw/add-listener (aw/asset-watcher))
;;  ::fw/create-modify-delete-file
;;  ::config-reload
;;  (fn [{:keys [::fw/time ::fw/path]}]
;;    ;; stuff happens here
;;    ))
