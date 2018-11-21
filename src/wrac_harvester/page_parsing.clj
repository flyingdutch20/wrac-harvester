(ns wrac-harvester.page-parsing
  (:use [hickory.core])
  (:require [hickory.select :as s]
            [clj-http :as client]
            [clojure.string :as string]))


;(def rb-index (-> (client/get "https://www.runbritainrankings.com/results/resultslookup.aspx") :body parse as-hickory))

(def rb-index (-> (java.net.URL. "https://www.runbritainrankings.com/results/resultslookup.aspx") :body parse as-hickory))

(-> (s/select (s/child (s/id :cphBody_dgMeetings) s/first-child) rb-index))
