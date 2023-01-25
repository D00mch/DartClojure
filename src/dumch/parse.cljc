(ns dumch.parse
  (:require
    #?(:clj  [clojure.java.io :as io])
    #?(:cljs [clojure.edn :refer [read-string]])
    [clojure.string :as str]
    [clojure.set :as set]
    #?(:cljs [dumch.base64 :as b64-cljs])
    [dumch.util :refer [ws nl maps mapn mapcats mapcatn]]
    #?(:cljs [dumch.util :refer-macros [inline-resource]])
    #?(:clj [instaparse.core :as insta :refer [defparser]]
       :cljs [instaparse.core :as insta :refer-macros [defparser]])
    [rewrite-clj.node :as n
     :refer [list-node map-node token-node keyword-node vector-node]
     :rename {list-node lnode, vector-node vnode,  map-node mnode,
              token-node tnode, keyword-node knode}]
    #_[clj-java-decompiler.core :refer [decompile]]
    #_[criterium.core :refer [quick-bench]])
  #?(:clj
     (:import java.util.Base64)))

#?(:clj (set! *warn-on-reflection* true))

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

(defn- split-by-$-expr [s]
  ;; two reasons for it to be that complex:
  ;; - can't make regex for nested {{}} work in jvm: (?=\{((?:[^{}]++|\{(?1)\})++)\})
  ;; - regex is slow anyway;
  (loop [stack [] ;; to count balanced curly: '{', '}'
         rslt []
         cur-str []
         cur-expr []
         i 0]
    (if (>= i (count s))
      (conj rslt (apply str (if (empty? cur-expr) cur-str cur-expr)))

      (let [ch (.charAt ^String s i)
            building-str? (empty? stack)
            allow-open-b? (and (> i 0) (= (.charAt ^String s (dec i)) \$))
            open-b? (and (or allow-open-b? (seq stack)) (= ch \{))
            close-b? (= ch \})
            expr-ends? (and close-b? (= (count stack) 1))]

        (if building-str?
          (recur
            (if open-b? (conj stack 1) stack)
            (if open-b?
              (conj rslt (subs (apply str cur-str) 0 (dec (count cur-str))))
              rslt)
            (if open-b? [] (conj cur-str ch))
            cur-expr
            (inc i))

          (recur
            (cond open-b? (conj stack 1)
                  close-b? (pop stack)
                  :else stack)
            (if expr-ends? (conj rslt (apply str cur-expr)) rslt)
            cur-str
            (if expr-ends? [] (conj cur-expr ch))
            (inc i)))))))

