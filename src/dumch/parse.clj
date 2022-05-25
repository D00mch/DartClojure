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

(defnc- lsp->clojure [[tag v1 v2 v3 _ v5 :as node] m]

  (= v1 "const") 
  (str "^:private " (lsp->clojure (remove #(= % "const" ) node) m))

  (= :s tag) (lsp->clojure v1 m)

  (string? node) (identifier-name node m)
  (= :string tag) (str/replace v1 #"\"|'" "\"")
  (= :named-arg tag) (str ":" v1)
  (= :number tag) (node->number node) 

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

  ;; the only reason for it being quoted list and not vector is the problem
  ;; with using zipper (improve) on a collectoin with both seq and vector 
  (= :list tag) (str "'(" (str/join " " (map #(lsp->clojure % m) (rest node))) ")")

  (= :lambda-args tag) (str "[" (str/join " " (rest node)) "]")
  (= :lambda tag) 
  (str "(fn " (str/join " " (map #(lsp->clojure % m) (rest node))) ")")

  (= :ternary tag)
  (str "(if " (lsp->clojure v1 m) " " 
          (lsp->clojure v3 m) " "
          (lsp->clojure v5 m) ")")

  :unknown)

(defn- remove-comments [code]
  (-> code
      (str/replace #"\/\*(\*(?!\/)|[^*])*\*\/" "") ; /* comment block */ 
      (str/replace #"(\/\/)(.+?)(?=[\n\r]|\*\))" "" #_one-line-comment )))

(defn dart->clojure [dart & {m :material :or {m "m"}}]
  (-> dart remove-comments widget-parser (lsp->clojure m) read-string))

(comment 
  
  (def code "
Column(
 children: [
   const Text('name'),
   Icon(Icons.widgets),
  AnimatedContainer(
        transformAlignment: Alignment.center,
        transform: Matrix4.diagonal3Values(
          _isOpened ? 0.7 : 1.0,
          _isOpened ? 0.7 : 1.0,
          1.0,
        ),
        duration: const Duration(milliseconds: 250),
        curve: const Interval(0.0, 0.5, curve: Curves.easeOut),
        child: AnimatedRotation(
            turns: _isOpened ? -0.1 : 0,
            curve: const Interval(0.25, 1.0, curve: Curves.easeInOut),
            duration: const Duration(milliseconds: 250),
            child: FloatingActionButton(
              onPressed: () => widget.closeHint.value = !widget.closeHint.value,
              backgroundColor: _isOpened ? Colors.white : theme.primaryColor,
              child: Icon(Icons.add, color: _isOpened ? theme.primaryColor : Colors.white),
            )),
  )
])

")

  (defparser widget-parser 
    (io/resource "widget-parser.bnf")
    :auto-whitespace :standard)

  (-> (insta/parse widget-parser code) (lsp->clojure "m") clojure.pprint/pprint)

  (dart->clojure code :material "b")
  )

