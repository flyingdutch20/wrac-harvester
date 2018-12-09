(ns wrac-harvester.core
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http.client :as client]
            [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def rb-index (-> (client/get "https://www.runbritainrankings.com/results/resultslookup.aspx") :body parse as-hickory))

;(-> (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))

(defn retrieve-rb-urls
  "Returns a vector of maps for each sub-category on the current category"
  [rb-index]
  (let [links (s/select (s/child (s/tag :tr))  (first (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index)))]
    (vec
     (map
      #(hash-map :date (-> % :content second :content first :content first)
                 :url  (-> (nth (-> % :content) 5) :content first :content first :attrs :href))
      links))))

;(retrieve-rb-urls rb-index)

(defn extract-date-from
  [date-string]
  (let [elms (reverse (string/split date-string #"\s"))]
    (fdt/parse (fdt/formatter "yyyyMMMdd") (str (nth elms 0) (nth elms 1) (nth elms 2)))))


;(extract-date-from "Mon 13 Tue 1 Nov 2018")


(defn retrieve-rb-urls-for-date
  [rb-index date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (extract-date-from (% :date)) comp-date))
          (retrieve-rb-urls rb-index)))

;(retrieve-rb-urls-for-date rb-index (dt/date-time 2018 11 23))



(defn retrieve-rb-race
  [url]
  (-> (client/get url) :body parse as-hickory))

(defn retrieve-rb-race-header
  [rb-race]
  (let [header (-> (first (s/select (s/child (s/id :cphBody_lblMeetingDetails)) rb-race)) :content)]
    (hash-map :name (-> (first header) :content first)
              :date (nth header 6))))

;(retrieve-rb-race-header (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))

(defn has-chip?
  [rb-race-header-line]
  (= (-> (nth (-> rb-race-header-line :content) 4) :content first :content first) "Chip"))

;(has-chip? (nth (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child)
;                                                 (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))))))

;(has-chip? (nth (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child)
;                                                 (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420"))))))



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


(defn retrieve-rb-race-runners
  [rb-race]
  (let [lines (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))
        chip (if (has-chip? (nth lines 2)) 1 0)
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
                   :sex   (-> (nth (:content %) (+ 10 chip)) :content first)
                   :club  (-> (nth (:content %) (+ 11 chip)) :content first))
         filtered))))

;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ; just gun time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=268481")) ; gun time and chip time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")) ; multiple races on one page
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241")) ; 6 pages


(defn retrieve-all-rb-race-runners
  [rb-race]
  (let [firstpage (retrieve-rb-race-runners rb-race)
        pages (retrieve-rb-race-pages rb-race)]
    (into firstpage
          (flatten
            (map
              #(retrieve-rb-race-runners
                 (retrieve-rb-race (str "https://www.runbritainrankings.com" (:page %))))
              pages)))))

;(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))
;(count (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))) ; 6 pages - 1500 runners
;(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))


(defn get-wetherby-runners
  [runners]
  (filter #(re-find #"(etherby)" (:club %)) runners))

;(get-wetherby-runners (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")))

(defn winners
  [runners]
  (filter #(= "1" (:pos %)) runners))

(defn single-race?
  [runners]
  (= 1 (count (winners runners))))

;(winners (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")))
;(winners (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")))
;(single-race? (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")))
;(single-race? (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")))

(defn first-male
  [runners]
  (first (filter #(= "M" (:sex %)) runners)))

(defn first-female
  [runners]
  (first (filter #(= "W" (:sex %)) runners)))

;(first-male (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")))
;(first-female (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")))

(defn print-winner
  [runner]
  (str (:pos runner) " " (:name runner) " - " (:club runner) " (" (if (:chip runner) (:chip runner) (:gun runner)) ")"))

(print-winner (first-male (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))))
(print-winner (first-female (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))))


(defn retrieve-race-name
  [rb-race]
  (let [header (-> (s/select (s/id :cphBody_lblMeetingDetails) rb-race) first :content)]
    (str
      (nth header (dec (count header)))
      " - "
      (-> header first :content first)
    )))

;(retrieve-race-name (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

(defn create-race-output
  [rb-race]
  (let [runners (retrieve-all-rb-race-runners rb-race)
        wetherby-runners (get-wetherby-runners runners)]
    (if (not-empty wetherby-runners)
      (str
        ";" (retrieve-race-name rb-race) " - " (count runners) " runners" "\n"
        ";" "First man " (print-winner (first-male runners)) " - first woman " (print-winner (first-female runners)) "\n"
        "\n"
        ))))


(create-race-output (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

