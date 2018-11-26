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


(defn retrieve-rb-race
  [url]
  (-> (client/get url) :body parse as-hickory))

(defn retrieve-rb-race-header
  [rb-race]
  (let [header (-> (first (s/select (s/child (s/id :cphBody_lblMeetingDetails)) rb-race)) :content)]
    (hash-map :name (-> (first header) :content first)
              :date (nth header 6))))

;(retrieve-rb-race-header (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

(defn retrieve-rb-race-runners
  [rb-race]
  (let [lines
        (s/select (s/child (s/tag :tr)) (first (s/select (s/child (s/id :cphBody_gvP) s/first-child) rb-race)))]
    (vec
     (map
      #(hash-map :pos (-> % :content second :content first :content first)
                 :gun  (-> (nth (-> % :content) 5) :content first :content first :attrs :href)
                 :chip  (-> (nth (-> % :content) 5) :content first :content first :attrs :href)
                 :name  (-> (nth (-> % :content) 5) :content first :content first :attrs :href)
                 :club  (-> (nth (-> % :content) 5) :content first :content first :attrs :href)
                 )
      lines))))

(retrieve-rb-race-runners (retrieve-rb-race "https://www.runbritainrankings.com/results/results.aspx?meetingid=261023"))

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
	"\r\n\t\t\t\t\t"]}
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
