(ns dumch.dartclojure
  (:require [clojure.pprint :refer [pprint]]
            [dumch.parse :refer [dart->clojure]])
  (:gen-class))

(defn -main [code]
  (->> code dart->clojure pprint))
