(ns arcadia.arcadia-tests
  (:use clojure.test)
  (:require [arcadia.core :as ac]
            [arcadia.internal.test :as at]
            [arcadia.internal.editor-callbacks :as ec]
            [arcadia.internal.player-callbacks :as pc]
            [clojure.spec.alpha :as s]
            [arcadia.debug :refer [break]])
  (:import [UnityEngine
            GameObject Component Vector2 Vector3]
           [Arcadia UnityStatusHelper]))

;; ============================================================
;; utils

(def ^:private retirement-key-counter (atom 0))

(defn schedule-retirement [timing-type timeout & xs]
  (let [k (swap! retirement-key-counter inc)
        f (fn []
            (doseq [x xs]
              (try
                (ac/retire x)
                (catch Exception e
                  (ac/log "Exception encountered in retirement phase of schedule-retirement:")
                  (UnityEngine.Debug/Log e)))))]
    (case timing-type
      :ms
      (ec/add-timeout k f timeout)

      :frames
      (pc/add-update-frame-timeout k f timeout)
      
      :eframes
      (ec/add-editor-frame-timeout k f timeout))))

(s/def ::eframes any?)

(s/def ::frames any?)

(s/def ::ms any?)

(s/def ::lit (s/every symbol? :kind vector))

(s/def ::with-temp-objects-args
  (s/cat
    :opts (s/?
            (s/keys* :opt-un [::eframes ::ms ::lit ::frames]))
    :bindings (s/?
                (s/and
                  vector?
                  #(even? (count %))))
    :body (s/* any?)))

(defmacro ^{:arglists ([kv-opts* bindings? body*])} with-temp-objects 
  "Sets up temporary objects and schedules their retirement. Intended for testing.

  Syntax: (with-temp-objects kv-opts* bindings? body*)

  `kv-opts*` are optional key-value pairs. They can be the following:

  :ms      - evaluated value is milliseconds before objects are retired
  :frames  - evaluated value is player frames before objects are retired
  :eframes - evaluated value is editor frames before objects are retired
  :lit     - value should be a vector of symbols which will be bound to
             new GameObjects (see below)

  `bindings` is an optional vector binding form, and has the same
  syntax as `clojure.core/let`.  Every value bound to a symbol in it
  should be a GameObject instance, since `arcadia.core/retire` will
  get called on all of them.

  If the :lit key is provided, the symbols in its vector value will be
  bound to new GameObject instances named after their corresponding symbol.

  When GameObjects bound in `bindings` or through the `:lit` vector
  get retired depends on the supplied options `:ms`, `:frames`, or `:eframes`. If
  both are provided an error is thrown. If neither is provided the
  GameObjects are retired as soon as `body` is evaluated."
  [& args]  
  (let [parse (s/conform ::with-temp-objects-args args)]
    (when (= ::s/invalid parse)
      (throw (Exception. (str "invalid arguments. spec explanation:\n"
                              (with-out-str (s/explain ::with-temp-objects-args args))))))
    (let [{:keys [opts bindings body]} parse
          [timing-type timeout] (let [e (select-keys opts [:eframes :frames :ms])]
                                  (when (< 1 (count e))
                                    (throw (Exception. "Please supply at most one of :eframes, :frames, or :ms")))
                                  (or (first e) [:now]))
          lit-bindings (->> (get opts :lit)
                            (mapcat (fn [sym]
                                      [sym `(UnityEngine.GameObject. ~(str sym))])))
          bindings (vec (concat lit-bindings bindings))]
      (if (= timing-type :now)
        (let [teardown (for [x (take-nth 2 bindings)]
                         `(ac/retire ~x))]
          `(let ~bindings
             (try
               ~@body
               (finally
                 ~@teardown))))
        `(let ~bindings
           (schedule-retirement ~timing-type ~timeout ~@(take-nth 2 bindings))
           ~@body)))))

;; --------------------------------------------------
;; async testing thing
;; consider moving the following to arcadia.internal.tests



;; ============================================================
;;

(def unique-key-counter (atom 0))

(defn unique-key [] (swap! unique-key-counter inc))

(def state-1
  {:v2 (UnityEngine.Vector2. 1 2)
   :v3 (UnityEngine.Vector3. 1 2 3)
   :vec [:a :b :c :d]
   :set #{:a :b :c :d}})

(def state-2
  {:a :A
   :b :B})

;; move to internal test framework

;; as-sub. bit aggressive but here we are. Usually I wouldn't
;; go for this kind of trivial sugar but it's important that
;; tests are very easy to write.
(defmacro as-sub [t-or-t-and-label & body]
  (cond
    (vector? t-or-t-and-label)
    (do (assert (= 2 (count t-or-t-and-label)))
        (let [[t label] t-or-t-and-label]
          (assert (symbol? t))
          (assert (string? label))
          `(let [~t (at/sub ~t ~label)]
             ~@body)))

    (symbol? t-or-t-and-label)
    (let [t t-or-t-and-label]
      `(let [~t (at/sub ~t)]
         ~@body))

    :else (throw
            (InvalidOperationException.
              (str "`t-or-t-and-label` must be either symbol or vector, instead got "
                   (class t-or-t-and-label))))))

;; consider breaking this up
(at/deftest hook-state-system t
  (as-sub [t "basic state tests"]
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (ac/state+ obj :test-2 state-2)
      (t (is (= (ac/state obj)
                {:test state-1
                 :test-2 state-2})
           "no-key state recovers all state entries")))
    (with-temp-objects :lit [obj]
      (ac/state+ obj :test state-1)
      (t (is (= (ac/state obj :test) state-1)
           "basic state roundtrip")))
    (t :close))
  ;; (as-sub [t "state-"]
  ;;   (with-temp-objects :lit [obj]
  ;;     (ac/state+ obj :test state-1)
  ;;     (ac/state- obj :test)
  ;;     (t (is (= (ac/state obj) nil))))
  ;;   (with-temp-objects :lit [obj]
  ;;     (ac/state+ obj :test state-1)
  ;;     (ac/state+ obj :test-2 state-2)
  ;;     (ac/state- obj :test)
  ;;     (t (is (= (ac/state obj) {:test-2 state-2})
  ;;          "partial state retrieval after state-")
  ;;       :close)))
  ;; (as-sub [t "state serialization"]
  ;;   (with-temp-objects :lit [obj]
  ;;     (ac/state+ obj :test state-1)
  ;;     (ac/state+ obj :test-2 state-2)
  ;;     (with-temp-objects [obj-2 (ac/instantiate obj)]
  ;;       (t (is (= (ac/state obj)
  ;;                 (ac/state obj-2))
  ;;            "round-trip serialization")
  ;;         :close))))
  ;; ;; requires play mode
  ;; ;; not throwing if not in play mode, though, because
  ;; ;; we need to test both edit and play mode.
  ;; (when (Arcadia.UnityStatusHelper/IsInPlayMode)
  ;;   (as-sub [t "hook+"]
  ;;     (with-temp-objects
  ;;       :frames 10
  ;;       :lit [obj]
  ;;       (let [state (ac/state+ obj :test {:v2 (UnityEngine.Vector2. 0 1)
  ;;                                         :v3 (UnityEngine.Vector3. 0 1 2)})]        
  ;;         (ac/hook+ obj :update :test
  ;;           (fn [obj' k]
  ;;             (t
  ;;               (is (= obj obj') "correct obj")
  ;;               (is (= k :update) "correct key")
  ;;               (is (= state (ac/state obj')) "correct state")
  ;;               :close)))))))
  )
