(ns dumch.dartclojure
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [better-cond.core :refer [defnc-]]
    [clojure.string :as str]
    [instaparse.core :as insta :refer [defparser]])
  (:gen-class))

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

#_(constructor-name "field.pinPut.copyWith(border: 1)" ":border 1" "m")

(defn str-insert
  "Insert c in string s at index i."
  [s c i]
  (str (subs s 0 i) c (subs s i)))

(defnc- lsp->clojure [[tag v1 v2 v3 _ v5 :as node] 
                      & {:keys [m] :or {m "m"}}]
  let [const? (= v1 "const")]

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
  (str 
    (when const? "^:const ")
    (constructor-name (if const? v2 v1) (lsp->clojure (if const? v3 v2)) m))

  (= :params tag) (str/join " " (map lsp->clojure (rest node)))
  (and (= :argument tag) v2) (str/join " " (map lsp->clojure (rest node)))
  (= :argument tag) (lsp->clojure v1)  

  (= :lambda-args tag) (str "[" (str/join " " (rest node)) "]")
  (= :lambda tag) 
  (str "(fn " (str/join " " (map lsp->clojure (rest node))) ")")

  (= :ternary tag)
  (str "(if " (lsp->clojure v1) " " 
          (lsp->clojure v3) " "
          (lsp->clojure v5) ")")

  :unknown)

(comment
 (->>
  ;code
  ;;"_pinPut.copyWith(border: Border.all(color: Colors.green))"
  "SClass.field.copyWith(border: 1)"
  widget-parser 
  lsp->clojure read-string
  ))
;; ((.copyWith _pinPut) :border (m.Border/all :color m.Colors/green))

(defn dart->clojure [dart]
  (->> dart widget-parser lsp->clojure read-string))

(defn -main [code]
  (->> code dart->clojure pprint))

(comment 
  (defparser widget-parser 
    (io/resource "widget-parser.bnf")
    :auto-whitespace :standard)

  (def code "
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
      )")

  (dart->clojure code)
  (insta/parses widget-parser code))
