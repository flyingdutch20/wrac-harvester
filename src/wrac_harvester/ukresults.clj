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


;(-> (nth (s/select (s/child (s/tag :tr)) ukr-index) 3) :content second :content first :attrs :href)

(defn retrieve-ukr-urls
  "Returns a vector of maps for each race from the index"
  []
  (let [lines (s/select (s/child (s/tag :tr)) ukr-index)
        filtered (filter #(and
                        (string? (-> % :content first :content first))
                        (> (count (-> % :content first :content first)) 4)) lines)]
    (vec
     (map
      #(hash-map :date (fdt/parse custom-formatter (str (-> % :content first :content first) " " current-year))
                 :url  (-> % :content second :content first :attrs :href))
      filtered))))

;(s/select (s/child (s/tag :tr)) ukr-index)
;(first (retrieve-ukr-urls))

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
;(retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")

(defn retrieve-ukr-race-header
  [ukr-race]
    (hash-map :name (-> (s/select (s/tag :h2) ukr-race) first :content first)
              :date (-> (s/select (s/tag :h3) ukr-race) first :content first)))

;(-> (s/select (s/tag :h3) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")) first :content first)

;(retrieve-ukr-race-header (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))))
;(:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))))
;(first (:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))))

;(first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))))))


(defn filter-race-lines
  [race-lines size]
    (filter #(= (count (:content %)) size)
            race-lines))

(defn retrieve-lines
  [ukr-race]
  (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) ukr-race))))

;(map #(first (:content %)) (s/select (s/child (s/tag :td)) (first (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))))
;(.indexOf (into [] (map #(first (:content %)) (s/select (s/child (s/tag :td)) (first (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))))) "Pos")
;(s/select (s/tag :th) (first (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))))

(defn make-header-vec
  [race-lines]
 (into []
    (if (empty? (s/select (s/tag :th) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :td)) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :th)) (first race-lines))))))

;(make-header-vec (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))
;(make-header-vec (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/schof.html")))
;(make-header-vec (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))
;(reduce str "" (rest "hello"))
;(str (first "hello"))

;(def my-lines (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))
;(def my-header (make-header-vec my-lines))

(defn get-value-for
  [field line header]
  (-> (nth (:content line) (.indexOf header field)) :content first))

;(get-value-for "Pos" (first (rest my-lines)) my-header)

(defn map-6-col
  [line header]
  (hash-map :pos   (get-value-for "Pos" line header)
             :gun   (get-value-for "Time" line header)
             :chip  (get-value-for "Time" line header)
             :name  (get-value-for "Name" line header)
             :cat   (reduce str "" (rest (get-value-for "Cat" line header)))
             :sex   (str (first (get-value-for "Cat" line header)))
             :club  (.toLowerCase (get-value-for "Club" line header))))

(defn map-9-col
  [line header]
  (hash-map :pos   (get-value-for "Pos" line header)
             :gun   (get-value-for "Time" line header)
             :chip  (get-value-for "Time" line header)
             :name  (get-value-for "Name" line header)
             :cat   (get-value-for "Cat" line header)
             :sex   (if (empty? (get-value-for "F" line header)) "M" "F")
             :club  (.toLowerCase (get-value-for "Club" line header))))

(defn map-empty
  [line header]
  (hash-map :pos   ""
             :gun   ""
             :chip  ""
             :name  ""
             :cat   ""
             :sex   ""
             :club  ""))

(defn retrieve-all-ukr-race-runners
  [ukr-race]
  (let [all (retrieve-lines ukr-race)
       header (make-header-vec all)
        filtered (filter-race-lines (rest all) (count header))]
    (vec
       (map
        #(case (count header)
           6 (map-6-col % header)
           (9 11) (map-9-col % header)
           (map-empty % header))
         filtered))))


;(count (:content (first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))))))))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2019/schof.html"))
;(utils/get-wetherby-runners (retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))

(defn retrieve-race-name
  [ukr-race]
  (let [header (retrieve-ukr-race-header ukr-race)]
    (str
      "UK Results - "
      (:date header)
      " - "
      (:name header)
    )))

;(retrieve-race-name (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(retrieve-race-name (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))

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

;(create-race-output (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(create-race-output (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))
;(create-race-output (retrieve-ukr-race "http://ukresults.net/2019/schof.html"))
;(create-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url (first (retrieve-ukr-urls))))))

(defn output-wrac-ukr-results-for-date
  [date]
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting http://ukresults.net"))
  (let [urls (utils/filter-site-urls-for-date retrieve-ukr-urls date)]
    (doseq [url urls]
      (create-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url url))))))
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Finished harvesting http://ukresults.net"))
  )


(defn output-wrac-ukr-results-for-date-string
  [date-string]
  (let [date (try (utils/extract-date-from date-string)
               (catch Exception e false))]
    (if date
      (output-wrac-ukr-results-for-date date))))

(defn output-wrac-ukr-results
  []
  (output-wrac-ukr-results-for-date-string (str "01 Jan " current-year)))

;(output-wrac-ukr-results)

;(try (utils/extract-date-from "all") (catch Exception e false))

;(output-wrac-ukr-results-for-date-string "01 Jan 2019")


(defn output-wrac-ukr-results-for-number-of-weeks
  [weeks]
  (let [date (dt/minus (dt/now) (dt/weeks weeks))]
    (output-wrac-ukr-results-for-date date)))



(defn output-wrac-ukr-results-for-last-two-weeks
  []
  (output-wrac-ukr-results-for-number-of-weeks 2))

;(output-wrac-ukr-results-for-last-two-weeks)



; last line to avoid debug below eof
