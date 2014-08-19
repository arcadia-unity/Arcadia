(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su]
            [unity.reflect-utils :as ru]
            [unity.hydration-forms :as hf]
            [clojure.string :as string])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

;; BUG: !!!BACKQUOTE IS BROKEN!!! but maybe that's because our repl is broken?
;; first pass via reflection

;; setter things take a component and a spec and set things for them.
;; one setter thing for each type.
(defmacro cast-as [x type]
  (let [xsym (with-meta (gensym "caster_") {:tag type})]
    `(let [~xsym ~x] ~xsym)))

;; seem to be fucking up backquoted macro references?

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

(defn nice-keyword [s]
  (keyword (string/lower-case (camels-to-hyphens s))))

;; generate more if you feel it worth testing for
(defn keys-for-setable [{n :name}]
  [(nice-keyword (name n))])

(defn type-for-setable [sm]
  (:type sm)) ;; good enough for now

(defn setable-properties [typ]
  (ru/properties typ))

(defn setable-fields [typ]
  (->> typ
    ru/fields
    (filter
      (fn [{fs :flags}]
        (and
          (:public fs)
          (not (:static fs)))))))

(defn setables [typ]
  (concat
    (setable-properties typ)
    (setable-fields typ)))

(defn hydration-form [hdb, typ, vsym]
  (if-let [[_ form-fn] (find (:hydration-form-fns hdb) typ)]
    (form-fn hdb vsym)
    `(unity.hydrate/cast-as ~vsym ~typ)))

;; insert converters etc here if you feel like it
(defn setter-key-clauses [hdb, targsym typ vsym]
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables typ)
            :let [styp (type-for-setable setable)
                  hf (hydration-form hdb styp vsym)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n) ~hf)]))))

(defn setter-reducing-fn-form [hdb, ^System.MonoType typ]
  (let [ksym (gensym "spec-key_")
        vsym (gensym "spec-val_")
        targsym (with-meta (gensym "targ")
                  {:tag typ})
        skcs (setter-key-clauses targsym hdb typ vsym)
        fn-inner-name (symbol (str "setter-fn-for-" typ))]
    `(fn ~fn-inner-name ~[targsym ksym vsym] 
       (case ~ksym
         ~@skcs
         ~targsym))))

(defn prepare-spec [spec]
  (dissoc spec :type))

(defn setter-form [hdb, ^System.MonoType typ]
  (let [targsym  (with-meta (gensym "setter-target") {:tag typ})
        specsym  (gensym "spec")
        sr       (setter-reducing-fn-form hdb typ)]
    `(fn [~targsym spec#]
       (reduce-kv
         ~sr
         ~targsym
         (unity.hydrate/prepare-spec spec#)))))

(defn generate-setter [hdb type]
  (eval (setter-form hdb type)))

(defn all-component-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat #(.GetTypes %))
    (filter #(isa? % UnityEngine.Component))))

(defn key-for-type [^System.MonoType t] ;; hope this works
  (nice-keyword (.Name t)))

(defn expand-map-to-type-kws [m] ;; bit particular
  (merge m (mu/map-keys m key-for-type)))

(defn default-component-setter-database []
  ;; potentially bit of a bootstrapping issue here; stuart sierra's
  ;; component lib would be nice
  (let [m0 {:hydration-setters {} 
            :hydration-form-fns {}}]
    (-> m0
      (assoc m0
        :hydration-setters
        (expand-map-to-type-kws
          (let [vsym (gensym "vsym_")]
            (mu/map-vals
              hf/default-misc-hydration-form-fns
              (fn [ffn] ;; won't be fast. find a macro or something to do this?
                (let [hf (ffn m0 vsym)]
                  (eval `(fn [~vsym] ~hf)))))))
        
        :hydration-form-fns
        hf/default-misc-hydration-form-fns))))

(def component-setter-database
  (atom (default-component-setter-database)
    :validator (fn [m]
                 (and
                   (map? m)
                   (contains? m :hydration-setters)
                   (contains? m :hydration-form-fns)))))

(defn refresh-component-setter-database []
  (let [types (all-component-types)
        cm (expand-map-to-type-kws
             (zipmap types (map generate-setter types)))]
    (swap! component-setter-database
      (fn [db]
        (update-in db [:hydration-setters]
          (fn [hs]
            (merge hs cm)))))))

(comment ;; this works well, but each type takes about 500
         ;; milliseconds, and there are 80 built-in component types
         ;; alone. hrm. would it be that slow if we did it as a macro?
  (refresh-component-setter-database))
;;; fdf

(defmacro refresh-component-setter-database-as-a-macro
  ([& [n]]
     (let [types (if (or (not n) (= n :all))
                   (all-component-types)
                   (take n (all-component-types)))
           sfs   (map setter-form types)]
       `(let [cm# (unity.hydrate/expand-map-to-type-kws
                    (zipmap [~@types]
                      [~@sfs]))]
          (swap! unity.hydrate/component-setter-database ;; GOT to fix backquote!!
            (fn [db]
              (update-in db [:hydration-setters]
                (fn [hs]
                  (merge hs cm#)))))))))

(defn register-component [type]
  (let [sm (expand-map-to-type-kws
             {type (generate-setter component-setter-database type)})]
    (swap! component-setter-database ;; maybe it should be an agent
      (fn [db]
        (update-in db [:hydration-setters]
          (fn [hs]
            (merge hs sm)))))))

(comment
  (defn set-members [c spec]
    ((setter spec) c spec))

  (defn hydrate-component [^GameObject obj, spec]
    (set-members (initialize-component obj, spec) spec)))
