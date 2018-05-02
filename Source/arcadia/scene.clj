(ns arcadia.scene
  (:refer-clojure :exclude [keys])
  (:require [arcadia.internal.map-utils :as mu]
            clojure.set
            [arcadia.core :as ac]
            [clojure.test :as t]
            [arcadia.internal.test :as at]))

;; ==================================================
;; label

(declare label-role label-roles make-label-roles)

(defonce ^:private label-registry
  (atom {::label->objects {}
         ::object->labels {}}))

(def ^:private sconj (fnil conj #{}))

(defn- reg [m k v]
  (update m k sconj v))

(defn- reg* [m k vs]
  (update m k #(reduce sconj % vs)))

(defn register* [obj labels]
  (if-let [st (ac/state obj ::labels)]
    (ac/state+ obj ::labels
      (update st ::labels into labels))
    (ac/roles+ obj (make-label-roles labels)))
  (swap! label-registry
    (fn [lr]
      (reduce
        (fn [lr label]
          (-> lr
              (update ::label->objects reg label obj)
              (update ::object->labels reg obj label)))
        lr
        labels)))
  obj)

(defn register
  "Registers GameObject `obj` with one or more `label`s. Returns `obj`."
  ([obj label]
   (if-let [st (ac/state obj ::labels)]
     (ac/state+ obj ::labels
       (update st ::labels conj label))
     (ac/roles+ obj (make-label-roles #{label})))
   (swap! label-registry
     (fn [hr]
       (-> hr
           (update ::label->objects reg label obj)
           (update ::object->labels reg obj label))))
   obj)
  ([obj label & labels]
   (register* obj (cons label labels))))

(defn- unreg [m k v]
  (if-let [xs (get m k)]
    (let [xs' (disj xs v)]
      (if (empty? xs')
        (dissoc m k)
        (assoc m k xs')))
    m))

(defn unregister* [obj labels]
  (when-let [st (ac/state obj ::labels)]
    (ac/state+ obj ::labels
      (update st ::labels #(reduce disj % labels))))
  (swap! label-registry
    (fn [lr]
      (reduce
        (fn [lr label]
          (-> lr
              (update ::label->objects unreg label obj)
              (update ::object->labels unreg obj label)))
        lr
        labels)))
  obj)

(defn unregister
  ([obj label]
   (unregister* obj [label]))
  ([obj label & labels]
   (unregister* obj (cons label labels))))

(defn- unregister-at [hr index-k reverse-k k]
  (let [index (get hr index-k)
        reverse (get hr reverse-k)] ; the reverse index
    (if-let [xs (get index k)]
      (let [reverse' (reduce #(unreg %1 %2 k) reverse xs)]
        (-> hr
            (update index-k dissoc k)
            (assoc reverse-k reverse')))
      hr)))

(declare objects labels)

(defn unregister-label [label]
  (doseq [o (objects label)]
    (unregister o label)))

(defn unregister-object [obj]
  (unregister* obj (labels obj))
  nil)

(defn objects
  ([] #{})
  ([label]
   (or (-> @label-registry (get ::label->objects) (get label))
       #{}))
  ([label & labels]
   (let [lo (get @label-registry ::label->objects)]
     (or (->> (map lo (cons label labels))
              (apply clojure.set/intersection))
         #{}))))

(defn objects-or
  ([] #{})
  ([label]
   (objects label))
  ([label & labels]
   (let [lo (get @label-registry ::label->objects)]
     (or (->> (map lo (cons label labels))
              (apply clojure.set/union))
         #{}))))

(defn labels
  ([] #{})
  ([obj]
   (or (->  @label-registry (get ::object->labels) (get obj))
       #{}))
  ([obj & objs]
   (let [ol (get @label-registry ::object->labels)]
     (or (->> (map ol (cons obj objs))
              (apply clojure.set/intersection))
         #{}))))

(defn labels-or
  ([] #{})
  ([object]
   (labels object))
  ([object & objects]
   (let [ol (get @label-registry ::object->labels)]
     (or (->> (map ol (cons object objects))
              (apply clojure.set/union))
         #{}))))

(defn all-labels []
  (-> @label-registry (get ::label->objects) clojure.core/keys set))

(defn all-objects []
  (-> @label-registry (get ::object->labels) clojure.core/keys set))

(defn registered?
  ([obj label]
   (-> @label-registry (get ::label->objects) (get label) (contains? obj))))

(declare retire)

(defn retire* [labels]
  (reduce #(retire %2) nil labels)
  nil)

(defn retire
  ([label]
   (let [objs (objects label)]
     (doseq [obj objs]
       (ac/retire obj))
     (unregister-label label)
     nil))
  ([label & labels]
   (retire* (cons label labels))))

(ac/defrole label-role
  :state {::labels nil}
  (awake [obj k]
    (let [{:keys [labels]} (ac/state obj k)]
      (doseq [label labels]
        (register obj label))))
  (on-destroy [obj k]
    (let [{:keys [labels]} (ac/state obj k)]
      (doseq [label labels]
        (unregister label obj)))))

(def label-roles
  {::labels label-role})

(defn make-label-roles [labels]
  (assoc-in label-roles [::labels :state ::labels]
    (mu/ensure-set labels)))

;; ============================================================
;; tests

(defn registration-fixture [f]
  (let [lr @label-registry]
    (reset! label-registry {::label->objects {}
                            ::object->labels {}})
    (try
      (f)
      (finally
        (reset! label-registry lr)))))

(t/use-fixtures :each #'registration-fixture)

(t/deftest registration
  (let [barnaby (UnityEngine.GameObject. "barnaby")
        barnaby-2 (UnityEngine.GameObject. "barnaby-2")
        lbls [:label-a :label-b]
        lbls-2 [:label-a :label-c]]
    (try
      (register* barnaby lbls)
      (register* barnaby-2 lbls-2)
      (t/is (every? #(registered? barnaby %) lbls))
      (t/is (= (remove #(registered? barnaby %) (concat lbls lbls-2)) [:label-c]))
      (t/is (= (labels barnaby) #{:label-a :label-b}))
      (t/is (= (apply objects lbls) #{barnaby}))
      (t/is (= (objects :label-a) #{barnaby barnaby-2}))
      (t/is (= (objects :label-a :flaadfa) #{}))
      (t/is (= (objects) #{}))
      (t/is (= (objects :flaadfa) #{}))
      (t/is (= (objects :label-c) #{barnaby-2}))
      (t/is (= (apply objects lbls-2) #{barnaby-2}))
      (t/is (= (apply objects (concat lbls lbls-2)) #{}))
      (t/is (= (labels-or barnaby barnaby-2) (set (concat lbls lbls-2))))
      (t/is (= (apply objects-or (concat lbls lbls-2)) #{barnaby barnaby-2}))
      ;; ------------------------------------------------------------
      ;; basics
      (t/is (= (all-labels) (set (concat lbls lbls-2))))
      (t/is (= (all-objects) #{barnaby barnaby-2}))
      ;; ------------------------------------------------------------
      ;; unregister
      (unregister-object barnaby)
      (t/is (= (labels barnaby) #{}))
      (t/is (= (all-labels) (set lbls-2)))
      (t/is (= (all-objects) #{barnaby-2}))
      (unregister-object barnaby-2)
      (t/is (= (all-labels) #{}))
      (t/is (= (all-objects) #{}))
      (finally
        (ac/retire barnaby)
        (ac/retire barnaby-2)))))
