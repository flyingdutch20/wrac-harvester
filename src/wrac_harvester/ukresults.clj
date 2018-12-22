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

;(utils/filter-site-urls-for-date retrieve-ukr-urls (dt/date-time 2018 11 23) )


;
;(str ukr-base-url "/" current-year "/" (:url (first (utils/filter-site-urls-for-date retrieve-ukr-urls (dt/date-time 2018 11 23)))))

(defn retrieve-ukr-race
  [url]
  (-> (client/get url) :body parse as-hickory))

;(retrieve-ukr-race "http://ukresults.net/2018/dalby.html")

(defn retrieve-ukr-race-header
  [ukr-race]
    (hash-map :name (-> (s/select (s/tag :h2) ukr-race) first :content first)
              :date (-> (s/select (s/tag :h3) ukr-race) first :content first)))

;(-> (s/select (s/tag :h3) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")) first :content first)

;(retrieve-ukr-race-header (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))))

(first (rest (s/select (s/child (s/tag :tr))
          (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))
          )))

(comment
  {:type :element, :attrs nil, :tag :tr, :content [
                                                    {:type :element, :attrs nil, :tag :td, :content ["1"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["220"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["001"]}
                                                    {:type :element, :attrs nil, :tag :td, :content [" "]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["Gareth Green"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["M40"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["(001/038)"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["York Knavesmire Harriers"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["03.29/05.37"]}
                                                    {:type :element, :attrs nil, :tag :td, :content ["00:34:51"]}
                                                    {:type :element, :attrs nil, :tag :td, :content [" "]}]}
  )


(defn retrieve-all-ukr-race-runners
  [ukr-race]
  (let [lines (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) ukr-race))))]
    (vec
       (map
        #(hash-map :pos   (-> (nth (:content %) 0) :content first)
                   :gun   (-> (nth (:content %) 9) :content first)
                   :chip  (-> (nth (:content %) 9) :content first)
                   :name  (-> (nth (:content %) 4) :content first)
                   :cat   (-> (nth (:content %) 5) :content first)
                   :sex   (if (> (count (-> (nth (:content %) 2) :content first)) 1)
                              "M"
                              "F")
                   :club  (.toLowerCase (-> (nth (:content %) 7) :content first)))
         lines))))

;(utils/get-wetherby-runners (retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))

(defn retrieve-race-name
  [ukr-race]
  (let [header (retrieve-ukr-race-header ukr-race)]
    (str
      (:date header)
      " - UK Results - "
      (:name header)
    )))

;(retrieve-ukr-race-name (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))

(defn create-race-output
  [ukr-race]
  (let [runners (retrieve-all-ukr-race-runners ukr-race)
        wetherby-runners (utils/get-wetherby-runners runners)]
    (if (not-empty runners)
      (do
        (println (str "Processing: " (retrieve-race-name ukr-race)))
        (if (not-empty wetherby-runners)
          (let [filename (str "c:/output/" (retrieve-race-name ukr-race) ".csv")]
            (io/make-parents filename)
            (spit filename
                  (str
                    "," (retrieve-race-name ukr-race) " - " (count runners) " runners" "\n"
                    "," "First man " (utils/print-winner (utils/first-male runners)) " - first woman " (utils/print-winner (utils/first-female runners)) "\n"
                    "\n"
                    ",Pos,Name,Cat,Time\n"
                  (string/join (utils/create-runners-output wetherby-runners))
                    "\n"))
            (println (str "Created: " filename))))))))

;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))

(defn output-wrac-ukr-results
  []
  (let [urls (retrieve-ukr-urls)]
    (doseq [url urls]
      (create-ukr-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url url)))))))


(create-ukr-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url (first (retrieve-ukr-urls))))))

(output-wrac-ukr-results)

(defn output-wrac-ukr-results-for-date
  [date]
  (let [urls (utils/filter-site-urls-for-date retrieve-ukr-urls date)]
    (doseq [url urls]
      (create-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url url)))))))

(defn output-wrac-ukr-results-for-date-string
  [date-string]
  (println date-string)
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting http://www.ukresults.net"))
  (let [date (try (utils/extract-date-from date-string)
               (catch Exception e false))]
    (if date
      (output-wrac-ukr-results-for-date date)
      (output-wrac-ukr-results)))
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Finished harvesting http://www.ukresults.net"))
  )

;(try (utils/extract-date-from "all") (catch Exception e false))

;(output-wrac-rb-results-for-date-string "16 Dec 2018")

(defn output-wrac-ukr-results-for-last-two-weeks
  []
  (let [date (dt/minus (dt/now) (dt/weeks 2))]
    (output-wrac-ukr-results-for-date date)))

;(output-wrac-ukr-results-for-last-two-weeks)



; last line to avoid debug below eof
