(defproject dartclojure "0.2.23-SNAPSHOT"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [better-cond/better-cond "2.1.4"]
                 [com.github.clj-easy/graal-build-time "1.0.5"]
                 [instaparse/instaparse "1.4.12"]
                 [org.clojure/tools.cli "1.0.206"]
                 [rewrite-clj/rewrite-clj "1.1.47"]
                 [zprint/zprint "1.2.7"]]
  :main dumch.dartclojure
  :source-paths ["src"]
  :resource-paths ["resources"]
  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]
                                  [io.github.cognitect-labs/test-runner "0.5.0"]
                                  [org.clojure/clojurescript "1.11.54"]
                                  [thheller/shadow-cljs "2.19.3"]
                                  [olical/cljs-test-runner "3.8.0"]]
                   :plugins [[slipset/deps-deploy "0.2.1"]]
                   :main cognitect.test-runner.api/test}})
