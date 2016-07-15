(ns arcadia.packages.data
  (:require [clojure.spec :as s]
            [arcadia.internal.spec :as as]))

(s/def ::artifact string?)

(s/def ::group string?)

(s/def ::group-artifact symbol?)

(s/def ::version string?)

(s/def ::normal-dependency
  (s/keys :req [::group ::artifact ::version]))

(s/def ::vector-dependency
  (s/and
    vector?
    (s/or
      :long (as/qwik-cat ::group ::artifact ::version)
      :short (as/qwik-cat ::group-artifact ::version))))

(s/def ::dependency (as/qwik-or ::vector-dependency ::normal-dependency))

(defn normalize-coordinates [coords]
  (as/qwik-conform [[k v] ::dependency coords]
    (case k
      ::vector-dependency
      (let [[ary conf3] v]
        (case ary
          :long conf3
          :short (let [{:keys [::group-artifact ::version]} conf3]
                   {::group (or (namespace group-artifact) (name group-artifact))
                    ::artifact (name group-artifact)
                    ::version version})))
      
      ::normal-dependency
      conf)))
