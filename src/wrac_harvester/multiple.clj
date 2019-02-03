(ns wrac-harvester.multiple
  (:require [hickory.core :as hc]
            [hickory.select :as s]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.java.io :as io]
            [wrac-harvester.utils :as utils]))


(def rb-base-url "https://www.runbritainrankings.com")

;(hc/as-hickory (hc/parse (:body (client/get (str rb-base-url "/results/resultslookup.aspx")))))


(defn retrieve-rb-urls
  "Returns a vector of maps for each sub-category on the current category"
  []
  (let [rb-index (hc/as-hickory (hc/parse (:body (client/get (str rb-base-url "/results/resultslookup.aspx")))))
        links (rest (s/select (s/child (s/tag :tr))  (first (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))))]
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
  (hc/as-hickory (hc/parse (:body (client/get url)))))


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

(defn filter-rb-race-lines
  [race-lines]
    (filter #(and
                      (:content (second (:content %)))
                      (> (count (:content (second (:content %)))) 2))
            race-lines))

(defn get-rb-header-line
  [race-lines]
  (first
    (filter #(and
                      (:content %)
                      (> (count (:content %)) 6))
            race-lines)))


(defn rb-has-chip?
  [lines]
  (let [line (get-rb-header-line lines)]
  (= (-> (nth (:content line) 4) :content first :content first) "Chip")))


