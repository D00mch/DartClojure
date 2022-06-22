(require '[clojure.string :as str]
         '[clojure.tools.cli :refer [parse-opts]]
         '[clojure.java.shell :refer [sh]])

(def version-files ["README.md" "pom.xml" "package.json"])

(defn cmd! [cmd] (sh  "sh" "-c" cmd))

(defn set-version [s v]
  (-> s
      (str/replace  #"\d+.\d+.\d+-SNAPSHOT" (str v "-SNAPSHOT"))
      (str/replace #"tag/\d+.\d+.\d+" (str "tag/" v))
      (str/replace #"\"version\": \"\d+.\d+.\d+\"" (str "\"version\": \"" v \"))
      (str/replace #"\"dartclojure\": \"\^\d+.\d+.\d+\"" (str "\"dartclojure\": \"^" v \"))))

(defn update-versions! [v]
  (println "about to update versions")
  (letfn [(update-file! [fname]
            (spit fname
                  (set-version (slurp fname) v)))]
    (doseq [f version-files] (update-file! f)))
  (println "versions updated"))

(def cli-options
  [["-v" "--version VERSION" "New release version"
    :default nil
    :parse-fn str]
   ["-p" "--publish PUBLISH" "Pass clojars password"
    :default nil
    :parse-fn str]
   ["-h" "--help"]])

(defn publish-clojars [psw]
  (println "about to publish to clojars")
  (cmd! 
    (str
      "env CLOJARS_USERNAME=liverm0r"
      " CLOJARS_PASSWORD=" psw
      " clj -X:deploy"))
  (println "published to clojars"))

(defn rebuild-and-publish-npm []
  (println "about to clean")
  (cmd! "npm run clean")
  (println "clean finished; about to build release")
  (println :built (cmd! "clj -M:shadow-cljs release :app :lib"))
  (println (cmd! "npm publish"))
  (println "published"))

(defn -main [& args]
  (let [{:keys [version publish]}
        (:options (parse-opts args cli-options))]
    (println "started with" :version version :publish publish)
    (when version (update-versions! version))
    (when publish
      (publish-clojars publish)
      (rebuild-and-publish-npm))))

(println "started with" :args *command-line-args*)

(apply -main *command-line-args*)
