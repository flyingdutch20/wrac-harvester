(ns wrac-harvester.core
  (:use [hickory.core])
  (:require
            [wrac-harvester.multiple :as all]
;            [wrac-harvester.runbritain :as rb]
;            [wrac-harvester.ukresults :as ukr]
    )
  (:gen-class))

(defn harvest-for-number-of-weeks
  [weeks]
  (all/output-wrac-rb-results-for-number-of-weeks weeks)
  (all/output-wrac-ukr-results-for-number-of-weeks weeks)
  (all/output-wrac-rbest-results-for-number-of-weeks weeks)
;  (rb/output-wrac-rb-results-for-number-of-weeks weeks)
;  (ukr/output-wrac-ukr-results-for-number-of-weeks weeks)
  )

(defn harvest-for-last-two-weeks
  []
  (harvest-for-number-of-weeks 2))

(defn harvest
  ([]
   (harvest-for-last-two-weeks))
  ([args]
   (let [weeks (try (biginteger (first args)) (catch Exception e 0))]
     (if (> weeks 0)
       (harvest-for-number-of-weeks weeks)
       (harvest-for-last-two-weeks)))))


;(biginteger "1")
;(try (biginteger ("a")) (catch Exception e 0))
;(harvest "4")

(defn show-usage
  "Show help about how to use the program"
  []
  (println "Harvest the Wetherby Runners race results from several websites and store in the folder C:/output")
  (println "Usage: java -jar wrac-harvester.jar harvest <weeks>\n")
  (println "Weeks is optional. If no number is provided then the last two weeks are harvested.")
  (println))

(defn -main [& args]
  (let [command (first args)]
    (if (= command "harvest")
      (harvest (rest args))
      (show-usage))))
;    (case command
;     "harvest" (harvest (rest args))
;     (show-usage))))

;(-main)
;(-main "harvest")
;(-main "harvest" "1")