(defn retrieve-rb-race-runners
  [rb-race]
  (let [lines (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))
        chip (if (rb-has-chip? lines) 1 0)
        filtered (filter-rb-race-lines lines)]
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

;(retrieve-rb-race-name (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

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


;(create-rb-race-output (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")) ;Dalby Dash
;(create-rb-race-output (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=251411")) ;Wetherby 10k


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

;
; ***** start of ukresults.net *****
;

(def ukr-base-url "http://ukresults.net")
(def current-year (dt/year (dt/today)))
(def ukr-index-url (str ukr-base-url "/" current-year "/index.html"))
(def ukr-index (hc/as-hickory (hc/parse (:body (client/get ukr-index-url)))))
(def custom-formatter (fdt/formatter "dd MMMM yyyy"))

;(hc/as-hickory (hc/parse (:body (client/get ukr-index-url))))
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
  (hc/as-hickory (hc/parse (:body (client/get url)))))

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


(defn filter-ukr-race-lines
  [race-lines size]
    (filter #(= (count (:content %)) size)
            race-lines))

(defn retrieve-ukr-lines
  [ukr-race]
  (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) ukr-race))))

;(map #(first (:content %)) (s/select (s/child (s/tag :td)) (first (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))))
;(.indexOf (into [] my-head) "Pos")
;(s/select (s/tag :th) (first (retrieve-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))))

(defn make-ukr-header-vec
  [race-lines]
 (into []
    (if (empty? (s/select (s/tag :th) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :td)) (first race-lines)))
      (map #(first (:content %)) (s/select (s/child (s/tag :th)) (first race-lines))))))

;(make-ukr-header-vec (retrieve-ukr-lines (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html")))
;(make-ukr-header-vec (retrieve-ukr-lines (retrieve-ukr-race "http://ukresults.net/2019/schof.html")))
;(make-ukr-header-vec (retrieve-ukr-lines (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))
;(make-ukr-header-vec (retrieve-ukr-lines (retrieve-ukr-race "http://ukresults.net/2019/3halls.html")))
;(reduce str "" (rest "hello"))
;(str (first "hello"))

;(def my-lines (retrieve-ukr-lines (retrieve-ukr-race "http://ukresults.net/2019/3halls.html")))
;(def my-header (make-ukr-header-vec my-lines))

(defn get-ukr-value-for
  [field line header]
  (let [idx (.indexOf header field)]
    (if (pos? idx)
      (-> (nth (:content line) idx) :content first)
      "")))

;(get-ukr-value-for "Pos" (first (rest my-lines)) my-header)
;(try (.indexOf my-header "Cat") (catch Exception e ""))
;(.indexOf my-header "Cat")

(defn map-ukr-6-col
  [line header]
  (hash-map :pos   (get-ukr-value-for "Pos" line header)
            :gun   (get-ukr-value-for "Time" line header)
            :chip  (get-ukr-value-for "Time" line header)
            :name  (get-ukr-value-for "Name" line header)
            :cat   (reduce str "" (rest (get-ukr-value-for "Cat" line header)))
            :sex   (str (first (get-ukr-value-for "Cat" line header)))
            :club  (.toLowerCase (get-ukr-value-for "Club" line header))))

(defn map-ukr-9-col
  [line header]
  (hash-map :pos   (get-ukr-value-for "Pos" line header)
            :gun   (get-ukr-value-for "Time" line header)
            :chip  (get-ukr-value-for "Time" line header)
            :name  (get-ukr-value-for "Name" line header)
            :cat   (get-ukr-value-for "Cat" line header)
            :sex   (if (empty? (get-ukr-value-for "F" line header)) "M" "F")
            :club  (.toLowerCase (get-ukr-value-for "Club" line header))))

(defn map-ukr-empty
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
  (let [all (retrieve-ukr-lines ukr-race)
       header (make-ukr-header-vec all)
       filtered (filter-ukr-race-lines (rest all) (count header))]
    (vec
       (map
        #(case (count header)
           6      (map-ukr-6-col % header)
           (9 11) (map-ukr-9-col % header)
                  (map-ukr-empty % header))
         filtered))))


;(count (:content (first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))))))))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))
;(retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2019/schof.html"))
;(utils/get-wetherby-runners (retrieve-all-ukr-race-runners (retrieve-ukr-race "http://ukresults.net/2018/dalby.html")))

(defn retrieve-ukr-race-name
  [ukr-race]
  (let [header (retrieve-ukr-race-header ukr-race)]
    (str
      "UK Results - "
      (:date header)
      " - "
      (:name header)
    )))

;(retrieve-ukr-race-name (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(retrieve-ukr-race-name (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))

(defn create-ukr-race-output
  [ukr-race]
  (let [runners (retrieve-all-ukr-race-runners ukr-race)
        wetherby-runners (utils/get-wetherby-runners runners)]
    (if (not-empty runners)
      (do
        (println (str "Processing: " (retrieve-ukr-race-name ukr-race)))
        (if (not-empty wetherby-runners)
          (let [filename (str "c:/output/" (retrieve-ukr-race-name ukr-race) ".csv")]
            (io/make-parents filename)
            (spit filename
                  (str
                    "," (retrieve-ukr-race-name ukr-race) " - " (count runners) " runners" "\n"
                    "," "First man " (utils/print-winner (utils/first-male runners)) " - first woman " (utils/print-winner (utils/first-female runners)) "\n"
                    "\n"
                    ",Pos,Name,Cat,Time\n"
                  (string/join (utils/create-runners-output wetherby-runners))
                    "\n"))
            (println (str "Created: " filename))))))))

;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2018/dalby.html"))
;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2019/morpeth11k.html"))
;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2019/schof.html"))
;(create-ukr-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url (first (retrieve-ukr-urls))))))
;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2019/elhfun.html"))
;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2019/sheffxc.pdf"))
;(create-ukr-race-output (retrieve-ukr-race "http://ukresults.net/2019/3halls.html"))



(defn output-wrac-ukr-results-for-date
  [date]
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting http://ukresults.net"))
  (let [urls (utils/filter-site-urls-for-date retrieve-ukr-urls date)]
    (doseq [url urls]
      (create-ukr-race-output (retrieve-ukr-race (str ukr-base-url "/" current-year "/" (:url url))))))
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Finished harvesting http://ukresults.net"))
  )

;(retrieve-ukr-urls)
;(utils/filter-site-urls-for-date retrieve-ukr-urls (utils/extract-date-from "01 Jan 2019"))

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

;
; ***** start of racebest.com *****
;

(def rbest-base-url "http://racebest.com")
(def rbest-index (hc/as-hickory (hc/parse (:body (client/get (str rbest-base-url "/results"))))))
(def rbest-custom-formatter (fdt/formatter "d MMM yyyy"))

;(s/select (s/child (s/tag :tr)) rbest-index)
;(-> (nth (s/select (s/child (s/tag :tr)) rbest-index) 3) :content first :content first)
;(-> (nth (s/select (s/child (s/tag :tr)) rbest-index) 3) :content second :content first :content first :attrs :href)

(defn retrieve-rbest-urls
  "Returns a vector of maps for each race from the index"
  []
  (let [lines (s/select (s/child (s/tag :tr)) rbest-index)
        filtered (filter #(and
                        (string? (-> % :content first :content first))
                        (> (count (-> % :content first :content first)) 4)) lines)]
    (vec
     (map
      #(hash-map :date (fdt/parse rbest-custom-formatter (-> % :content first :content first))
                 :url  (-> % :content second :content first :content first :attrs :href))
      filtered))))

