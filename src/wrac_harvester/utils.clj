(ns wrac-harvester.utils
  (:require [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string]))

(defn extract-date-from
  [date-string]
  (let [elms (reverse (string/split date-string #"\s"))]
    (fdt/parse (fdt/formatter "yyyyMMMdd") (str (nth elms 0) (nth elms 1) (nth elms 2)))))

;(extract-date-from "Mon 13 Tue 1 Nov 2018")

(defn filter-site-urls-for-date
  [site-urls date]
  (def comp-date (dt/plus date (dt/days -1)))
  (filter #(and
             (% :url)
             (dt/after? (:date %) comp-date))
          (site-urls)))

(defn get-wetherby-runners
  [runners]
  (filter #(re-find #"(wetherby)" (:club %)) runners))

(defn winners
  [runners]
  (filter #(= "1" (:pos %)) runners))

(defn single-race?
  [runners]
  (= 1 (count (winners runners))))

(defn first-male
  [runners]
  (first (filter #(= "M" (:sex %)) runners)))

(defn first-female
  [runners]
  (first (filter #(= "F" (:sex %)) runners)))

(defn create-runners-output
  [runners]
  (map #(str
         ","
         (:pos %)
         ","
         (:name %)
         ","
         (:sex %)
         (:cat %)
         ","
         (if (< (count (:gun %)) 6) "0:")
         (if (:chip %) (:chip %) (:gun %))
         "\n")
       runners))

(defn print-winner
  [runner]
  (str "("(:pos runner) ") " (:name runner) " - " (:club runner) " (" (if (:chip runner) (:chip runner) (:gun runner)) ")"))

