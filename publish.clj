(require '[clojure.string :as str]
         '[clojure.tools.cli :refer [parse-opts]]
         '[clojure.java.shell :refer [sh]])

(def version-files ["README.md" "pom.xml" "package.json"])

(defn cmd! [cmd] (sh  "sh" "-c" cmd))
(defn cmd-prn! [cmd] (println (cmd! cmd)))

(defn set-version [s v]
  (-> s
      (str/replace  #"\d+.\d+.\d+-SNAPSHOT" (str v "-SNAPSHOT"))
      (str/replace #"tag/\d+.\d+.\d+" (str "tag/" v))
      (str/replace #"\"version\": \"\d+.\d+.\d+\"" (str "\"version\": \"" v \"))
      (str/replace #"\"dartclojure\": \"\^\d+.\d+.\d+\"" (str "\"dartclojure\": \"^" v \"))))

(defn update-versions- [v]
  (println "about to update versions")
  (letfn [(update-file! [fname]
            (spit fname
                  (set-version (slurp fname) v)))]
    (doseq [f version-files] (update-file! f)))
  (println "versions updated"))

(defn build-jar- []
  (println "about to build jar")
  (cmd-prn! "clj -X:depstar"))

(defn publish-clojars- [psw]
  (println "about to publish to clojars")
  (cmd-prn! 
    (str
      "env CLOJARS_USERNAME=liverm0r"
      " CLOJARS_PASSWORD=" psw
      " clj -X:deploy"))
  (println "published to clojars"))

(defn rebuild-and-publish-npm- []
  (println "about to clean")
  (cmd-prn! "npm run clean")
  (println "clean finished; about to build release")
  (cmd-prn! "clj -M:shadow-cljs release :app :lib")
  (cmd-prn! "npm publish")
  (println "published"))

(defn open-docker- [] 
  (cmd-prn! "open -a Docker")
  (while (-> (cmd! "docker stats --no-stream") :out empty?)
    (Thread/sleep 1500))
  (Thread/sleep 1000))

(defn build-aar64-linux- []
  (open-docker-)
  (cmd-prn! "docker image rm -f dartclojure")
  (cmd-prn! "docker rm -f dc")

  (cmd-prn! "docker build --pull --no-cache --tag dartclojure .")
  (println "test command")
  (cmd-prn! "docker run --name dc -i dartclojure ./dartclj \"1+1;\"")
  (cmd-prn! "docker cp dc:/usr/src/app/dartclj \"dartclj-aarch64-linux\""))

(defn build-aaar64-darwin- []
  (cmd! "chmod +x compile.sh")
  (cmd-prn! "./compile.sh")
  (cmd-prn! "mv dartclj dartclj-aarch64-darwin"))

(defn build-native-images- []
  (build-aaar64-darwin-)
  (build-aar64-linux-))

(def cli-options
  [["-v" "--version VERSION" "New release version"
    :default nil
    :parse-fn str]
   ["-p" "--publish PUBLISH" "Pass clojars password"
    :default nil
    :parse-fn str]
   ["-n" "--native NATIVE" "Build native flag"
    :default false
    :parse-fn #(Boolean/parseBoolean %)]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [version publish native] :as options}
        (:options (parse-opts args cli-options))]
    (println "started with" options)
    (when (or publish native) (build-jar-))
    (when version (update-versions- version))
    (when publish
      (publish-clojars- publish)
      (rebuild-and-publish-npm-))
    (when native (build-native-images-))))

(println "started with" :args *command-line-args*)

(apply -main *command-line-args*)
