(ns dumch.parse
  (:require
    [better-cond.core :as b]
    #?(:cljs [dumch.base64 :as b64-cljs])
    #?(:clj [clojure.java.io :as io])
    #?(:cljs [clojure.edn :refer [read-string]])
    [clojure.string :as str]
    [dumch.util :refer [ws nl maps mapn mapcats mapcatn]]
    #?(:cljs [dumch.util :refer-macros [inline-resource]])
    #?(:clj [instaparse.core :as insta :refer [defparser]]
       :cljs [instaparse.core :as insta :refer-macros [defparser]])
    [rewrite-clj.node :as n
     :refer [list-node map-node token-node keyword-node vector-node]
     :rename {list-node lnode, vector-node vnode,  map-node mnode,
              token-node tnode, keyword-node knode}])
  #?(:clj
     (:import java.util.ArrayDeque
              java.util.Base64
              java.lang.StringBuilder)))

(defparser widget-parser
  #?(:clj (io/resource "widget-parser.bnf")
     :cljs (inline-resource "widget-parser.bnf"))
  :auto-whitespace :standard)

(defn- dart-op->clj-op [o]
  (case o
    "is" 'dart/is?
    "??" 'or
    "==" '=
    "!=" 'not=
    "%"  'rem
    "~/" 'quot
    (symbol o)))

(defn- substitute-curly-quotes [s & {f :backward}]
  ;; crutch for cases like ''' ' ''' and '\''
  (if f
    (-> (str/replace s #"”" "\"")
        (str/replace #"’" "'"))
    (-> (str/replace s #"\"" "”")
        (str/replace #"'" "’"))))

(defn- split-by-$-expr [^:String s]
  ;; two reasons for it to be that complex:
  ;; - can't make regex for nested {{}} work in jvm: (?=\{((?:[^{}]++|\{(?1)\})++)\})
  ;; - regex is slow anyway;
  (loop [stack #?(:clj (ArrayDeque.) ;; to count balanced curly: '{', '}'
                  :cljs (js/Array.))
         rslt []
         cur-str #?(:clj (StringBuilder.)
                    :cljs (js/Array.))
         cur-expr #?(:clj (StringBuilder.)
                     :cljs (js/Array.))
         i 0]
    (if (>= i (count s))
      (conj rslt #?(:clj (.toString (if (empty? cur-expr) cur-str cur-expr))
                    :cljs (.join (if (empty? cur-expr) cur-str cur-expr) "")))

      (let [ch (.charAt s i)
            building-str? (empty? stack)
            allow-open-b? (and (> i 0) (= (.charAt s (dec i)) \$))
            open-b? (and (or allow-open-b? (seq stack)) (= ch \{))
            close-b? (= ch \})
            expr-ends? (and close-b? (= (count stack) 1))]

        (if building-str?
          (recur
            (if open-b? (doto stack (.push 1)) stack)
            (if open-b?
              (conj rslt (subs #?(:clj (.toString cur-str)
                                  :cljs (.join cur-str ""))
                               0
                               #?(:clj (dec (.length cur-str))
                                  :cljs (dec (.-length cur-str)))))
              rslt)
            #?(:clj (if open-b? (StringBuilder.) (.append cur-str ch))
               :cljs (if open-b? (js/Array.) (doto cur-str (.push ch))))
            cur-expr
            (inc i))

          (recur
            (cond open-b? (doto stack (.push 1))
                  close-b? (doto stack .pop)
                  :else stack)
            (if expr-ends? (conj rslt #?(:clj (.toString cur-expr)
                                         :cljs (.join cur-expr ""))) rslt)
            cur-str
            #?(:clj (if expr-ends? (StringBuilder.) (.append cur-expr ch))
               :cljs (if expr-ends? (js/Array.) (doto cur-expr (.push ch))))
            (inc i)))))))

