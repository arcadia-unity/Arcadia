(ns arcadia.react
  (:use arcadia.core)
  (:require [arcadia.hydrate :as hydrate]
            [clojure.data :as data]
            [clojure.set :as s]))

(def go-map
  "Map of user keys to unity GameObject instances"
  (atom {}))

(defn realize! 
  "Like hydrate, buts takes a map and updates go-map"
  ([h]
   (map (partial apply realize!) h))
  ([k spec]
   (let [go (hydrate/hydrate spec)]
     (swap! go-map assoc k go))))

(defn keyset [m]
  "The set of all keys in map m"
  (-> m keys set))

(defn patch [old-scene new-scene]
  "Generate a patch to transform old-scene into new-scene. Returns a map with the keys:
  :added   - map of new values, suitable for hydrate
  :changed - map of old values, suitable for populate!
  :remove  - set of keys to remove"
  (let [[old-keys new-keys same-keys] (map keyset (data/diff old-scene new-scene))
        added-keys (s/difference new-keys same-keys)
        removed-keys (s/difference old-keys same-keys)
        changed-keys (s/difference new-keys added-keys)]
    {:added (select-keys new-scene added-keys)
     :changed (select-keys new-scene changed-keys)
     :removed removed-keys}))

(defn apply! [patch]
  "Apply a patch to the scene. Hydrates everything in :added, populates everything
  in :changed, destroys everything in :removed"
  (let [{:keys [added changed removed]} patch
        object-map @go-map
        changed-objs (map #(vector (-> % first object-map)
                                   (-> % last))
                          changed)
        removed-objs (map object-map removed)]
    (dorun (map (partial apply realize!) added))
    (dorun (map (partial apply hydrate/populate!) changed-objs))
    (dorun (map destroy removed-objs))))

;; --

(:sprite-renderer (hydrate/dehydrate (Selection/activeObject)))

(def start
  {:cube {:transform [{:local-position (Vector3. 0 0 0)}]
          :sprite-renderer [{}]}})

(defn new-state [m]
  (-> m
      (update-in [:cube :transform 0 :local-rotation]
                 #(Quaternion/RotateTowards %
                                            (Quaternion/Euler 90 0 0)
                                            1))
      (update-in [:cube :transform 0 :local-position]
                 #(Vector3/op_Addition % Vector3/forward))))

(declare update)

(defcomponent Reactor [state]
  (Start [this]
         (set! (.state this) start)
         (realize! (.state this)))
  (Update [this]
          (update this)))

(defn update [^Reactor this]
  (let [oldstate (.state this)
        newstate (new-state oldstate)]
    (apply! (patch oldstate newstate))))