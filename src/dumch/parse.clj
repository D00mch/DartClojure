(ns dumch.parse
  (:require
    [clojure.java.io :as io]
    [better-cond.core :refer [defnc-]]
    [clojure.string :as str]
    [instaparse.core :as insta :refer [defparser]])
  (:import java.util.Base64))

(defparser widget-parser 
  (io/resource "widget-parser.bnf")
  :auto-whitespace :standard)

(defn- node->number [[tag value]]
  (if (= tag :number)
    (-> value (str/replace #"^\." "0.") read-string)
    (throw (ex-info "node with wrong tag passed" {:tag tag}))))

(defn- upper? [s] (some-> s first Character/isUpperCase))

(defn- str->with-import [s material]
  (if (upper? s) (str material "/" s) s))

(defn- to-identifier-name [s material]
  (let [with?? (re-matches #".*\?\..*" s)
        [v1 v2 :as parts] (str/split (str/replace s #"\?|\!" "") #"\.")
        threading #(str
                     "(" % " " 
                         (to-identifier-name (first parts) material) 
                         " ." (str/join " ."  (next parts)) ")")]

    (cond
      (= (count parts) 1) (str->with-import v1 material)
      with?? (threading "some->")

      (= (count parts) 2)
      (if (upper? v1)
        (str material "." v1 "/" v2)
        (str "(." v2 " " v1 ")"))

      :else (threading "->"))))

(defn- to-constructor-name
  "params: constructor name, params string, material require name"
  [n p m] ;; TODO: how to remove duplication with identifier-name ?
  (let [with?? (re-matches #".*\?\..*" n)
        [v1 v2 :as parts] (str/split (str/replace n #"\?|\!" "") #"\.")
        threading #(str
                     "(" % " "
                         (str->with-import v1 m) (when (> (count parts) 2) " .")
                         (str/join " ."  (butlast (next parts)))
                         " (." (last parts) " " p ")"
                         ")")]

    (cond 
      (= 1 (count parts)) (str "(" (str->with-import v1 m) " " p ")")
      with?? (threading "some->") 
      (= 2 (count parts)) (str "(." v2 " " (str->with-import v1 m) " " p ")")
      :else (threading "->"))))

(defn- str-insert
  "Insert c in string s at index i."
  [s c i]
  (str (subs s 0 i) c (subs s i)))

(defn- to-method-call [invocation]
  (if (re-matches #"^\(.*\)$" invocation)
    (str-insert invocation \. 1)
    (str "(." invocation ")")))

(defn- dart-op->clj-op [o]
  (case o
    "is" "dart/is?"
    "??" "or"
    "||" "or"
    "&&" "and"
    "==" "="
    "!=" "not="
    "%"  "rem"
    "~/" "quot"
    o))

(defn- substitute-curly-quotes [s]
  (str/replace s #"\"|'" "”"))

(defnc- ast->clojure [[tag v1 v2 v3 :as node] m]

  (= v1 "const") (ast->clojure (remove #(= % "const" ) node) m)
  ;; read-string ignores ^:const
  ;(str "^:const " (ast->clojure (remove #(= % "const" ) node) m))

  (string? node) (to-identifier-name node m)
  (= :string tag) (str/replace v1 #"^.|.$" "\"")
  (= :named-arg tag) (str ":" v1)
  (= :number tag) (node->number node) 

  ;; the only reason for it being quoted list and not vector is the problem
  ;; with using zipper (improve) on a collectoin with both seq and vector 
  (= :list tag) 
  (str "'(" (str/join " " (map #(ast->clojure % m) (rest node))) ")")

  (= :map tag)
  (str "{" (str/join " " (map #(ast->clojure % m) (rest node))) "}" )

  (= :get tag)
  (str "(get " (ast->clojure v1 m) " " (ast->clojure v2 m) ")")

  ;; block below 
  (and (= :invocation tag) v2)
  (str "(-> " (ast->clojure v1 m) 
            (->> node
                 (drop 2)
                 (map #(ast->clojure % m))
                 (map to-method-call)
                 (str/join " "))
            ")")

  (and (= :s tag) v2) 
  (str "(do " (->> node rest (map #(ast->clojure % m)) (str/join " ")) ")") 
  (#{:s :return :typed-value :invocation :priority} tag)
  (ast->clojure v1 m)

  (= :constructor tag)
  (str (to-constructor-name v1 (ast->clojure v2 m) m))

  (= :if tag)
  (case (count node)
    3 (str "(when " (ast->clojure v1 m) " " (ast->clojure v2 m) ")")
    4 (str "(if " (->> (rest node) (map #(ast->clojure % m)) (str/join " ")) ")") 
    (str "(cond " (->> (rest node) (map #(ast->clojure % m)) (str/join " ")) ")"
         (when (even? (count node)) (str " " (ast->clojure (last node) m)))) )

  (= :lambda-args tag) (str "[" (str/join " " (rest node)) "]")
  (= :lambda-body tag) (str/join " " (map #(ast->clojure % m) (rest node)))
  (= :params tag) (str/join " " (map #(ast->clojure % m) (rest node)))
  (= :argument tag)
  (if v2 
    (str/join " " (map #(ast->clojure % m) (rest node)))
    (ast->clojure v1 m))
  (= :lambda tag) 
  (str "(fn " (str/join " " (map #(ast->clojure % m) (rest node))) ")")


  (= :assignment tag)
  (str "(set! " (ast->clojure v1 m) " " (ast->clojure v2 m) ")")

  (= :ternary tag)
  (str "(if " (ast->clojure v1 m) " " 
          (ast->clojure v2 m) " "
          (ast->clojure v3 m) ")")

  (= :neg tag) (str "(- " (ast->clojure v1 m) ")")
  (= :await tag) (str "(await " (ast->clojure v1 m) ")")
  (#{:not :dec :inc} tag) (str "(" (name tag) " " (ast->clojure v1 m) ")") 

  (and (= tag :compare) (= v2 "as")) (ast->clojure v1 m)
  (#{:compare :add :mul :and :or :ifnull :equality} tag) 
  (str "(" (dart-op->clj-op v2) " " (ast->clojure v1 m) " " (ast->clojure v3 m) ")")

  (and (keyword? tag) (-> tag str second (= \_))) :unidiomatic

  :unknown)

(defn- encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn- decode [to-decode]
  (String. (.decode (Base64/getDecoder) to-decode)))

(defn- multiline->single [s]
  (let [pattern #"'{3}([\s\S]*?'{3})|\"{3}([\s\S]*?\"{3})"
        transform (fn [[m]] (substitute-curly-quotes m))]
    (-> s
        (str/replace pattern transform)
        (str/replace #"”””" "'"))))

(defn clean
  "removes comments from string, transforms multiline string
   to singleline, replaces quotes (', \") with 'curly-quotes' (“)"
  [code]
  (let [str-pattern #"([\"\'])(?:(?=(\\?))\2.)*?\1"
        transform #(fn [[m]]
                     (str "'" (% (.substring m 1 (dec (count m)))) "'"))]
    (-> code 

        ;; to simplify modifications below
        multiline->single

        ;; TODO: find another wat to deal with comments
        ;; the problem is that comments could appear inside strings
        (str/replace str-pattern (transform encode))    ; encode strings to Base64

        ;; cleaning code from comments
        (str/replace #"\/\*(\*(?!\/)|[^*])*\*\/" "")    ; /* ... */ 
        (str/replace #"(\/\/).*" "")                    ; // ... 
        (str/replace str-pattern 
                     (transform (comp substitute-curly-quotes 
                                      decode)))))) 
      
(defn- save-read [code]
  (if (string? code) 
    (try 
      (read-string code)
      (catch Exception e
        code)) 
    code))

(defn dart->clojure [dart & {m :material :or {m "m"}}]
  (-> dart clean widget-parser (ast->clojure m) save-read))

(comment 
  
  (def code
    "
var item = catalog.getByIndex(index);
if (item.isLoading) {
  print(1);
}
a 
")


  (defparser widget-parser 
    (io/resource "widget-parser.bnf")
    :auto-whitespace :standard)

  (insta/parse widget-parser (clean code) :total 1)

  (dart->clojure code)

  (->> code
      clean 
      (insta/parses widget-parser :total true) 
      ; (ast->clojure "m") 
      ; save-read
      ))
