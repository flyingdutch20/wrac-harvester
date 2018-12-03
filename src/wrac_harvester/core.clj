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

(retrieve-rb-race-pages (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))

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
        #(hash-map :pos   (-> (nth (-> % :content) 2) :content first)
                   :gun   (-> (nth (-> % :content) 3) :content first)
                   :chip  (if-not (zero? chip) (-> (nth (-> % :content) 4) :content first))
                   :name  (-> (nth (-> % :content) (+ 7 chip)) :content first :content first)
                   :cat   (-> (nth (-> % :content) (+ 9 chip)) :content first)
                   :sex   (-> (nth (-> % :content) (+ 10 chip)) :content first)
                   :club  (-> (nth (-> % :content) (+ 11 chip)) :content first))
         filtered))))

;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ; just gun time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=268481")) ; gun time and chip time
;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")) ; multiple races on one page



(defn retrieve-all-rb-race-runners
  [rb-race]
  (let [firstpage (retrieve-rb-race-runners rb-race)
        pages (retrieve-rb-race-pages rb-race)]
;    (into firstpage
;          (retrieve-rb-race-runners
;            (retrieve-rb-race (str "https://www.runbritainrankings.com" (:page (first pages))))))))

    (map #(into firstpage (retrieve-rb-race-runners
            (retrieve-rb-race (str "https://www.runbritainrankings.com" (:page %)))))
         pages)))



(def first [{:a 1} {:a 2}])
(def rest {[{:a 3} {:a 4}] [{:a 5} {:a 6}]})

(#(into first %) rest)


(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))
;(retrieve-all-rb-race-runners)

(def race (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))
(retrieve-rb-race-runners race)
(retrieve-rb-race-pages race)



(count (retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241")))
(retrieve-all-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))
