(ns dumch.parse
  (:require
    [clojure.java.io :as io]
    [better-cond.core :refer [defnc-]]
    [clojure.string :as str]
    [instaparse.core :as insta :refer [defparser]]))

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
  (let [[v1 v2 :as parts] (str/split s #"\.")]

    (case (count parts)
      1 (str->with-import v1 material)

      2 (if (upper? v1)
          (str material "." v1 "/" v2)
          (str "(." v2 " " v1 ")"))

      (str 
        "(-> " 
             (identifier-name (first parts) material) " ."
             (str/join " ."  (next parts)) ")"))))

(defn constructor-name 
  "params: constructor name, params string, material require name"
  [n p m]

  (let [[v1 v2 :as parts] (str/split n #"\.")]

    (case (count parts)
      1 (str "(" (str->with-import v1 m) " " p ")")

      2 (str "(." v2 " " (str->with-import v1 m) " " p ")")

      (str 
        "(-> " 
             (str->with-import v1 m) " ."
             (str/join " ."  (butlast (next parts)))
             " (." (last parts) " " p ")"
             ")"))))

(defn str-insert
  "Insert c in string s at index i."
  [s c i]
  (str (subs s 0 i) c (subs s i)))

(defnc- lsp->clojure [[tag v1 v2 v3 _ v5 :as node] 
                      & {:keys [m] :or {m "m"}}]

  (= v1 "const") 
  (str "^:private " (lsp->clojure (remove #(= % "const" ) node)))

  (= :s tag) (lsp->clojure v1)

  (string? node) (identifier-name node m)
  (= :string tag) (str/replace v1 #"\"|'" "\"")
  (= :named-arg tag) (str ":" v1)
  (= :number tag) (node->number node) 

  (and (= :invocation tag) v2)
  (str "(-> " (lsp->clojure v1) 
            (->> node
                 (drop 2)
                 (map lsp->clojure)
                 (map #(str-insert % \. 1))
                 (str/join " "))
            ")")

  (= :invocation tag) (lsp->clojure v1)

  (= :constructor tag)
  (str (constructor-name v1 (lsp->clojure v2) m))

  (= :params tag) (str/join " " (map lsp->clojure (rest node)))
  (and (= :argument tag) v2) (str/join " " (map lsp->clojure (rest node)))
  (= :argument tag) (lsp->clojure v1)  
  (= :list tag) (str "[" (str/join " " (map lsp->clojure (rest node))) "]")

  (= :lambda-args tag) (str "[" (str/join " " (rest node)) "]")
  (= :lambda tag) 
  (str "(fn " (str/join " " (map lsp->clojure (rest node))) ")")

  (= :ternary tag)
  (str "(if " (lsp->clojure v1) " " 
          (lsp->clojure v3) " "
          (lsp->clojure v5) ")")

  :unknown)

(defn- remove-comments [code]
  (-> code
      (str/replace #"\/\*(\*(?!\/)|[^*])*\*\/" "") ; /* comment block */ 
      (str/replace #"(\/\/)(.+?)(?=[\n\r]|\*\))" "" #_one-line-comment )))

(defn dart->clojure [dart]
  (->> dart remove-comments widget-parser lsp->clojure read-string))

(comment 
  (def column
  "
Column(
 children: [
   FlutterLogo(size: 50,),
   const Text('Flutter tutorial by woolha.com', style: const TextStyle(color: Colors.teal)),
   Icon(Icons.widgets),
 ],
)")


  (def code "
Column(
  children: const <Widget>[
    Text('Deliver features faster'),
    Text('Craft beautiful UIs'),
    Expanded(
      child: FittedBox(
        fit: BoxFit.contain, // otherwise the logo will be tiny
        child: FlutterLogo(),
      ),
    ),
  ],
)")

  (defparser widget-parser 
    (io/resource "widget-parser.bnf")
    :auto-whitespace :standard)

  (insta/parse widget-parser column)

  (dart->clojure code)
  )

