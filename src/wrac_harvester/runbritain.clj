(ns wrac-harvester.runbritain
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [wrac-harvester.utils :as utils]))


(def rb-base-url "https://www.runbritainrankings.com")
(def rb-index (-> (client/get (str rb-base-url "/results/resultslookup.aspx")) :body parse as-hickory))

;(rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))))

(defn retrieve-rb-urls
  "Returns a vector of maps for each sub-category on the current category"
  []
  (let [links (rest (s/select (s/child (s/tag :tr))  (first (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))))]
    (vec
     (map
      #(hash-map :date (utils/extract-date-from (-> % :content second :content first :content first))
                 :url  (-> (nth (-> % :content) 5) :content first :content first :attrs :href))
      links))))

;(retrieve-rb-urls)


(defn retrieve-rb-urls-for-date
  [date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (:date %) comp-date))
          (retrieve-rb-urls)))

;(retrieve-rb-urls-for-date (dt/date-time 2018 11 23))


(defn retrieve-rb-race
  [url]
  (-> (client/get url) :body parse as-hickory))

(defn retrieve-rb-race-header
  [rb-race]
  (let [header (-> (first (s/select (s/child (s/id :cphBody_lblMeetingDetails)) rb-race)) :content)]
    (hash-map :name (-> (first header) :content first)
              :date (nth header 6))))

;(retrieve-rb-race-header (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))


(defn retrieve-rb-race-pages
  [rb-race]
  (let [pages (-> (s/select (s/id :cphBody_lblTopPageLinks) rb-race) first :content)]
    (filter #(% :page)
      (vec
        (map
          #(hash-map :page (-> % :attrs :href))
          pages)))))

;(retrieve-rb-race-pages (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241")) ; 6 pages
;(retrieve-rb-race-pages (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ; 2 pages

(defn filter-race-lines
  [race-lines]
    (filter #(and
                      (:content (second (:content %)))
                      (> (count (:content (second (:content %)))) 2))
            race-lines))

(defn get-header-line
  [race-lines]
  (first
    (filter #(and
                      (:content %)
                      (> (count (:content %)) 6))
            race-lines)))


(defn has-chip?
  [lines]
  (let [line (get-header-line lines)]
  (= (-> (nth (:content line) 4) :content first :content first) "Chip")))


(defn retrieve-rb-race-runners
  [rb-race]
  (let [lines (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))
        chip (if (has-chip? lines) 1 0)
        filtered (filter-race-lines lines)]
    (vec
       (map
        #(hash-map :pos   (-> (nth (:content %) 2) :content first)
                   :gun   (-> (nth (:content %) 3) :content first)
                   :chip  (if-not (zero? chip) (-> (nth (:content %) 4) :content first))
                   :name  (if (-> (nth (:content %) (+ 7 chip)) :content first :content first)
                            (-> (nth (:content %) (+ 7 chip)) :content first :content first)
                            (-> (nth (:content %) (+ 7 chip)) :content first))
                   :cat   (-> (nth (:content %) (+ 9 chip)) :content first)
                   :sex   (let [sex (-> (nth (:content %) (+ 10 chip)) :content first)]
                            (case sex
                              "M" "M"
                              "W" "F"
                              ""))
                   :club  (.toLowerCase (-> (nth (:content %) (+ 11 chip)) :content first)))
         filtered))))


;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ; just gun time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=268481")) ; gun time and chip time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")) ; multiple races on one page
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241")) ; 6 pages
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=251411")) ; Wetherby 10k


(defn retrieve-all-rb-race-runners
  [rb-race]
  (let [firstpage (retrieve-rb-race-runners rb-race)
        pages (retrieve-rb-race-pages rb-race)]
    (into firstpage
          (apply concat
            (map
              #(retrieve-rb-race-runners
                 (retrieve-rb-race (str "https://www.runbritainrankings.com" (:page %))))
              pages)))))

;(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))
;(count (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))) ; 6 pages - 1500 runners
;(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))
;(count (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=251411"))) ; 4 pages - 873 runners
;(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=251411")) ; 4 pages - 873 runners


(defn retrieve-rb-race-name
  [rb-race]
  (let [header (-> (s/select (s/id :cphBody_lblMeetingDetails) rb-race) first :content)]
    (str
      "Run Britain - "
      (nth header (dec (count header)))
      " - "
      (-> header first :content first)
    )))

;(retrieve-race-name (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

(defn create-rb-race-output
  [rb-race]
  (let [runners (retrieve-all-rb-race-runners rb-race)
        wetherby-runners (utils/get-wetherby-runners runners)]
    (if (not-empty runners)
      (do
        (println (str "Processing: " (retrieve-rb-race-name rb-race)))
        (if (not-empty wetherby-runners)
          (let [filename (str "c:/output/" (retrieve-rb-race-name rb-race) ".csv")]
            (io/make-parents filename)
            (spit filename
                  (str
                    "," (retrieve-rb-race-name rb-race) " - " (count runners) " runners" "\n"
                    "," "First man " (utils/print-winner (utils/first-male runners)) " - first woman " (utils/print-winner (utils/first-female runners)) "\n"
                    "\n"
                    ",Pos,Name,Cat,Time\n"
                  (string/join (utils/create-runners-output wetherby-runners))
                    "\n"))
            (println (str "Created: " filename))))))))


;(create-race-output (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ;Dalby Dash
;(create-race-output (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=251411")) ;Wetherby 10k


;(retrieve-rb-urls-for-date rb-index (dt/date-time 2018 11 23))

(defn output-wrac-rb-results-for-date
  [date]
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting https://www.runbritainrankings.com"))
  (let [urls (utils/filter-site-urls-for-date retrieve-rb-urls date)]
    (doseq [url urls]
      (create-rb-race-output (retrieve-rb-race (str rb-base-url (:url url))))))
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Finished harvesting https://www.runbritainrankings.com"))
  )

(defn output-wrac-rb-results
  []
  (let [urls (retrieve-rb-urls)]
    (doseq [url urls]
      (create-rb-race-output (retrieve-rb-race (str rb-base-url (:url url)))))))

;(output-wrac-rb-results)

(defn output-wrac-rb-results-for-number-of-weeks
  [weeks]
  (let [date (dt/minus (dt/now) (dt/weeks weeks))]
    (output-wrac-rb-results-for-date date)))

(defn output-wrac-rb-results-for-last-two-weeks
  []
  (output-wrac-rb-results-for-number-of-weeks 2))

;(output-wrac-rb-results-for-last-two-weeks)

(defn output-wrac-rb-results-for-date-string
  [date-string]
  (let [date (try (utils/extract-date-from date-string)
               (catch Exception e false))]
    (if date
      (output-wrac-rb-results-for-date date)
      (output-wrac-rb-results)))
  )

;(try (utils/extract-date-from "all") (catch Exception e false))

;(output-wrac-rb-results-for-date-string "16 Dec 2018")



; comment for debugger
