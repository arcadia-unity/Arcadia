(ns unity.hydrate
  (:require [unity.map-utils :as mu]
            [unity.seq-utils :as su]
            [unity.reflect-utils :as ru]
            [clojure.string :as string])
  (:import UnityEditor.AssetDatabase
           [System.Reflection Assembly]
           System.AppDomain))

;; BUG: !!!BACKQUOTE IS BROKEN!!! but maybe that's because our repl is broken?
;; BUG: case doesn't work for types!

;; first pass via reflection

;; setter things take a component and a spec and set things for them.
;; one setter thing for each type.
(defmacro cast-as [x type]
  (let [xsym (with-meta (gensym "caster_") {:tag type})]
    `(let [~xsym ~x] ~xsym)))

;; seem to be fucking up backquoted macro references?

(defn camels-to-hyphens [s]
  (string/replace s #"([a-z])([A-Z])" "$1-$2"))

;; generate more if you feel it worth testing for
(defn keys-for-setable [{n :name}]
  [(keyword (string/lower-case (camels-to-hyphens (name n))))])

(defn type-for-setable [{typ :type}]
  typ) ;; good enough for now

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

;; insert converters etc here if you feel like it
(defn setter-key-clauses [targsym typ vsym]
  (let [valsym (with-meta (gensym) {:tag typ})]
    (apply concat
      (for [{n :name, :as setable} (setables typ)
            :let [styp (type-for-setable setable)]
            k  (keys-for-setable setable)]
        `[~k (set! (. ~targsym ~n) 
               (unity.hydrate/cast-as ~vsym ~styp))]))))


(defn setter-reducing-fn-form [^System.MonoType typ]
  (let [ksym (gensym "spec-key_")
        vsym (gensym "spec-val_")
        targsym (with-meta (gensym "targ")
                  {:tag typ})
        skcs (setter-key-clauses targsym typ vsym)
        fn-inner-name (symbol (str "setter-fn-for-" typ))]
    `(fn ~fn-inner-name ~[targsym ksym vsym] 
       (case ~ksym
         ~@skcs
         ~targsym))))

(defn prepare-spec [spec]
  (dissoc spec :type))

(defn setter-form [^System.MonoType typ]
  (let [targsym  (with-meta (gensym "setter-target") {:tag typ})
        specsym  (gensym "spec")
        sr       (setter-reducing-fn-form typ)]
    `(fn [~targsym spec#]
       (reduce-kv
         ~sr
         ~targsym
         (unity.hydrate/prepare-spec spec#)))))

(defn generate-setter [typ]
  (eval (setter-form type)))

(defn all-component-types []
  (->>
    (.GetAssemblies AppDomain/CurrentDomain)
    (mapcat #(.GetTypes %))
    (filter #(isa? % UnityEngine.Component))))

(def component-setter-database
  (atom {} :validator map?))

(defn refresh-component-setter-database []
  (let [types (all-component-types)]
    (reset! component-setter-database
      (zipmap types (map generate-setter types)))))

(comment ;; this works well, but each type takes about 500
         ;; milliseconds, and there are 80 built-in component types
         ;; alone. hrm. would it be that slow if we did it as a macro?
  (refresh-component-setter-database))

(defmacro refresh-component-setter-database-as-a-macro
  ([& [n]]
     (let [types (if (or (not n) (= n :all))
                   (all-component-types)
                   (take n (all-component-types)))
           sfs   (map generate-setter-form types)]
       `(let [ts# [~@types]]
          (reset! unity.hydrate/component-setter-database ;; GOT to fix backquote!!
            (zipmap [~@types]
              [~@sfs]))))))

(comment
  (defn set-members [c spec]
    ((setter spec) c spec))

  (defn hydrate-component [^GameObject obj, spec]
    (set-members (initialize-component obj, spec) spec)))


