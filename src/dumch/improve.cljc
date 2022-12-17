(ns dumch.improve 
  (:require
    [better-cond.core :as b]
    [clojure.string :as str]
    [dumch.util :refer [lists* upper-case?]]
    [rewrite-clj.node :as n]
    [rewrite-clj.paredit :as p]
    [rewrite-clj.zip :as z]))

(defn- remove-child-named-arg [zloc]
  (-> zloc z/down (z/find-value z/right '.child) z/right  z/remove z/remove* z/up))

(defn- nested-children? [zloc times]
  (let [zloc (-> zloc z/down (z/find-value z/right '.child))] 
    (cond 
      (not= '.child (z/sexpr zloc)) false
      (= 1 times) true
      :else (recur (-> zloc z/next) (dec times)))))

(defn- nest-flatten [zloc & {fl :flutter :or {fl "f"}}]
  (loop [zloc zloc
         rslt []]
    (if-let [child (-> zloc z/down (z/find-value z/right '.child))]
      (recur (-> child z/right) (conj rslt (remove-child-named-arg zloc)))
      (z/edn
        (n/list-node (lists* (n/token-node (symbol (str (when fl (str fl "/")) "widget"))) 
                             (map z/node (conj rslt zloc))))))))

(defn wrap-nest [zloc & {fl :flutter :or {fl "f"}}]
  (z/prewalk zloc
             #(and (z/list? %) (nested-children? % 3))
             #(nest-flatten % :flutter fl)))

(b/defnc- try-remove-redundant-do [zloc] 
  :when-let [fn-node (-> zloc z/down)]
  :let [fn? (= (z/sexpr fn-node) 'fn)
        defn? (= (z/sexpr fn-node) 'defn)]

  :when-let [_ (or fn? defn?)
             fn-node (if defn? (z/right fn-node) fn-node)
             do-zloc (-> fn-node z/right z/right z/down)]
             
  (= (z/sexpr do-zloc) 'do)
  (-> do-zloc z/remove z/splice z/up))

(def ^:private core-vars
  #{'AbstractClassInstantiationError 'ArgumentError 'AssertionError 'BidirectionalIterator 
    'BigIntbool 'CastError 'Comparable 'Comparator 'ConcurrentModificationError 
    'CyclicInitializationError 'DateTime 'double 'Duration 'Error 'Exception 'Expando  
    'FallThroughError 'Finalizer 'FormatException 'Function 'IndexError 'IntegerDivisionByZeroException
    'Invocation 'Iterable 'Iterator 'List 'Map 'MapEntry 'Match 'NoSuchMethodError 'Null 'NullThrownError
    'Object 'OutOfMemoryError 'Pattern 'pragma 'RangeError 'RegExp 'RegExpMatch 'Runes 'RuneIterator 'Set 
    'Sink 'StackOverflowError 'StackTrace 'StateError 'Stopwatch 'String 'StringBuffer 'StringSink
    'Symbol 'Type 'TypeError 'UnimplementedError 'UnsupportedError 'Uri 'WeakReference})

(b/defnc- insert-alias [symb m]
  :let [s (str symb)]
  (not (str/includes? s ".")) (symbol (str m "/" symb)) 

  :let [parts (cons m (str/split s #"\."))]
  (symbol (str (str/join "." (butlast parts)) "/" (last parts))))

(defn simplify [node & {fl :flutter, m :material :or {fl "f", m "m"}}]
  (-> node 
      z/edn
      (z/postwalk 
        (fn [zloc]
          (b/cond 
            (and (z/list? zloc) (nested-children? zloc 3))
            (nest-flatten zloc :flutter fl)

            :let [undo-node (try-remove-redundant-do zloc)]
            undo-node undo-node

            :let [expr (z/sexpr zloc)] 
            (= expr 'print) (z/edit zloc #(symbol (str "dart:core/" %)))

            ;; require alias
            (and (symbol? expr) 
                 (upper-case? (first (str expr)))
                 (not (core-vars expr)))
            (z/edit zloc #(insert-alias % m))

            :else zloc)))))

#_(-> "(Duration 39)"
      rewrite-clj.parser/parse-string
      simplify
      z/sexpr)

(comment 
  (def nested "(Container 
                 .child 
                 (Box .child (Padding .child (Text 1)) .a 1))") 

  (-> nested z/of-string (nested-children? 3))

  (-> nested z/of-string
      z/down
      (z/find-value '.child)
      z/right
      p/raise
      z/up
      z/string
      )

  (def animation "(Column 
      .children 
      [(Text \"name\") 
       (Icon m.Icons/widgets)
       (IgnorePointer.
         .ignoring (boolean @open)
         .child
         (AnimatedContainer.
           .transformAlignment m.Alignment/center
           .transform (m.Matrix4/diagonal3Values
                        (if @open 0.7 1.0)
                        (if @open 0.7 1.0)
                        1.0)
           .duration ^:const (Duration. .milliseconds 250)
           .curve ^:const (Interval. 0.0 0.5 .curve m.Curves/easeOut)
           .child
           (AnimatedOpacity.
             .opacity (if @open 0.0 (+ (+ 2.0 1.0) (+ -3.0) 1.0))
             .curve ^:const (Interval. 0.25 1.0 .curve m.Curves/easeInOut)
             .duration ^:const (Duration. .milliseconds 250)
             .child
             (FloatingActionButton.
               .onPressed toggle
               .child
               (Icon. m.Icons/create)))))])
    ")

  (z/sexpr (wrap-nest (z/of-string animation))))
