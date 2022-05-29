(ns dumch.dartclojure
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [dumch.improve :as improve]
            [dumch.parse :as parse]
            [instaparse.core :as insta])
  #_(:import (java.awt Toolkit)
             (java.awt.datatransfer Clipboard StringSelection))
  (:gen-class))

(def cli-options 
  [["-r" "--repl REPL" "Mode: repl or single command, single by default"
    :default false
    :parse-fn #(Boolean/parseBoolean %)
    :validate [boolean? "Must be either true or false"]]
   ["-e" "--end REPL" "Mode: text or symbol to show the end of code"
    :default ""
    :parse-fn str]
   ["-m" "--material MATERIAL" "material require alias, any string"
    :default "m"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %)
               "invalid require alias for material"]]
   ["-f" "--flutter FLUTTER_MACRO_PACKAGE" "flutter-macro require alias, any string"
    :default "f"
    :parse-fn str
    :validate [#(re-matches #"^[A-Za-z][A-Za-z0-9]*$" %) 
               "invalid require alias for flutter-materail"]]
   ["-c" "--clipboard BOOLEAN" "whether or not to put result in clipboard; not supported"
    :default true
    :parse-fn #(Boolean/parseBoolean %)
    :validate [boolean? "Must be either true or false"]]
   ["-h" "--help"]])

#_(defn- clipboard-put! [^String s clipboard]
    (.setContents clipboard (StringSelection. s) nil))

(defn- show! [data _]
  #_(defonce ^Clipboard clipboard (.. Toolkit getDefaultToolkit getSystemClipboard))
  (pprint data)
  #_(when put-in-clipboard? (clipboard-put! (str/join data) clipboard)))

(defn convert 
  "get dart code, material and flutter-macro aliases and returns clojure"
  ([code]
   (convert code "m" "f"))
  ([code material flutter]
   (let [ast (parse/dart->ast code) 
         bad? (insta/failure? ast)]
     (if bad?
       (str "Can't convert the code: " (:text ast))
       (-> 
         (parse/ast->clojure ast material)
         (improve/wrap-nest :flutter flutter)
         parse/save-read
         improve/wrap-nest
         improve/simplify)))))

(defn- stdin-loop! [material flutter put-in-clipboard? end]
  (println (str "Paste dart code below, press enter"
                (when (seq end) (str ", write '" end "',"))
                " and see the result:\n"))
  (loop [input (read-line)
         acc []]
    (if (= input end)
      (do 
        (try 
          (-> (convert (str/join "\n" acc) material flutter)
              (show! put-in-clipboard?))
          (catch Exception e (println "Can't convert the code; e " e)))
        (println)
        (recur (read-line) []))
      (recur (read-line) (conj acc input)))))

(defn -main [& args] 
  (let [{{:keys [repl end material flutter clipboard help]} :options, 
         errors :errors, 
         args :arguments :as m} 
        (parse-opts args cli-options)]
    (cond 
      help (println "here is the arguments\n" m)
      errors (println errors)
      repl (stdin-loop! material flutter clipboard end)
      :else (if args
              (show!
                (convert (first args) material flutter)
                clipboard)
              (println "no arguments passed")))))

(comment 
  (->
    "
    (context, index) {
      if (index == 0) {
        return const Padding(
          padding: EdgeInsets.only(left: 15, top: 16, bottom: 8),
          child: Text(
            'You might also like:',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w500,
            ),
          ),
        );
      }
      return const SongPlaceholderTile();
    };
    "
    (convert "m" "f")))
