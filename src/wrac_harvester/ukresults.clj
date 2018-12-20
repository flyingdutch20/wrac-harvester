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
(def current-year (dt/year (dt/today)))
(def ukr-index (-> (client/get (str ukr-base-url "/" current-year "/index.html")) :body parse as-hickory))
(def custom-formatter (fdt/formatter "dd MMMM yyyy"))


;(-> (nth (s/select (s/child (s/tag :tr)) ukr-index) 25) :content second :content first :attrs :href)

(defn retrieve-ukr-urls
  "Returns a vector of maps for each race from the index"
  []
  (let [lines (s/select (s/child (s/tag :tr)) ukr-index)
        filtered (filter #(string? (-> % :content first :content first)) lines)]
    (vec
     (map
      #(hash-map :date (fdt/parse custom-formatter (str (-> % :content first :content first) " " current-year))
                 :url  (-> % :content second :content first :attrs :href))
      filtered))))

;(retrieve-ukr-urls)

(defn retrieve-ukr-urls-for-date
  [date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (:date %) comp-date))
          (retrieve-ukr-urls)))

(utils/filter-site-urls-for-date retrieve-ukr-urls (dt/date-time 2018 11 23) )


; http://ukresults.net/2018/dalby.html
;(str ukr-base-url "/" current-year "/" (:url (first (utils/filter-site-urls-for-date retrieve-ukr-urls (dt/date-time 2018 11 23)))))


; last line to avoid debug below eof
