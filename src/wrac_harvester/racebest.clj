(ns wrac-harvester.racebest
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [wrac-harvester.utils :as utils]))


(def rbest-base-url "http://racebest.com")
(def rbest-index (-> (client/get (str rbest-base-url "/results")) :body parse as-hickory))
(def custom-formatter (fdt/formatter "d MMM yyyy"))


;(s/select (s/child (s/tag :tr)) rbest-index)
;(-> (nth (s/select (s/child (s/tag :tr)) rbest-index) 3) :content first :content first)
;(-> (nth (s/select (s/child (s/tag :tr)) rbest-index) 3) :content second :content first :content first :attrs :href)

(defn retrieve-urls
  "Returns a vector of maps for each race from the index"
  []
  (let [lines (s/select (s/child (s/tag :tr)) rbest-index)
        filtered (filter #(and
                        (string? (-> % :content first :content first))
                        (> (count (-> % :content first :content first)) 4)) lines)]
    (vec
     (map
      #(hash-map :date (fdt/parse custom-formatter (-> % :content first :content first))
                 :url  (-> % :content second :content first :content first :attrs :href))
      filtered))))

;(first (retrieve-urls))

(defn retrieve-urls-for-date
  [date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (:date %) comp-date))
          (retrieve-urls)))

;(utils/filter-site-urls-for-date retrieve-urls (dt/date-time 2018 11 23) )

(defn retrieve-race
  [url]
  (-> (slurp url) parse as-hickory))

;(retrieve-race "https://racebest.com/results/q66zt")
;(client/get "https://racebest.com/results/q66zt")
;(slurp "https://racebest.com/results/q66zt")

;(retrieve-race "https://racebest.com/results/q66zt")
;(slurp "https://racebest.com/results/q66zt")
;(-> (slurp "https://racebest.com/results/q66zt") :document parse as-hickory)

;(retrieve-race "https://racebest.com/results/ug9s7")

(defn retrieve-rbest-race-header
  [race]
    (hash-map :name (-> (s/select (s/tag :h2) race) first :content first)
              :date (-> (s/select (s/tag :h3) race) first :content first)))

;(-> (s/select (s/tag :h3) (retrieve-race "https://racebest.com/results/q66zt")) first :content first)

;(retrieve-rbest-race-header (retrieve-race "https://racebest.com/results/q66zt"))
;(:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-race "https://racebest.com/results/q66zt"))))
;(:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-race "https://racebest.com/results/q66zt"))))
;(first (:content (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-race "https://racebest.com/results/q66zt")))))

;(first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-race "https://racebest.com/results/q66zt"))))))


(defn filter-race-lines
  [race-lines size]
    (filter #(= (count (:content %)) size)
            race-lines))

(defn retrieve-lines
  [race]
  (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) race))))

;(map #(first (:content %)) (s/select (s/child (s/tag :td)) (first (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt")))))
;(.indexOf (into [] my-head) "Pos")
;(s/select (s/tag :th) (first (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt"))))

(defn make-header-vec
  [race-lines]
 (into []
    (if (empty? (s/select (s/tag :th) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :td)) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :th)) (first race-lines))))))

;(make-header-vec (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt")))
;(make-header-vec (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt")))
;(make-header-vec (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt")))
;(reduce str "" (rest "hello"))
;(str (first "hello"))

;(def my-lines (retrieve-lines (retrieve-race "https://racebest.com/results/q66zt")))
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

(defn retrieve-all-rbest-race-runners
  [race]
  (let [all (retrieve-lines race)
       header (make-header-vec all)
        filtered (filter-race-lines (rest all) (count header))]
    (vec
       (map
        #(case (count header)
           6 (map-6-col % header)
           (9 11) (map-9-col % header)
           (map-empty % header))
         filtered))))


;(count (:content (first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-race "https://racebest.com/results/q66zt"))))))))
;(retrieve-all-race-runners (retrieve-race "https://racebest.com/results/q66zt"))
;(retrieve-all-race-runners (retrieve-race "https://racebest.com/results/q66zt"))
;(retrieve-all-race-runners (retrieve-race "https://racebest.com/results/q66zt"))
;(utils/get-wetherby-runners (retrieve-all-race-runners (retrieve-race "https://racebest.com/results/q66zt")))

(defn retrieve-race-name
  [race]
  (let [header (retrieve-rbest-race-header race)]
    (str
      "Racebest - "
      (:date header)
      " - "
      (:name header)
    )))

;(retrieve-race-name (retrieve-race "https://racebest.com/results/q66zt"))
;(retrieve-race-name (retrieve-race "https://racebest.com/results/q66zt"))

(defn create-race-output
  [race]
  (let [runners (retrieve-all-rbest-race-runners race)
        wetherby-runners (utils/get-wetherby-runners runners)]
    (if (not-empty runners)
      (do
        (println (str "Processing: " (retrieve-race-name race)))
        (if (not-empty wetherby-runners)
          (let [filename (str "c:/output/" (retrieve-race-name race) ".csv")]
            (io/make-parents filename)
            (spit filename
                  (str
                    "," (retrieve-race-name race) " - " (count runners) " runners" "\n"
                    "," "First man " (utils/print-winner (utils/first-male runners)) " - first woman " (utils/print-winner (utils/first-female runners)) "\n"
                    "\n"
                    ",Pos,Name,Cat,Time\n"
                  (string/join (utils/create-runners-output wetherby-runners))
                    "\n"))
            (println (str "Created: " filename))))))))

;(create-race-output (retrieve-race "https://racebest.com/results/q66zt"))
;(create-race-output (retrieve-race "https://racebest.com/results/q66zt"))
;(create-race-output (retrieve-race "https://racebest.com/results/q66zt"))
;(create-race-output (retrieve-race (str rbest-base-url "/" (:url (first (retrieve-rbest-urls))))))

(defn output-wrac-rbest-results-for-date
  [date]
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting https://racebest.com"))
  (let [urls (utils/filter-site-urls-for-date retrieve-rbest-urls date)]
    (doseq [url urls]
;     (println (str "hello " (:url url)))
      (create-race-output (retrieve-race (str rbest-base-url "/" (:url url))))))
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Finished harvesting https://racebest.com"))
  )
;(retrieve-rbest-urls)
;(utils/filter-site-urls-for-date retrieve-rbest-urls (utils/extract-date-from "01 Jan 2019"))

(defn output-wrac-rbest-results-for-date-string
  [date-string]
  (let [date (try (utils/extract-date-from date-string)
               (catch Exception e false))]
    (if date
      (output-wrac-rbest-results-for-date date))))

(defn output-wrac-rbest-results
  []
  (output-wrac-rbest-results-for-date-string (str "01 Jan " current-year)))

;(output-wrac-rbest-results)

;(try (utils/extract-date-from "all") (catch Exception e false))

;(output-wrac-rbest-results-for-date-string "01 Jan 2019")


(defn output-wrac-rbest-results-for-number-of-weeks
  [weeks]
  (let [date (dt/minus (dt/now) (dt/weeks weeks))]
    (output-wrac-rbest-results-for-date date)))



(defn output-wrac-rbest-results-for-last-two-weeks
  []
  (output-wrac-rbest-results-for-number-of-weeks 2))

;(output-wrac-rbest-results-for-last-two-weeks)



; last line to avoid debug below eof
