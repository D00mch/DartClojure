(ns converter.core
  (:require ["dartclojure" :as dartclojure]))

(println "Hello")

(defn ^:export convert []
  (try
    (let [dart-code (.-value (js/document.getElementById "dart"))
          clj-code (dartclojure/convert dart-code)]
      (set! (.-innerHTML (js/document.getElementById "clojure")) clj-code))
    (catch :default e
      (js/alert e))))

(defn main! []
  (println "Hello from main"))

(defn ^:dev/after-load reload! []
  (println "shadow-cljs reloaded"))