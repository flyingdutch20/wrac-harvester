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

(-> (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))

(defn retrieve-urls
  "Returns a vector of maps for each sub-category on the current category"
  [tree]
  (let [links (-> (s/select
               (s/child
                (s/id :cphBody_dgMeetings) s/first-child) rb-index) :content first)]
    (vec
     (map
      #(hash-map :date (-> % :content second :content first :content first)
                 :url  (-> (nth (-> % :content) 5) :content first :content first :attrs :href))
      links))))

(retrieve-urls rb-index)

(s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index)

(-> {:type :element, :attrs {:bgcolor "#ECF2F7"}, :tag :tr, :content
["\r\n\t\t\t\t"
{:type :element, :attrs {:nowrap "nowrap"}, :tag :td, :content
[{:type :element, :attrs {:size "2"}, :tag :font, :content ["Thu 22 Nov 2018"]}]}]}
    :content second :content first :content first)


(def tr-sample
{:type :element, :attrs {:bgcolor "#ECF2F7"}, :tag :tr, :content
["\r\n\t\t\t\t"
{:type :element, :attrs {:nowrap "nowrap"}, :tag :td, :content
	[{:type :element, :attrs {:size "2"}, :tag :font, :content ["Thu 22 Nov 2018"]}]}
{:type :element, :attrs nil, :tag :td, :content
	[{:type :element, :attrs {:size "2"}, :tag :font, :content ["\r\n                    " {:type :element, :attrs {:href "https://www.phoenixrunning.co.uk/resultsDetail", :target "_blank"}, :tag :a, :content ["Phoenix Timelord on the Thames  6-Hour"]} "\r\n                    \r\n                "]}]}
{:type :element, :attrs nil, :tag :td, :content [{:type :element, :attrs {:size "2"}, :tag :font, :content ["Walton-on-Thames"]}]}
{:type :element, :attrs {:align "center"}, :tag :td, :content
	[{:type :element, :attrs {:size "2"}, :tag :font, :content ["Multi"]}]}
{:type :element, :attrs {:align "center"}, :tag :td, :content
	[{:type :element, :attrs {:size "2"}, :tag :font, :content [{:type :element, :attrs {:href "/results/results.aspx?meetingid=260933"}, :tag :a, :content ["Full"]}]}]}
{:type :element, :attrs nil, :tag :td, :content
	[{:type :element, :attrs {:size "2"}, :tag :font, :content [{:type :element, :attrs {:href "/submit/submitmeeting.aspx?meetingid=260933", :title "submit results"}, :tag :a, :content [{:type :element, :attrs {:src "/images/pot/email.gif", :border "0"}, :tag :img, :content nil}]}]}]}
"\r\n\t\t\t"]})

; date
(-> tr-sample :content second :content first :content first)
; link
(-> (nth (-> tr-sample :content) 5) :content first :content first :attrs :href)



(comment
1. Find :tr then get :content
2. Find :td then get :content
3a. First one is the date
3b. Second one can be ignored (link to original results and race name)
3c. Third one can be ignored (location)
3d. Fourth one can be ignored (type of race)
3e. Fifth has the link to the result if content within content is "Full" or "Complete"

:tr :content gives a seq with 8 elms.

)