(defn- substitute-$
  "Returns string AST with $ substitutions"
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
  (cond (not= tag :number)
        (throw (ex-info "node with wrong tag passed" {:tag tag}))

        (str/starts-with? value "0x")
        (symbol value)

        :else
        (-> value (str/replace #"^\." "0.") read-string)))

(declare ast->clj)

(defn- flatten-dot [node]
  (loop [[tag v1 v2 :as node] node ;; having structure like [dot [dot a b] c]
         stack [] ;; put here 'c', then 'b', while trying to find 'a' ^
         [a [tag2 n params :as b] :as rslt] []  ;; result will look like [a b c]
         op? false]
    (cond (= :dot tag) (recur v1 (conj stack v2) rslt op?)
          (= :dot-op tag) (recur v1 (conj stack v2) rslt true)
          node (recur nil (conj stack node) rslt op?)
          (seq stack) (recur node (pop stack) (conj rslt (peek stack)) op?)
          op? (lnode (list* (tnode 'some->) ws (maps ast->clj rslt)))
          (= (count rslt) 2)
          (ast->clj [tag2 n (cons :params (cons [:argument a] (next params)))])
          :else (lnode (list* (tnode '->) ws (maps ast->clj rslt))))))

(defn- dot->clj [[tag v1 v2]]
  (let [dt (if (= tag :invoke) "." ".-")]
    (if (and v2 (> (count v2) 1))
      (lnode (list* (ast->clj [tag v1]) ws (ast->clj v2)))
      (tnode (symbol (str dt (ast->clj v1)))))))

(defn flatten-same-node [[f & params]] ;; [f [f 1 2] 3] -> [f 1 2 3]
  (mapcat
    #(if (and (sequential? %) (= (first %) f))
       (flatten-same-node %)
       [%])
    params))

(defn flatten-cascade [node]
  (let [flt (flatten-same-node node)]
    (lnode
      (list* (tnode 'doto) ws
             (ast->clj (first flt))
             (->> flt
                  (drop 1)
                  (map (fn [[tag & params :as node]]
                         (if (= tag :constructor)
                           (cons :invoke params)
                           node)))
                  (maps ast->clj))))))

(defn- flatten-commutative-node [[f :as node]]
  (list*
    [:identifier (case f :and "and", :or "or", :add "+", :mul "*")]
    (flatten-same-node node)))

(defn compare-longest-path
  "Getting a seq of nodes like:
  `([:compare [:identifier \"a\"] \">\" [:identifier \"b\"]]
   [:compare [:identifier \"b\"] \">\" [:identifier \"c\"]] ...)`.
  In other words, we have a graph here. Example:
  `a -> b, b -> c, b -> d, c -> e`.
  This function returns the seq of nodes which is the longest path.
  In terms of 'Example' above, it is going to be: `a -> b -> c -> e`"
  ([nodes]
   (compare-longest-path nodes (group-by #(-> % second second) nodes)))
  ([nodes dictionary]
   (apply max-key
          count
          ; node exmaple
          (for [[_ [_ _] _ [_ end] :as node] nodes
                :let [continues (get dictionary end)]]
            (cons
              node
              (when continues
                (compare-longest-path continues (dissoc dictionary end))))))))

(defn- build-compare-ast [compare-vals]
  (list* :compare+
         [:identifier (nth (first compare-vals) 2)]
         (second (first compare-vals))
         (map #(nth % 3) compare-vals)))

(defn- unify-compare
  "If we have different compare functions in the node, like `<` and `>`,
  replace all `<` with `>` and `<=` with `>=`.
  Returns grouped by function sequences"
  [compare-nodes]
  (let [compare-fns (set (map #(nth % 2) compare-nodes))]
    (if (<= (count compare-fns) 1)
      [compare-nodes]
      (->> compare-nodes
           (mapv
             (fn [[tag e1 f e2 :as node]]
               (cond (= f "<") [tag e2 ">" e1]
                     (= f "<=") [tag e2 ">=" e1]
                     :else node)))
           (group-by #(nth % 2))
           vals))))

(defn- flatten-compare [[tag & params :as and-node]]
  (let [{compare-nodes true,
         other-nodes false} (group-by (fn [[f]] (= f :compare)) params)]
    (if compare-nodes
      ;; `seq` below means `seq of nodes`
      (let [compare-seqs (unify-compare compare-nodes)
            longest-seqs (map compare-longest-path compare-seqs)
            ast-seqs (map build-compare-ast longest-seqs)
            other-nodes (apply set/difference
                               (set (apply concat other-nodes compare-seqs))
                               (or (map set longest-seqs) '(#{})))]
        (cond (seq other-nodes)
              (concat [tag] other-nodes ast-seqs)

              (= (count ast-seqs) 1) (next (first ast-seqs))

              :else (cons tag ast-seqs)))
      and-node)))

(defn dfs [f sq]
 (some f (tree-seq coll? seq sq)))

(defn- switch-case-ok? [[tag v1 v2 v3]]
  (or (= tag :default-case)
      (and (= tag :switch-case)
           (= (first v1) :cases)
           (or (some-> v3 second (= "break"))
               (dfs #{:return} v2)))))

(defn- if->clj [node]
  (case (count node)
    3 (lnode (list* (tnode 'when) ws (->> node rest (maps ast->clj))))
    4 (lnode (list* (tnode 'if) ws (->> node rest (maps ast->clj))))
    (lnode
      (if (even? (count node))
        (concat
          (list* (tnode 'cond) ws (->> node butlast rest (maps ast->clj)))
          [ws (knode :else) ws (ast->clj (peek node))])
        (list* (tnode 'cond) ws (->> node rest (maps ast->clj)))))))

(defn- switch-ok? [[_ _ & cases]]
  (reduce #(and %1 %2) (map switch-case-ok? cases)))

(defn- switch-case->clj-nodes [[_ [_ & cases] body]]
  (let [body (ast->clj body)]
    (mapcat
      #(list (ast->clj %) body)
      cases)))

(defn- switch-branch->clj-nodes [[tag v1 :as node]]
  (if (= tag :switch-case)
    (switch-case->clj-nodes node)
    [(ast->clj v1)]))

(defn- switch->case-or-warn [[_ expr & cases :as node]]
  (if (switch-ok? node)
    (lnode
      (list*
        (tnode 'case) ws
        (ast->clj expr) nl
        (mapcatn #(switch-branch->clj-nodes %) cases)))
    :unidiomatic))

(defn- try-on->clj-node [[_ [_ q] [_ e] body :as on-part]]
  (lnode [(tnode 'catch) ws
          (ast->clj (or q [:identifier "Exception"])) ws
          (ast->clj (or e [:identifier "e"])) nl
          (ast->clj body)]))

(defn- try-branch->clj-node [node]
  (if (= (count node) 2)
    (lnode [(tnode 'finally) ws (ast->clj (second node))])
    (try-on->clj-node node)))

(defn- try->clj [[_ body & branches]]
  (lnode
    (list*
      (tnode 'try) nl
      (ast->clj body)
      (map #(try-branch->clj-node %) branches))))

(defn- var-declare->clj [[_ v1 :as node]]
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
                    (maps ast->clj)))))))

(defn ast->clj [[tag v1 v2 v3 :as node]]
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
    :var-declare (var-declare->clj node)
    :var-init (lnode [(tnode 'def) ws
                      (ast->clj v1) ws
                      (or (some-> v2 ast->clj) (tnode 'nil))])
    :class
    (lnode (list*
             (tnode 'comment) nl
             "use flutter/widget macro instead of classes" nl
             (mapcat #(if (sequential? %) % [%])
                     (mapn ast->clj (rest node)))))
    :method (lnode [(tnode 'defn) ws
                    (ast->clj v1) ws
                    (ast->clj v2) ws
                    (ast->clj v3)])

    :constructor (lnode (list* (ast->clj v1) ws (ast->clj v2)))
    :params (mapcats ast->clj (rest node))
    :argument (if v2 (map ast->clj (rest node)) [(ast->clj v1)])
    :named-arg (tnode (symbol (str "." (ast->clj v1))))

    :dot  (flatten-dot node)
    :dot-op (flatten-dot node)
    :invoke (dot->clj node)
    :field (dot->clj node)

    :lambda (lnode [(tnode 'fn) ws (ast->clj v1) nl (ast->clj v2)])
    :lambda-args (vnode (->> node rest (maps ast->clj)))
    :block (ast->clj (cons :code (rest node)))

    :ternary (ast->clj [:if v1 v2 v3])
    :if (if->clj node)
    :switch (switch->case-or-warn node)
    :try (try->clj node)
    :cascade (flatten-cascade node)

    :for-in (n/list-node [(tnode 'for) ws
                          (vnode [(ast->clj v1) ws (ast->clj v2)]) nl
                          (ast->clj v3)])

    :return (if v1 (ast->clj v1) (tnode 'nil))
    :typecasting (ast->clj v1)
    :const (n/meta-node (tnode :const) (ast->clj v1))
    :identifier (symbol (str/replace v1 #"!" ""))
    :qualified (tnode (symbol (str/join "." (map ast->clj (next node)))))
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
    (:or :add :mul) (lnode (maps ast->clj (flatten-commutative-node node)))
    (:not :dec :inc) (lnode [(tnode (symbol tag)) ws (ast->clj v1)])
    (:compare :div :ifnull :equality)
    (lnode [(tnode (dart-op->clj-op v2)) ws (ast->clj v1) ws (ast->clj v3)])

    (cond
      (and (keyword? tag) (-> tag str second (= \_))) :unidiomatic
      :else :unknown)))

(defn- encode [^String to-encode]
  #?(:clj (.encodeToString (Base64/getEncoder) (.getBytes to-encode))
     :cljs (b64-cljs/utf8_to_b64 to-encode)))

(defn- decode [^String to-decode]
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
                     (str "'" (% (subs m 1 (dec (count m)))) "'"))]
    (-> code

        ;; to simplify modifications below
        multiline->single

        ;; TODO: find another way to deal with comments
        ;; the problem is that comments could appear inside strings
        (str/replace str-pattern (transform encode))    ; encode strings to Base64

        ;; cleaning code from comments
        (str/replace #"/\*(\*(?!/)|[^*])*\*/" "")    ; /* ... */
        (str/replace #"(//).*" "")                   ; // ...
        (str/replace str-pattern
                     (transform (comp substitute-curly-quotes decode))))))

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
Future<String> createOrderMessage() async {
  var order = await fetchUserOrder();
  return 'Your order is: $order';
}
    ")

  (insta/parses widget-parser code2)

  (dart->clojure code2)

  (-> "a && b && c"
      dart->ast
      ast->clj
      n/string))
