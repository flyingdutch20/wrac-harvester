(ns wrac-harvester.utils
  (:require [clj-time.core :as dt]
            [clj-time.format :as fdt]
            [clojure.string :as string]))

(defn extract-date-from
  [date-string]
  (let [elms (reverse (string/split date-string #"\s"))]
    (fdt/parse (fdt/formatter "yyyyMMMdd") (str (nth elms 0) (nth elms 1) (nth elms 2)))))


;(extract-date-from "Mon 13 Tue 1 Nov 2018")

