(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.java.shell :refer [sh]]))

(def target "target")
(def class-dir (str target "/classes"))
(def basis (b/create-basis {:project "deps.edn"}))

(defn cmd! [cmd] (sh  "sh" "-c" cmd))
(defn cmd-prn! [cmd] (println (cmd! cmd)))

;; # Versions

(def version-files ["README.md" "pom.xml" "package.json"])

(defn set-version [s v]
  (-> s
      (str/replace  #"\d+.\d+.\d+-SNAPSHOT" (str v "-SNAPSHOT"))
      (str/replace #"tag/\d+.\d+.\d+" (str "tag/" v))
      (str/replace #"\"version\": \"\d+.\d+.\d+\"" (str "\"version\": \"" v \"))
      (str/replace #"\"dartclojure\": \"\^\d+.\d+.\d+\"" (str "\"dartclojure\": \"^" v \"))))

(defn update-versions [v]
  (println "about to update versions")
  (letfn [(update-file! [fname]
            (spit fname
                  (set-version (slurp fname) v)))]
    (doseq [f version-files] (update-file! f)))
  (println "versions updated"))

;; # Uberjar

(defn publish-clojars [psw]
  (println "about to publish to clojars")
  (cmd! "cp target/dartclj*.jar dartclj.jar")
  (cmd-prn! 
    (str
      "env CLOJARS_USERNAME=liverm0r"
      " CLOJARS_PASSWORD=" psw
      " clj -X:deploy"))
  (println "published to clojars"))

(defn uber-file [name version]
  (format "target/%s-%s-standalone.jar" name version))

(defn clean [_]
  (b/delete {:path target}))

(defn uber [{:keys [version] :as params}]
  (println "getting params: " params)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (uber-file "dartclj" version)
           :basis basis
           :main  'dumch.dartclojure}))

;; # Native

(defn open-docker [] 
  (cmd-prn! "open -a Docker")
  (while (-> (cmd! "docker stats --no-stream") :out empty?)
    (Thread/sleep 1500))
  (Thread/sleep 5000))

(defn build-aar64-linux [version]
  (open-docker)
  (cmd-prn! "docker image rm -f dartclojure")
  (cmd-prn! "docker rm -f dc")

  (cmd-prn! "docker build --pull --no-cache --tag dartclojure .")
  (println "test command")
  (cmd-prn! "docker run --name dc -i dartclojure ./dartclj \"1+1;\"")
  (cmd-prn!
    (str "docker cp dc:/usr/src/app/dartclj \"target/dartclj-" 
         version
         "-aarch64-linux\"")))

(defn build-aaar64-darwin [version]
  (cmd! "pwd")
  (cmd! "cp target/dartclj*.jar dartclj.jar")
  (cmd! "chmod +x compile.sh")
  (cmd-prn! "./compile.sh")
  (cmd-prn! (str "mv dartclj target/dartclj-" version "-aarch64-darwin")))

(defn native [{:keys [version]}]
  (build-aaar64-darwin version)
  (build-aar64-linux version))

;; # NPM

(defn rebuild-and-publish-npm []
  (println "about to clean")
  (cmd! "rm -rf .shadow-cljs")
  (cmd-prn! "npm run clean")
  (println "clean finished; about to build release")
  (cmd-prn! "clj -M:shadow-cljs release :app :lib")
  (cmd-prn! "npm publish")
  (println "published"))

(defn npm [_]
  (rebuild-and-publish-npm))

;; # All

(defn release [{:keys [version clojarspass] :as params}]
  (clean nil)
  (update-versions version)
  (uber params)
  (publish-clojars clojarspass)
  (native params)
  (rebuild-and-publish-npm))

(comment
  (require '[clojure.tools.deps.alpha.repl :refer [add-libs]])
  (add-libs '{io.github.clojure/tools.build 
              {:git/tag "v0.8.3" :git/sha "0d20256"}})

  (def version "0.2.13")
  (update-versions version)
  (uber {:version version})
  (native {:version version})
  ,)
