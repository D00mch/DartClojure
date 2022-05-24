(ns dumch.dartclojure
  (:require [clojure.pprint :refer [pprint]]
            [dumch.improve :refer [wrap-nest]]
            [dumch.parse :refer [dart->clojure]])
  (:gen-class))

(defn -main [code]
  (->> code dart->clojure wrap-nest pprint))