(defn- substitute-$
  "Returns ast of string with $ substitutions"
  [^:String s]
  (let [p #"\$[a-zA-Z_]+[a-zA-Z0-9!_]*"
        s (str/replace s p (fn [m] (str "${" (subs m 1) "}")))
        sq (split-by-$-expr s)]
    (if (= (count sq) 1)
      [:string+ s]
      [:strings+ (->> sq
                      (map-indexed #(if (zero? (mod (inc %1) 2))
                                      (widget-parser %2)
                                      (when (seq %2) [:string+ %2])))
                      (filter some?))])))

(defn- node->string-ast
  "Returns ast, handling $, de-`substitute-curly-quotes`, commas and r (raw str)"
  [[_ s]]
  (if (-> s first (= \r))
    [:string+ (str/replace s #"^r.|.$" "")]
    (-> (str/replace s #"^.|.$" "")
        (substitute-curly-quotes :backward true)
        substitute-$)))

(defn- node->number [[tag value]]
  (if (= tag :number)
    (-> value (str/replace #"^\." "0.") read-string)
    (throw (ex-info "node with wrong tag passed" {:tag tag}))))

(defn- flatten-dot [node ast->clj]
  (loop [[tag v1 v2 :as node] node ;; having structure like [dot [dot a b] c]
         stack [] ;; put here 'c', then 'b', while trying to find 'a' ^
         [a [_ n params :as b] :as rslt] []  ;; result will look like [a b c]
         op? false]
    (cond (= :dot tag) (recur v1 (conj stack v2) rslt op?)
          (= :dot-op tag) (recur v1 (conj stack v2) rslt true)
          node (recur nil (conj stack node) rslt op?)
          (seq stack) (recur node (pop stack) (conj rslt (peek stack)) op?)

          op? (lnode (list* (tnode 'some->) ws (maps ast->clj rslt)))
          (= (count rslt) 2)
          (ast->clj [:invoke n (cons :params (cons [:argument a] (next params)))])
          :else (lnode (list* (tnode '->) ws (maps ast->clj rslt))))))

(defn flatten-same-node [[f & params]] ;; [f [f 1 2] 3] -> [f 1 2 3]
  (mapcat
    #(if (and (sequential? %) (= (first %) f))
       (flatten-same-node %)
       [%])
    params))

(defn flatten-cascade [node ast->clj]
  (lnode
    (list* (tnode 'doto) ws
           (->> node
                flatten-same-node
                (map (fn [[tag & params :as node]]
                       (if (= tag :constructor)
                         (cons :invoke params)
                         node)))
                (maps ast->clj)))))

(defn- flatten-commutative-node [[f :as node]]
  (list*
    [:identifier (case f :and "and", :or "or", :add "+", :mul "*")]
    (flatten-same-node node)))

(defn- flatten-compare [[_ & params :as and-node]]
  (let [compare-nodes (->> (filter (fn [[f]] (= f :compare)) params)
                           (sort-by (fn [[_ [_ value]]] value)))
        get-adjacent (fn [mapfn]
                       (->> compare-nodes
                            mapfn
                            (partition 2 1)
                            (take-while (fn [[p1 p2]] (= (last p1) (second p2))))
                            (mapcat identity)
                            seq))
        build-ast #(list* :compare+
                          [:identifier (nth (first %) 2)]
                          (distinct (mapcat (fn [[_ a _ b]] [a b]) %)))
        compare-vals (or (get-adjacent identity) (get-adjacent reverse))]
    (if compare-vals
      (let [and-node (filterv (fn [v] (not (some #(= v %) compare-vals))) and-node)
            compare-ast (build-ast compare-vals)]
        (if (= (count and-node) 1)
          (next compare-ast)
          (conj and-node compare-ast)))
      and-node)))

(defn dfs [f sq]
 (some f (tree-seq coll? seq sq)))

(b/defnc- switch-case-ok? [[tag v1 v2 v3]]
  (= tag :default-case) true

  :when (= tag :switch-case)
  :when (= (first v1) :cases)
  :when (or (some-> v3 second (= "break"))
            (dfs #{:return} v2))
  true)

(defn- switch-ok? [[_ _ & cases]]
  (reduce #(and %1 %2) (map switch-case-ok? cases)))

(defn- switch-case->clj-nodes [[_ [_ & cases] body] ast->clj]
  (let [body (ast->clj body)]
    (mapcat
      #(list (ast->clj %) body)
      cases)))

(defn- switch-branch->clj-nodes [[tag v1 :as node] ast->clj]
  (if (= tag :switch-case)
    (switch-case->clj-nodes node ast->clj)
    [(ast->clj v1)]))

(defn- switch->case-or-warn [[_ expr & cases :as node] ast->clj]
  (if (switch-ok? node)
    (lnode
      (list*
        (tnode 'case) ws
        (ast->clj expr) nl
        (mapcatn #(switch-branch->clj-nodes % ast->clj) cases)))
    :unidiomatic))

(defn ast->clj [[tag v1 v2 v3 v4 :as node]]
  #_(println :node node)
  (case tag
    :s (ast->clj v1)
    :code (if v2
            (lnode (list* (tnode 'do) nl (mapn ast->clj (rest node))))
            (ast->clj v1))

    :file (if v2
            (n/list-node (list* (tnode 'do) nl (mapn ast->clj (rest node))))
            (ast->clj v1))
    :import-block (lnode (list* (tnode 'require) ws (mapn ast->clj (rest node))))
    :import-as (vnode [(ast->clj v1) ws (knode :as) ws (ast->clj v2)])
    :import-naked (vnode [(ast->clj v1) ws (knode :as) ws 'give-an-alias-or-refer])
    :import-show (vnode [(ast->clj v1) ws
                         (knode :refer) ws
                         (vnode (maps ast->clj (drop 2 node)))])
    :import-hide (vnode [(ast->clj v1) ws (knode :as) ws 'be-aware-of-hide-here])
    :import-full
    (vnode [(ast->clj v1) ws
            (knode :as) ws
            (ast->clj v2) ws
            (knode :refer) ws
            (vnode (maps ast->clj (drop 3 node)))])
    :var-declare
    (let [inits (filter sequential? node)
          const? (= v1 "const")
          with-const (fn [[_ n v :as var-init-node]]
                       (if const?
                         [:var-init [:const n] v]
                         var-init-node))]
      (if (= (count inits) 1)
        (ast->clj (with-const (first inits)))
        (lnode
          (list* (tnode 'do) ws
                 (->> (map with-const inits)
                      (maps ast->clj))))))

    :var-init (lnode [(tnode 'def) ws
                      (ast->clj v1) ws
                      (or (some-> v2 ast->clj) (tnode 'nil))])
    :class
    (lnode
      (list*
        (tnode 'comment) nl
        "use flutter/widget macro instead of classes" nl
        (mapcat
          #(if (sequential? %) % [%])
          (mapn ast->clj (rest node)))))
    :method (lnode [(tnode 'defn) ws
                    (ast->clj v1) ws
                    (ast->clj v2) nl
                    (ast->clj v3)])

    :constructor (lnode (list* (ast->clj v1) ws (ast->clj v2)))
    :params (mapcats ast->clj (rest node))
    :argument (if v2 (map ast->clj (rest node)) [(ast->clj v1)])
    :named-arg (knode (keyword (ast->clj v1)))

    :dot  (flatten-dot node ast->clj)
    :dot-op (flatten-dot node ast->clj)
    :invoke (lnode (list* (ast->clj [:field v1]) ws (ast->clj v2)))
    :field (tnode (symbol (str "." (ast->clj v1))))

    :lambda (lnode [(tnode 'fn) ws (ast->clj v1) nl (ast->clj v2)])
    :lambda-body (ast->clj (cons :code (rest node)))
    :lambda-args (vnode (->> node rest (maps ast->clj)))

    :ternary (ast->clj [:if v1 v2 v3])
    :if (case (count node)
          3 (lnode (list* (tnode 'when) ws (->> node rest (maps ast->clj))))
          4 (lnode (list* (tnode 'if) ws (->> node rest (maps ast->clj))))
          (lnode
            (if (even? (count node))
              (concat
                (list* (tnode 'cond) ws (->> node butlast rest (maps ast->clj)))
                [ws (knode :else) ws (ast->clj (last node))])
              (list* (tnode 'cond) ws (->> node rest (maps ast->clj))))))
    :switch (switch->case-or-warn node ast->clj)
    :cascade (flatten-cascade node ast->clj)

    :for-in (n/list-node [(tnode 'for) ws
                          (vnode [(ast->clj v1) ws (ast->clj v2)]) nl
                          (ast->clj v3)])

    :return (if v1 (ast->clj v1) (tnode 'nil))
    :typecasting (ast->clj v1)
    :const (n/meta-node (tnode :const) (ast->clj v1))
    :identifier (symbol (str/replace v1 #"!" ""))
    :list (n/vector-node (maps ast->clj (rest node)))
    :map (mnode (maps ast->clj (rest node)))
    :get (lnode [(tnode 'get) ws (ast->clj v1) ws (ast->clj v2)])
    :string (-> node node->string-ast ast->clj)
    :string+ v1
    :strings+ (lnode (list* (tnode 'str) ws (maps ast->clj v1)))
    :number (node->number node)

    :neg (lnode [(tnode '-) ws (ast->clj v1)])
    :sub (lnode [(tnode '-) ws (ast->clj v1) ws (ast->clj v2)])
    :await (lnode [(tnode 'await) ws (ast->clj v1)])

    :and (->> node flatten-commutative-node flatten-compare (maps ast->clj) lnode)
    :compare+ (lnode (list* (ast->clj v1) ws (->> node (drop 2) (maps ast->clj))))
    (cond
      (#{:or :add :mul} tag) (lnode (maps ast->clj (flatten-commutative-node node)))
      (#{:not :dec :inc} tag) (lnode [(tnode (symbol tag)) ws (ast->clj v1)])
      (#{:compare :div :ifnull :equality} tag)
      (lnode [(tnode (dart-op->clj-op v2)) ws (ast->clj v1) ws (ast->clj v3)])
      (and (keyword? tag) (-> tag str second (= \_))) :unidiomatic
      :else :unknown)))

(defn- encode [to-encode]
  #?(:clj (.encodeToString (Base64/getEncoder) (.getBytes to-encode))
     :cljs (b64-cljs/utf8_to_b64 to-encode)))

(defn- decode [to-decode]
  #?(:clj (String. (.decode (Base64/getDecoder) to-decode))
     :cljs (b64-cljs/b64_to_utf8 to-decode)))

(defn- multiline->single [s]
  (let [pattern #"'{3}([\s\S]*?'{3})|\"{3}([\s\S]*?\"{3})"
        transform (fn [[m]] (substitute-curly-quotes m))]
    (-> s
        (str/replace pattern transform)
        (str/replace #"”””|’’’" "'"))))

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

        ;; clean from annotations
        (str/replace #"(\s*@.*\n)" "\n")

        ;; cleaning code from comments
        (str/replace #"/\*(\*(?!/)|[^*])*\*/" "")    ; /* ... */
        (str/replace #"(//).*" "")                   ; // ...
        (str/replace str-pattern
                     (transform (comp substitute-curly-quotes
                                      decode))))))

(defn save-read [code]
  (try
    (n/sexpr code)
    (catch #?(:clj Exception
              :cljs js/Error) e (n/string code))))

(defn dart->ast [^:String dart]
  (insta/parse widget-parser (clean dart)))

(defn dart->clojure [^:String dart]
  (-> dart dart->ast (ast->clj) save-read))

(comment

  (def code "
    class A {
             static var i = 1;
             }
    ")

  (def code2 "
    var bar = 0;
    const bar = 1;
    static bar = 2;
    static final bar = 3;
    static const int bar = 4;
    static final int bar = 5;
    static final int bar = 6, lar;
    ")

  (insta/parses widget-parser code2 :total 1)

  (dart->clojure code2)

  (-> "a && b && c"
      dart->ast
      ast->clj
      n/string))