;(first (retrieve-rbest-urls))

(defn retrieve-rbest-urls-for-date
  [date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (:date %) comp-date))
          (retrieve-rbest-urls)))

;(utils/filter-site-urls-for-date retrieve-rbest-urls (dt/date-time 2018 11 23) )
;(utils/filter-site-urls-for-date retrieve-rbest-urls (dt/date-time 2019 01 15) )

(defn retrieve-rbest-race
  [url]
  (hc/as-hickory (hc/parse (:body (client/get url)))))


;hc/as-hickory (hc/parse (:body
;(:content (first (:content (second (:content (retrieve-rbest-race "https://racebest.com/results/q66zt"))))))
;(first (:content (first (s/select (s/tag :title) (retrieve-rbest-race "https://racebest.com/results/q66zt")))))
;(hc/as-hickory (hc/parse (:body (client/get "https://racebest.com/results/q66zt"))))
;(hc/as-hickory (hc/parse (:body (client/get "https://racebest.com/results/eyhu9"))))

;(retrieve-rbest-race "https://racebest.com/results/q66zt")
;(slurp "https://racebest.com/results/q66zt")
;(-> (slurp "https://racebest.com/results/q66zt") :document parse as-hickory)

;(retrieve-rbest-race "https://racebest.com/results/ug9s7")
;(retrieve-rbest-race "https://racebest.com/results/eyhu9")


; the race itself doesn't have the date. The index does have the date and title

(defn retrieve-rbest-race-header
  [race date]
    (hash-map :name (-> (s/select (s/tag :title) race) first :content first)
              :date (fdt/unparse rbest-custom-formatter date)))


;(retrieve-rbest-race-header (retrieve-rbest-race "https://racebest.com/results/q66zt") (dt/date-time 2019 01 15))

;(s/select (s/class :hidden-phone) (first (s/select (s/child (s/tag :thead)) (retrieve-rbest-race "https://racebest.com/results/q66zt"))))


;headings
;(map #(-> % :content first)
;     (s/select (s/class :hidden-phone)
;               (first (s/select (s/child (s/tag :thead))
;                                (retrieve-rbest-race "https://racebest.com/results/q66zt")))))

;race lines in hickory format
;(:content (first (s/select (s/tag :tbody) (retrieve-rbest-race "https://racebest.com/results/q66zt"))))


(defn filter-rbest-race-lines
  [race-lines size]
    (filter #(= (count (:content %)) size)
            race-lines))

(defn retrieve-rbest-lines
  [race]
  (:content (first (s/select (s/tag :tbody) race))))



;(retrieve-rbest-lines (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(map #(first (:content %)) (s/select (s/child (s/tag :td)) (first (retrieve-rbest-lines (retrieve-rbest-race "https://racebest.com/results/q66zt")))))
;(.indexOf (into [] my-head) "Pos")
;(s/select (s/tag :th) (first (retrieve-rbest-lines (retrieve-rbest-race "https://racebest.com/results/q66zt"))))


(defn make-rbest-header-vec
  [race]
  (into []
  (map #(-> % :content first)
     (s/select (s/class :hidden-phone)
               (first (s/select (s/child
                                  (s/tag :thead))
                                race))))))

;(make-rbest-header-vec (retrieve-rbest-race "https://racebest.com/results/q66zt"))

;(def my-rbest-lines (retrieve-rbest-lines (retrieve-rbest-race "https://racebest.com/results/q66zt")))
;(def my-rbest-header (make-rbest-header-vec (retrieve-rbest-race "https://racebest.com/results/q66zt")))


(defn get-rbest-value-for
  [field line header]
  (-> (nth (:content line) (.indexOf header field)) :content first))

;(get-rbest-value-for "Name" (second my-rbest-lines) my-rbest-header)


(defn map-6-rbest-col
  [line header]
  (hash-map :pos   (get-rbest-value-for "Position" line header)
             :gun   (get-rbest-value-for "Time" line header)
             :chip  (get-rbest-value-for "Time" line header)
             :name  (get-rbest-value-for "Name" line header)
             :cat   (reduce str "" (rest (get-rbest-value-for "Category" line header)))
             :sex   (str (first (get-rbest-value-for "Category" line header)))
             :club  (.toLowerCase (get-rbest-value-for "Club" line header))))

(defn map-9-rbest-col
  [line header]
  (hash-map :pos   (get-rbest-value-for "Position" line header)
             :gun   (get-rbest-value-for "Time" line header)
             :chip  (get-rbest-value-for "Time" line header)
             :name  (get-rbest-value-for "Name" line header)
             :cat   (reduce str "" (rest (get-rbest-value-for "Category" line header)))
             :sex   (str (first (get-rbest-value-for "Category" line header)))
             :club  (.toLowerCase (get-rbest-value-for "Club" line header))))

(defn map-rbest-empty
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
  (let [all (retrieve-rbest-lines race)
       header (make-rbest-header-vec race)
        filtered (filter-rbest-race-lines all (count header))]
    (vec
       (map
        #(case (count header)
           6 (map-6-rbest-col % header)
           (9 11) (map-9-rbest-col % header)
           (map-rbest-empty % header))
         filtered))))


;(count (:content (first (rest (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/class :sortable) s/first-child) (retrieve-rbest-race "https://racebest.com/results/q66zt"))))))))
;(retrieve-all-rbest-race-runners (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(retrieve-all-rbest-race-runners (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(retrieve-all-rbest-race-runners (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(utils/get-wetherby-runners (retrieve-all-rbest-race-runners (retrieve-rbest-race "https://racebest.com/results/q66zt")))

(defn retrieve-rbest-race-name
  [race date]
  (let [header (retrieve-rbest-race-header race date)]
    (str
      "Racebest - "
      (:date header)
      " - "
      (:name header)
    )))

;(retrieve-rbest-race-name (retrieve-rbest-race "https://racebest.com/results/q66zt") (dt/date-time 2019 01 15))
;(retrieve-rbest-race-name (retrieve-rbest-race "https://racebest.com/results/q66zt"))

(defn create-rbest-race-output
  [race date]
  (let [runners (retrieve-all-rbest-race-runners race)
        wetherby-runners (utils/get-wetherby-runners runners)]
    (if (not-empty runners)
      (do
        (println (str "Processing: " (retrieve-rbest-race-name race date)))
        (if (not-empty wetherby-runners)
          (let [filename (str "c:/output/" (retrieve-rbest-race-name race date) ".csv")]
            (io/make-parents filename)
            (spit filename
                  (str
                    "," (retrieve-rbest-race-name race date) " - " (count runners) " runners" "\n"
                    "," "First man " (utils/print-winner (utils/first-male runners)) " - first woman " (utils/print-winner (utils/first-female runners)) "\n"
                    "\n"
                    ",Pos,Name,Cat,Time\n"
                  (string/join (utils/create-runners-output wetherby-runners))
                    "\n"))
            (println (str "Created: " filename))))))))

;(create-rbest-race-output (retrieve-rbest-race "https://racebest.com/results/q66zt") (dt/date-time 2019 01 15))
;(create-rbest-race-output (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(create-rbest-race-output (retrieve-rbest-race "https://racebest.com/results/q66zt"))
;(create-rbest-race-output (retrieve-rbest-race (str rbest-base-url "/" (:url (first (retrieve-rbest-urls))))))

(defn output-wrac-rbest-results-for-date
  [date]
  (println (str (fdt/unparse (fdt/formatters :hour-minute) nil) " - Harvesting https://racebest.com"))
  (let [urls (utils/filter-site-urls-for-date retrieve-rbest-urls date)]
    (doseq [url urls]
;     (println (str "hello " (:url url)))
      (create-rbest-race-output (retrieve-rbest-race (str rbest-base-url "/" (:url url))) (:date url))))
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

(defn output-wrac-rbest-results
  []
  (output-wrac-rbest-results-for-number-of-weeks 52))

;(output-wrac-rbest-results)




; last line to avoid debug below eof
