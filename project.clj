(defproject wrac-harvester "0.4.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-time "0.15.1"]
                 [hickory "0.7.1"]
                 [clj-http "3.9.1"]]
  :main ^:skip-aot wrac-harvester.core
  :source-paths ["src"]
  :target-path "target/%s"
  :repl-options {:port 8081}
  :profiles {:uberjar {:aot :all}})
