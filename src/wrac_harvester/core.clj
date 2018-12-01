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

;(retrieve-rb-race-pages (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=247241"))



(defn retrieve-rb-race-runners
  [rb-race]
  (let [lines (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))
        chip (if (has-chip? (nth lines 2)) 1 0)]
    (filter #(and
               (not= (count (:content %)) 3)
               (:content (second (:content %))))
      (vec
       (map
        #(hash-map :pos   (-> (nth (-> % :content) 2) :content first)
                   :gun   (-> (nth (-> % :content) 3) :content first)
                   :chip  (if-not (zero? chip) (-> (nth (-> % :content) 4) :content first))
                   :name  (-> (nth (-> % :content) (+ 7 chip)) :content first :content first)
                   :cat   (-> (nth (-> % :content) (+ 9 chip)) :content first)
                   :sex   (-> (nth (-> % :content) (+ 10 chip)) :content first)
                   :club  (-> (nth (-> % :content) (+ 11 chip)) :content first))
         (drop 3 lines))))))

(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))
(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=268481"))

;(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420"))
;229420 this one doesn't work because it has a second table on the same page
;<tr style="background-color:Transparent;"><td colspan="49"><a name="r2"></a><b>5K B</b></td></tr>
(:content (second (:content (nth (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420"))
                                                                        )) 91))))




(defn retrieve-rb-race-runners-chip
  "#WIP"
  [rb-race]
  (let [lines (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))
        chip (if (has-chip? (nth lines 2)) 1 0)]
    (filter #(and
               (not= (count (:content %)) 3)
               (:content (second (:content %))))
      (vec
       (map
        #(hash-map :pos   (-> % :content))
      (drop 3 lines))))))

(retrieve-rb-race-runners-chip (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=268481"))
(retrieve-rb-race-runners-chip (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420"))


(nth (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child)
                                                 (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=229420")
                                                 ))) 1)

(nth (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child)
                                                 (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023")
                                                 ))) 3)


(comment
  {:type :element, :attrs {:bgcolor "White"}, :tag :tr, :content
	["\r\n\t\t\t\t\t\t"
	{:type :element, :attrs nil, :tag :td, :content [
		"\r\n                        "
		{:type :element, :attrs {:id "cphBody_gvP_chkUseInHeadToHead_3", :type "checkbox", :name "ctl00$cphBody$gvP$ctl05$chkUseInHeadToHead"}, :tag :input, :content nil}
		"\r\n                    "]}
	{:type :element, :attrs nil, :tag :td, :content ["1"]}
	{:type :element, :attrs nil, :tag :td, :content ["34:51"]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [
		{:type :element, :attrs {:color "Black"}, :tag :font, :content [" "]}]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [
		{:type :element, :attrs {:color "Black"}, :tag :font, :content [" "]}]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [
		{:type :element, :attrs {:color "Black"}, :tag :font, :content [{:type :element, :attrs nil, :tag :b, :content [" "]}]}]}
	{:type :element, :attrs nil, :tag :td, :content [{:type :element, :attrs {:href "/runners/profile.aspx?athleteid=112443", :target "_blank"}, :tag :a, :content ["Gareth Green"]} " "]}
	{:type :element, :attrs nil, :tag :td, :content [{:type :element, :attrs {:color "#7D1414"}, :tag :font, :content [{:type :element, :attrs nil, :tag :b, :content [" "]}]}]}
	{:type :element, :attrs nil, :tag :td, :content ["V40"]}
	{:type :element, :attrs nil, :tag :td, :content ["M"]}
	{:type :element, :attrs nil, :tag :td, :content ["Knavesmire"]}
	{:type :element, :attrs nil, :tag :td, :content ["34:33"]}
	{:type :element, :attrs nil, :tag :td, :content ["34:33"]}
	{:type :element, :attrs {:align "center", :nowrap "nowrap"}, :tag :td, :content ["-1.2"]}
	{:type :element, :attrs nil, :tag :td, :content [" "]}
	{:type :element, :attrs nil, :tag :td, :content [
		{:type :element, :attrs {:href "/submit/identifyperformance.aspx?performanceid=48190803", :title "notify us of amends", :target "_blank"}, :tag :a, :content [
			{:type :element, :attrs {:src "/images/pot/email.gif", :border "0"}, :tag :img, :content nil}]}]}
	"\r\n\t\t\t\t\t"]})

(comment
{:type :element, :attrs {:bgcolor "#ECF2F7"}, :tag :tr, :content
	["\r\n\t\t\t\t\t\t"
	{:type :element, :attrs nil, :tag :td, :content [
		"\r\n                        "
		{:type :element, :attrs {:id "cphBody_gvP_chkUseInHeadToHead_4", :type "checkbox", :name "ctl00$cphBody$gvP$ctl06$chkUseInHeadToHead"}, :tag :input, :content nil}
		"\r\n                    "]}
	{:type :element, :attrs nil, :tag :td, :content ["2"]}
	{:type :element, :attrs nil, :tag :td, :content ["34:54"]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [{:type :element, :attrs {:color "Black"}, :tag :font, :content [" "]}]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [{:type :element, :attrs {:color "Black"}, :tag :font, :content [" "]}]}
	{:type :element, :attrs {:align "right", :nowrap "nowrap"}, :tag :td, :content [{:type :element, :attrs {:color "Black"}, :tag :font, :content [{:type :element, :attrs nil, :tag :b, :content [" "]}]}]}
	{:type :element, :attrs nil, :tag :td, :content [{:type :element, :attrs {:href "/runners/profile.aspx?athleteid=400783", :target "_blank"}, :tag :a, :content ["Nathan Veall"]} " "]}
	{:type :element, :attrs nil, :tag :td, :content [{:type :element, :attrs {:color "#7D1414"}, :tag :font, :content [{:type :element, :attrs nil, :tag :b, :content ["SB"]}]}]}
	{:type :element, :attrs nil, :tag :td, :content ["U23"]}
	{:type :element, :attrs nil, :tag :td, :content ["M"]}
	{:type :element, :attrs nil, :tag :td, :content ["York Triathlon"]}
	{:type :element, :attrs nil, :tag :td, :content ["34:54"]}
	{:type :element, :attrs nil, :tag :td, :content ["33:50"]}
	{:type :element, :attrs {:align "center", :nowrap "nowrap"}, :tag :td, :content ["1.4"]}
	{:type :element, :attrs nil, :tag :td, :content [" "]}
	{:type :element, :attrs nil, :tag :td, :content [
		{:type :element, :attrs {:href "/submit/identifyperformance.aspx?performanceid=48190804", :title "notify us of amends", :target "_blank"}, :tag :a, :content [
			{:type :element, :attrs {:src "/images/pot/email.gif", :border "0"}, :tag :img, :content nil}]}]}
	"\r\n\t\t\t\t\t"]}

  )
