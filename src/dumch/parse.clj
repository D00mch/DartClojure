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

(defn- identifier-name [s material]
  (let [with?? (re-matches #".*\?\..*" s)
        [v1 v2 :as parts] (str/split (str/replace s #"\?|\!" "") #"\.")
        threading #(str
                     "(" % " " 
                         (identifier-name (first parts) material) 
                         " ." (str/join " ."  (next parts)) ")")]

    (cond
      (= (count parts) 1) (str->with-import v1 material)
      with?? (threading "some->")

      (= (count parts) 2)
      (if (upper? v1)
        (str material "." v1 "/" v2)
        (str "(." v2 " " v1 ")"))

      :else (threading "->"))))

(defn- constructor-name
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

(defn- operator-name [o]
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

(defnc- lsp->clojure [[tag v1 v2 v3 :as node] m]

  (= v1 "const") 
  (str "^:private " (lsp->clojure (remove #(= % "const" ) node) m))

  (= :s tag) (lsp->clojure v1 m)
  (= :return tag) (lsp->clojure v1 m) 

  (string? node) (identifier-name node m)
  (= :string tag) (str/replace v1 #"\"|'" "\"")
  (= :named-arg tag) (str ":" v1)
  (= :number tag) (node->number node) 

  ;; the only reason for it being quoted list and not vector is the problem
  ;; with using zipper (improve) on a collectoin with both seq and vector 
  (= :list tag) 
  (str "'(" (str/join " " (map #(lsp->clojure % m) (rest node))) ")")

  (= :map tag)
  (str "{" (str/join " " (map #(lsp->clojure % m) (rest node))) "}" )

  (= :get tag)
  (str "(get " (lsp->clojure v1 m) " " (lsp->clojure v2 m) ")")

  (and (= :invocation tag) v2)
  (str "(-> " (lsp->clojure v1 m) 
            (->> node
                 (drop 2)
                 (map #(lsp->clojure % m))
                 (map #(str-insert % \. 1))
                 (str/join " "))
            ")")

  (= :invocation tag) (lsp->clojure v1 m)

  (= :constructor tag)
  (str (constructor-name v1 (lsp->clojure v2 m) m))

  (= :params tag) (str/join " " (map #(lsp->clojure % m) (rest node)))
  (and (= :argument tag) v2) (str/join " " (map #(lsp->clojure % m) (rest node)))
  (= :argument tag) (lsp->clojure v1 m)  

  (and (= :if tag) (= (count node) 3))
  (str "(when " (lsp->clojure v1 m) " " (lsp->clojure v2 m) ")")

  (and (= :if tag) (= (count node) 4))
  (str "(if " (->> (rest node) (map #(lsp->clojure % m)) (str/join " ")) ")")

  (= :if tag) 
  (str "(cond " (->> (rest node) (map #(lsp->clojure % m)) (str/join " ")) ")"
       (when (even? (count node)) (str " " (lsp->clojure (last node) m))))

  (= :lambda-args tag) (str "[" (str/join " " (rest node)) "]")
  (= :lambda-body tag) (str/join " " (map #(lsp->clojure % m) (rest node)))
  (= :lambda tag) 
  (str "(fn " (str/join " " (map #(lsp->clojure % m) (rest node))) ")")

  (= :assignment tag)
  (str "(set! " (lsp->clojure v1 m) " " (lsp->clojure v2 m) ")")

  (= :priority tag) (lsp->clojure v1 m)

  (= :ternary tag)
  (str "(if " (lsp->clojure v1 m) " " 
          (lsp->clojure v2 m) " "
          (lsp->clojure v3 m) ")")

  (= :neg tag) (str "(- " (lsp->clojure v1 m) ")")
  (#{:not :dec :inc} tag) (str "(" (name tag) " " (lsp->clojure v1 m) ")") 

  (and (= tag :compare) (= v2 "as")) (lsp->clojure v1 m)
  (#{:compare :add :mul :and :or :ifnull :equality} tag) 
  (str "(" (operator-name v2) " " (lsp->clojure v1 m) " " (lsp->clojure v3 m) ")")

  (and (keyword? tag) (-> tag str second (= \_))) :unidiomatic

  :unknown)

(defn- encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn- decode [to-decode]
  (String. (.decode (Base64/getDecoder) to-decode)))

(defn- clean [code]
  (let [str-reg #"([\"\'])(?:(?=(\\?))\2.)*?\1"
        transform #(fn [[m]]
                     (str "'" (% (.substring m 1 (dec (count m)))) "'"))]
    (-> code ;; TODO: find another wat to deal with comments
        (str/replace str-reg (transform encode))       ; encode strings to Base64
        (str/replace #"\/\*(\*(?!\/)|[^*])*\*\/" "")   ; /* comment block */ 
        (str/replace #"(\/\/)(.+?)(?=[\n\r]|\*\))" "") ; //one-line-comment 
        (str/replace str-reg (transform decode)))))    ; decode strings from Base64
      
(defn- save-read [code]
  (if (string? code) (read-string code) code))

(defn dart->clojure [dart & {m :material :or {m "m"}}]
  (-> dart clean widget-parser (lsp->clojure m) save-read))

(comment 
  
  (def code
    "
Column(
  children: [
    Question(
      questions[_questionIndex]['questionText'],
    ),
    (questions[_questionIndex]['answers'] as List<String>)
        .map((answer) {
      return Answer(_answerQuestion, answer);
    }).toList()
  ],
)
  ")

  (def code "
ListView(
   scrollDirection: Axis.vertical,
   children: [...state.a.map((acc) => _t(ctx, acc))],
)"
  )

  (defparser widget-parser 
    (io/resource "widget-parser.bnf")
    :auto-whitespace :standard)

  (insta/parse widget-parser code)

  (dart->clojure code)
)
