(ns wrac-harvester.core
  (:use [hickory.core])
  (:require
            [wrac-harvester.runbritain :as rb]
            [wrac-harvester.ukresults :as ukr])
  (:gen-class))


(defn harvest
  ([]
   (rb/output-wrac-rb-results-for-last-two-weeks))
  ([arg]
   (rb/output-wrac-rb-results-for-date-string arg)))

;(harvest "10 Dec 2018")

(defn show-usage
  "Show help about how to use the program"
  []
  (println "Harvest the Wetherby Runners race results from the Run Britain website and store in the folder C:/output")
  (println "Usage: java -jar wrac-harvester.jar harvest <dd-mm-yyyy>\n")
  (println "Date is optional. If no date is provided then the last two weeks are harvested.")
  (println "If <all> (or wrong date) is provided then all races from the index page are harvested.")
  (println))

(defn -main [& args]
  (let [command (first args)]
    (if (= command "harvest")
      (harvest (rest args))
      (show-usage))))
;    (case command
;     "harvest" (harvest (rest args))
;     (show-usage))))

