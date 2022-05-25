(ns dumch.dartclojure
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.cli :refer [parse-opts]]
            [dumch.improve :refer [wrap-nest]]
            [dumch.parse :refer [dart->clojure]]
            [clojure.string :as str])
  (:gen-class))

(def cli-options 
  [["-r" "--repl REPL" "Mode: repl or single command, single by default"
    :default false
    :parse-fn #(Boolean/parseBoolean %)
    :validate [boolean? "Must be either true or false"]]
   ["-m" "--material MATERIAL" "material require alias, any string"
    :default "m"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %)
               "invalid require alias for material"] 
    ]
   ["-f" "--flutter FLUTTER_MACRO_PACKAGE" "flutter-macro require alias, any string"
    :default "f"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %) 
               "invalid require alias for flutter-materail"]]
   ["-h" "--help"]])

(defn- stdin-loop [material flutter]
  (loop [input (read-line)
         acc []]
    (if (nil? (seq input))
      (do (try 
            (-> (str/join " " acc)
                (dart->clojure :material material)
                (wrap-nest :flutter flutter)
                pprint)
            (catch Exception e (println "Can't convert the code; e " e)))
          (recur (read-line) []))

      (recur (read-line) (conj acc input)))))

(defn -main [& args] 
  (let [{{:keys [repl material flutter help]} :options, 
         errors :errors, 
         args :arguments :as m} 
        (parse-opts args cli-options)]
    (cond 
      help (println "here is the arguments\n" m)
      errors (println errors)
      repl (stdin-loop material flutter)
      :else (if args
              (-> args 
                  first
                  (dart->clojure :material material)
                  (wrap-nest :flutter flutter)
                  pprint)
              (println "no arguments passed")))))

(comment 
  (->>
    "
    Column(
     children: [
       const Text('name'),
       Icon(Icons.widgets),
    ])
    "
    dart->clojure
    wrap-nest
    pprint)
 )
