(defproject wrac-harvester "0.2.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.15.0"]
                 [hickory "0.7.1"]
                 [clj-http "3.9.1"]]
  :main ^:skip-aot wrac-harvester.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
