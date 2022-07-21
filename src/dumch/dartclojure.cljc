(ns dumch.dartclojure
  (:require #?(:cljs ["readline" :as node-rl])
            #?(:cljs [clojure.edn :refer [read-string]])
            #?(:cljs ["fs" :as node-fs])
            [clojure.pprint :refer [pprint]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [dumch.convert :refer [convert]]
            [zprint.core :as zpr])
  #?(:clj (:gen-class)))

#?(:clj (set! *warn-on-reflection* true))

(def cli-options
  [["-r" "--repl REPL" "Mode: repl or single command, single by default"
    :default false
    :parse-fn #?(:clj #(Boolean/parseBoolean %)
                 :cljs read-string)
    :validate [boolean? "Must be either true or false"]]
   ["-e" "--end TEXT" "Mode: text or symbol to show the end of code"
    :default ""
    :parse-fn str]
   ["-m" "--material MATERIAL" "material require alias, any string"
    :default "m"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %)
               "invalid require-alias for material"]]
   ["-f" "--flutter FLUTTER_MACRO_PACKAGE" "flutter-macro require alias, any string"
    :default "f"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %)
               "invalid require alias for flutter-materail"]]
   ["-c" "--colors BOOLEAN" "colorize output"
    :default false
    :parse-fn #?(:clj #(Boolean/parseBoolean %)
                 :cljs read-string)
    :validate [boolean? "Must be either true or false"]]
   ["-p" "--path PATH TO FILE, OR URL" "url or path to dart file to translate"
    :parse-fn str]
   ["-h" "--help"]])

(defn show! [data & {:keys [colors] :or {colors false}}]
  (if colors
    (zpr/czprint data {:parse-string? true})
    (println data)))

(defn- print-repl-instructions [end]
  (println (str "Paste dart code below, press enter"
                (when (seq end) (str ", write '" end "',"))
                " and see the result:\n")))

#?(:clj
   (defn- stdin-loop! [& {:keys [material flutter end colors]}]
     (print-repl-instructions end)
     (loop [input (read-line)
            acc []]
       (if (= input end)
         (do
           (try
             (-> (convert (str/join "\n" acc)
                          :material material
                          :flutter flutter)
                 (show! :colors colors))
             (catch Exception e (println "Can't convert the code; e " e)))
           (println)
           (recur (read-line) []))
         (recur (read-line) (conj acc input))))))

#?(:cljs
   ;; This could have been a lambda, but then we can't update it via the REPL
   (defn- process-node-repl-input! [input !code {:keys [material flutter end colors]}]
     (if (= input end)
       (do
         (try
           (-> (convert (str/join "\n" @!code)
                        :material material
                        :flutter flutter)
               (show! :colors colors))
           (catch js/Error e (println "Can't convert the code; e " e)))
         (println)
         (reset! !code []))
       (swap! !code conj input))))

#?(:cljs
   (defn- node-repl! [& {:keys [end] :as params}]
     (print-repl-instructions end)
     (let [!code (atom [])]
       (-> (.createInterface node-rl #js {:input js/process.stdin
                                          :output js/process.stdout
                                          :terminal false})
           (.on "line" (fn [input]
                         (process-node-repl-input! input !code params)))))))

(defn -main [& args]
  (let [{{:keys [repl help path] :as params} :options,
         errors :errors,
         args :arguments :as m}
        (parse-opts args cli-options)
        params (mapcat identity params)]
    (cond
      help (println "here is the arguments\n" m)
      errors (println errors)
      repl #?(:clj (apply stdin-loop! params)
              :cljs (apply node-repl! params))
      path (when-let [code (#?(:clj slurp
                               :cljs #(node-fs/readFileSync % #js {:encoding "utf-8" :flag "r"})) path)]
             (apply show!
                    (apply convert code params)
                    params))
      :else (if args
              (apply show!
                     (apply convert (first args) params)
                     params)
              (println "no arguments passed")))))

#?(:cljs
   (def node-main #'-main))

#?(:cljs
   (defn shadow-cljs-reload []
     (println "shadow-cljs reloaded the code")))
