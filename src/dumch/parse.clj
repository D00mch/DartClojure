(ns dumch.parse
  (:require
    [clojure.java.io :as io]
    [better-cond.core :refer [defnc]]
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
  (str/replace
   (if (upper? s) (str material "/" s) s)
   #"!" ""))

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

(defn- flatten-dot [node ast->clj]
  (loop [[tag v1 v2 :as node] node ;; having structure like [dot [dot a b] c]
         stack [] ;; put here 'c', then 'b', while trying to find 'a' ^
         [a [_ n params :as b] :as rslt] []  ;; result will look like [a b c]
         op? false]
    (cond (= :dot tag) (recur v1 (conj stack v2) rslt op?)
          (= :dot-op tag) (recur v1 (conj stack v2) rslt true)
          node (recur nil (conj stack node) rslt op?)
          (seq stack) (recur node (pop stack) (conj rslt (peek stack)) op?)
          ;; return results
          op? (str "(some-> " (str/join " " (map ast->clj rslt)) ")")
          (= (count rslt) 2) (ast->clj [:invoke n (cons :params (cons a (next params)))])
          :else (str "(-> " (str/join " " (map ast->clj rslt)) ")"))))

(defnc ast->clj [[tag v1 v2 v3 :as node] m]
  (= :identifier tag) (str->with-import v1 m)
  (= :string tag) (str/replace v1 #"^.|.$" "\"")
  (= :named-arg tag) (str ":" (ast->clj v1 m))
  (= :number tag) (node->number node) 

  ;; the only reason for it being quoted list and not vector is the problem
  ;; with using zipper (improve) on a collectoin with both seq and vector 
  (= :list tag) 
  (str "'(" (str/join " " (map #(ast->clj % m) (rest node))) ")")

  (= :map tag)
  (str "{" (str/join " " (map #(ast->clj % m) (rest node))) "}" )

  (= :get tag)
  (str "(get " (ast->clj v1 m) " " (ast->clj v2 m) ")")

  (and (= :s tag) v2) 
  (str "(do " (->> node rest (map #(ast->clj % m)) (str/join " ")) ")") 
  (#{:s :return :typed-value :priority :const} tag)
  (ast->clj v1 m)

  (= :constructor tag)
  (str "(" (ast->clj v1 m) " " (ast->clj v2 m) ")")

  (= :if tag)
  (case (count node)
    3 (str "(when " (ast->clj v1 m) " " (ast->clj v2 m) ")")
    4 (str "(if " (->> (rest node) (map #(ast->clj % m)) (str/join " ")) ")") 
    (str "(cond " (->> (rest node) (map #(ast->clj % m)) (str/join " ")) ")"
         (when (even? (count node)) (str " " (ast->clj (last node) m)))) )

  (= :lambda-args tag) 
  (str "[" (str/join " " (map #(ast->clj % m) (rest node))) "]")
  (= :lambda-body tag) (str/join " " (map #(ast->clj % m) (rest node)))
  (= :params tag) (str/join " " (map #(ast->clj % m) (rest node)))
  (= :argument tag)
  (if v2 
    (str/join " " (map #(ast->clj % m) (rest node)))
    (ast->clj v1 m))
  (= :lambda tag) 
  (str "(fn " (str/join " " (map #(ast->clj % m) (rest node))) ")")

  (= :assignment tag)
  (str "(set! " (ast->clj v1 m) " " (ast->clj v2 m) ")")

  (= :ternary tag)
  (str "(if " (ast->clj v1 m) " " 
          (ast->clj v2 m) " "
          (ast->clj v3 m) ")")

  (= :neg tag) (str "(- " (ast->clj v1 m) ")")
  (= :await tag) (str "(await " (ast->clj v1 m) ")")
  (#{:not :dec :inc} tag) (str "(" (name tag) " " (ast->clj v1 m) ")") 

  (and (= tag :compare) (= v2 "as")) (ast->clj v1 m)
  (#{:compare :add :mul :and :or :ifnull :equality} tag) 
  (str "(" (dart-op->clj-op v2) " " (ast->clj v1 m) " " (ast->clj v3 m) ")")

  (or (= :dot tag) (= :dot-op tag)) (flatten-dot node #(ast->clj % m)) 
  (= :invoke tag) (str "(." (ast->clj v1 m) " " (ast->clj v2 m) ")")
  (= :field tag) (str "." (ast->clj v1 m))

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
      
(defn save-read [code]
  (if (string? code) 
    #_{:clj-kondo/ignore [:unused-binding]}
    (try 
      (read-string code)
      (catch Exception e code)) 
    code))

(defn dart->ast [dart]
  (insta/parse widget-parser (clean dart)))

(defn dart->clojure [dart & {m :material :or {m "m"}}]
  (-> dart dart->ast (ast->clj m) save-read))

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

  (insta/parses widget-parser (clean code) :total 1)

  (dart->clojure code)

  (->> code
      clean 
      (insta/parses widget-parser :total true) 
      ; (ast->clojure "m") 
      ; save-read
      ))
