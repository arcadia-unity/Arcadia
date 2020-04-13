(ns arcadia.internal.autocompletion
  (:require [clojure.main]))

;; This namespace has been adapted from a fork of the `clojure-complete` library by @sogaiu:
;; https://github.com/sogaiu/clojure-complete/blob/clr-support/src/complete/core.cljc
;; The original code was in turn adapted from swank-clojure (http://github.com/jochu/swank-clojure)
;; List of changes:
;; - Added support for keyword autocompletion
;; - Removed conditional reader tags

(defn namespaces
  "Returns a list of potential namespace completions for a given namespace"
  [ns]
  (map name (concat (map ns-name (all-ns)) (keys (ns-aliases ns)))))

(defn ns-public-vars
  "Returns a list of potential public var name completions for a given
  namespace"
  [ns]
  (map name (keys (ns-publics ns))))

(defn ns-vars
  "Returns a list of all potential var name completions for a given namespace"
  [ns]
  (for [[sym val] (ns-map ns) :when (var? val)]
    (name sym)))

(defn ns-classes
  "Returns a list of potential class name completions for a given namespace"
  [ns]
  (map name (keys (ns-imports ns))))

(def special-forms
  (map name '[def if do let quote var fn loop recur throw try monitor-enter
              monitor-exit dot new set!]))

(defn- static? [member]
  (.IsStatic member))

(defn static-members
  "Returns a list of potential static members for a given class"
  [^System.RuntimeType class]
  (for [member (concat (.GetMethods class)
                       (.GetFields class)
                       '()) :when (static? member)]
    (.Name member)))

(defn resolve-class [sym]
  (try (let [val (resolve sym)]
         (when (class? val) val))
       (catch Exception e
         (when (not= clojure.lang.TypeNotFoundException
                     (class (clojure.main/repl-exception e)))
           (throw e)))))

(defmulti potential-completions
  (fn [^String prefix ns]
    (cond (.StartsWith prefix ":") :keyword
          (.Contains prefix "/") :scoped
          (.Contains prefix ".") :class
          :else :var)))

(defmethod potential-completions :scoped
  [^String prefix ns]
  (when-let [prefix-scope
             (first (let [[x & _ :as pieces]
                          (.Split prefix (.ToCharArray "/"))]
                      (if (= x "")
                        '()
                        pieces)))]
    (let [scope (symbol prefix-scope)]
      (map #(str scope "/" %)
           (if-let [class (resolve-class scope)]
             (static-members class)
             (when-let [ns (or (find-ns scope)
                               (scope (ns-aliases ns)))]
               (ns-public-vars ns)))))))

(defmethod potential-completions :class
  [^String prefix ns]
  (concat (namespaces ns)))

(defmethod potential-completions :var
  [_ ns]
  (concat special-forms
          (namespaces ns)
          (ns-vars ns)
          (ns-classes ns)))

(def sym-key-map
  (-> clojure.lang.Keyword
      (.GetField "_symKeyMap" (enum-or BindingFlags/NonPublic BindingFlags/Static))
      (.GetValue nil)))

(defmethod potential-completions :keyword
  [_ _]
  (let [keyword-candidate-list
        (->> sym-key-map
             (.Values)
             (map #(str (.Target %))))]
    keyword-candidate-list))

(defn completions
  "Return a sequence of matching completions given a prefix string and an
  optional current namespace."
  ([prefix] (completions prefix *ns*))
  ([^String prefix ns]
   (-> (for [^String completion (potential-completions prefix ns)
             :when (.StartsWith completion prefix)]
         completion)
       distinct
       sort)))
