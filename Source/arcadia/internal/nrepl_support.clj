(ns arcadia.internal.nrepl-support
  (:import [BList]
           [BDictionary]))

(defn complete-symbol [text]
  (let [[ns prefix-str] (as-> text <>
                          (symbol <>)
                          [(some-> <> namespace symbol) (name <>)])
        ns-to-check (if ns
                      (or ((ns-aliases *ns*) ns) (find-ns ns))
                      *ns*)
        fn-candidate-list (when ns-to-check
                            (if ns
                              (map str (keys (ns-publics ns-to-check)))
                              (map str (keys (ns-map ns-to-check)))))]
    (into '() (comp (filter #(.StartsWith % prefix-str))
                    (map #(if ns (str ns "/" %) %))
                    (map #(-> {:candidate %
                               :type "function"})))
          (concat
           fn-candidate-list))))

(defn complete-namespace [text]
  (let [[ns prefix-str] (as-> text <>
                          (symbol <>)
                          [(some-> <> namespace symbol) (name <>)])
        ns-candidate-list (when-not ns
                            (map (comp str ns-name) (all-ns)))]
    (into '() (comp (filter #(.StartsWith % prefix-str))
                    (map str)
                    (map #(-> {:candidate %
                               :type "namespace"})))
          ns-candidate-list)))

(def sym-key-map
  (-> clojure.lang.Keyword
      (.GetField "_symKeyMap" (enum-or BindingFlags/NonPublic BindingFlags/Static))
      (.GetValue nil)))

(defn complete-keyword [text]
  (let [keyword-candidate-list
        ;; NOTE: :_ is used here to get an instance to some keyword.
        (->> sym-key-map
             (.Values)
             (map #(str (.Target %))))]
    (into '() (comp (filter #(.StartsWith % text))
                    (map #(-> {:candidate %
                               :type "keyword"})))
          keyword-candidate-list)))

(defn bencode-result
  "Converts a seq of completion maps into a BList of BDictionary"
  [completions]
  (let [blist (BList.)]
    (doseq [{:keys [candidate, type]} completions]
      (.Add blist (doto (BDictionary.)
                    (.Add "candidate" candidate)
                    (.Add "type" type))))
    blist))

(defn complete [^String prefix]
  (bencode-result
   (cond
     (.StartsWith prefix ":") (complete-keyword prefix)
     (.Contains prefix "/") (complete-symbol prefix)
     :else
     (concat (complete-symbol prefix)
             (complete-namespace prefix)))))
