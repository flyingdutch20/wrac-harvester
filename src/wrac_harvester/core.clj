(ns wrac-harvester.core
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as string])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def rb-index (-> (client/get "https://www.runbritainrankings.com/results/resultslookup.aspx") :body parse as-hickory))

(-> (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))

(defn retrieve-urls
  "Returns a vector of maps for each sub-category on the current category"
  [tree]
  (let [links (s/select
               (s/child
                (s/id :cphBody_dgMeetings) s/first-child) rb-index)]
    (vec
     (map
      #(hash-map :date (-> (first %) :tag :td :content first :content first)
                 :url  (-> (nth % 4) :attrs :href)
                 :name (-> (nth % 2) :content first :content first))
      links))))

(retrieve-urls rb-index)

(s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index)
