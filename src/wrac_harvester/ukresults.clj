(ns wrac-harvester.ukresults
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [wrac-harvester.utils :as utils]))


(def ukr-base-url "http://ukresults.net")
(def ukr-index (-> (client/get (str ukr-base-url "/" (dt/year (dt/today)) "/index.html")) :body parse as-hickory))


;(-> (nth (s/select (s/child (s/tag :tr)) ukr-index) 25) :content second :content first :attrs :href)

(defn retrieve-ukr-urls
  "Returns a vector of maps for each sub-category on the current category"
  [ukr-index]
  (let [links (s/select (s/child (s/tag :tr)) ukr-index)]
    (vec
     (map
      #(hash-map :date (-> % :content first :content first)
                 :url  (-> % :content second :content first :attrs :href))
      links))))

(retrieve-ukr-urls ukr-index)

; works but returns numerous other lines. Either first filter lines or create a filter afterwards
