(ns dumch.parse-test
  (:require [clojure.test :refer :all]
            [dumch.improve :refer [lists->vectors wrap-nest]]
            [dumch.parse :refer :all]
            [instaparse.core :as insta]))

(deftest invocations-name-test
  (testing "simple constructor"
    (is (= (dart->clojure "Text('text')")
           (-> '(m/Text "text")))))
  (testing "typed constructor"
    (is (= (dart->clojure "Text<A<B>, C>('text')")
           (-> '(m/Text "text")))))
  (testing "line invocations"
    (is (= (dart->clojure "One(1).two().three(2, 3)")
           (-> '(-> (m/One 1) (.two) (.three 2 3))))))
  (testing "instance method invocation"
    (is (= (dart->clojure "_pinPut.copyWith(border: 1)")
           '(.copyWith _pinPut :border 1))))
  (testing "field's field.method invocation"
    (is (= (dart->clojure "field!.pinPut.copyWith(border: 1)")
           '(-> field .pinPut (.copyWith :border 1)))))
  (testing "static field method invocation"
    (is (= (dart->clojure "SClass.field.copyWith(border: 1)")
           '(-> m/SClass .field (.copyWith :border 1)))))
  (testing "invocation on list"
    (is (= (-> "[1].cast()" dart->clojure wrap-nest)
           '(-> [1] (.cast))))
    (is (= (-> "[1].length" dart->clojure wrap-nest)
           '(-> [1] (.length)))))
  (testing "invocation on map"
    (is (= (-> "{1:1}.cast()" dart->clojure wrap-nest)
           '(-> {1 1} (.cast))))
    (is (= (-> "{1:1}.length" dart->clojure wrap-nest)
           '(-> {1 1} (.length))))))

(deftest optional-name-test
  (testing "optional field somewhere in the chain"
    (is (= (dart->clojure "obj?.field.name?.first")
           '(some-> obj .field .name .first)))
    (is (= (dart->clojure "obj.field?.name")
           '(some-> obj .field .name))))
  (testing "field in optional object"
    (is (= (dart->clojure "obj?.name")
           '(some-> obj .name)))))

(deftest optional-invocation-test 
  (testing "optional field somewhere in the invocation chain"
    (is (= (dart->clojure "SClass?.field.copyWith(border: 1)")
           '(some-> m/SClass .field (.copyWith :border 1))))
    (is (= (dart->clojure "SClass.field?.copyWith()")
           '(some-> m/SClass .field (.copyWith)))))
  (testing "invocation on optional field"
    (is (= (dart->clojure "instance?.copyWith(border: 1)")
           '(some-> instance (.copyWith :border 1))))))

(deftest list-test
  (testing "ignore spread operator"
    (is (= (-> "[...state.a.map((acc) => _t(ctx, acc))]" 
               dart->clojure
               lists->vectors)
           '(:unknown))))
  (testing "typed list"
    (is (= (-> "<Int>[1, 2]" dart->clojure lists->vectors)
           '(1 2))))
  (testing "column children typeless list"
    (is 
      (= (-> "Column(children: [const Text('name'),
                                Icon(Icons.widgets)])" 
             dart->clojure)
         '(m/Column :children '((m/Text "name") (m/Icon m.Icons/widgets)))))))

(deftest map-test
  (testing "typeless map as named parameter"
    (is 
      (= (dart->clojure "ListCell.icon(m: {1 : 2, 'a' : b, c : 'd'})")
         '(.icon m/ListCell :m {1 2, "a" b, c "d"}))))
  (testing "typed map" 
    (is 
      (= (-> "<int, List<int>>{1: [1, 2]}" dart->clojure lists->vectors)
         {1 [1 2]}))))

(deftest set!-test
  (is (= (dart->clojure "a = b") :unidiomatic)))

(deftest get-test
  (testing "single get"
   (is (= (dart->clojure "tabs[2]") '(get tabs 2))))
  (testing "serveral get in a row"
    (is (= (dart->clojure "questions[1]['two']") 
           '(get (get questions 1) "two")))))

(deftest logic-test
  (testing "and: &&"
    (is (= (dart->clojure "b && a") '(and b a))))
  (testing "or: ||, ??"
    (is (= (dart->clojure "b || a") '(or b a)))
    (is (= (dart->clojure "b ?? a") '(or b a))))
  (testing "compare: >, <, =>, <="
    (is (= (dart->clojure "b > a") '(> b a)))
    (is (= (dart->clojure "b < a") '(< b a)))
    (is (= (dart->clojure "b >= a") '(>= b a)))
    (is (= (dart->clojure "b <= a") '(<= b a)))
    (is (= (dart->clojure "1 is int") '(dart/is? 1 int))))
  (testing "equality: ==, !="
    (is (= (dart->clojure "b == a") '(= b a)))
    (is (= (dart->clojure "b != a") '(not= b a)))))

(deftest ternary-test
  (is (= (dart->clojure "a ? b : c") '(if a b c))))

(deftest unary-prefix-test
  (testing "not" (is (= (dart->clojure "!a") '(not a))))
  (testing "inc" (is (= (dart->clojure "++a") '(inc a))))
  (testing "dec" (is (= (dart->clojure "--a") '(dec a))))
  (testing "-" (is (= (dart->clojure "-a") '(- a)))))

(deftest math-test
  (testing "difference"
    (is (= (dart->clojure "b - a") '(- b a))))
  (testing "sum"
    (is (= (dart->clojure "b + a") '(+ b a))))
  (testing "remainder"
    (is (= (dart->clojure "b % a") '(rem b a))))
  (testing "divide and round"
    (is (= (dart->clojure "b ~/ a") '(quot b a))))
  (testing "division"
    (is (= (dart->clojure "b / a") '(/ b a))))
  (testing "multiply"
    (is (= (dart->clojure "b * a") '(* b a)))))

(deftest lambda-test
  (testing "lambda as ternary parameter"
    (is (= (dart->clojure 
             "TextButton(onPressed: _searchEnabled ? () { pop(); } : null)")
           '(m/TextButton
              :onPressed
              (if _searchEnabled 
                (fn [] (pop))
                null)))))
  (testing "lambda with =>"
    (is (= (dart->clojure "Button(onPressed: (ctx) => 1 + 1;)")
           '(m/Button :onPressed (fn [ctx] (+ 1 1))))))
  (testing "lambda with body"
    (is (= (dart->clojure "Button(onPressed: (ctx) { println(a == a); setState(a); })")
           '(m/Button :onPressed (fn [ctx] (do (println (= a a)) (setState a)))))))
  (testing "lambda with typed argument"
    (is (= (dart->clojure "Button(onPressed: (Context ctx) => 1 + 1;)")
           '(m/Button :onPressed (fn [ctx] (+ 1 1)))))))

(deftest if-test
  (testing "one branch if with curly body"
    (is (= (dart->clojure "if (a == b) print('a');")
           '(when (= a b) (print "a")))))
  (testing "if else with and without body"
    (is (= (dart->clojure "if (b) { print(1); return 3; } else return 2;")
           '(if b (do (print 1) 3) 2))))
  (testing "if and else-if"
    (is (= (dart->clojure "if (b) b; else if (a) a; else if (c) c;")
           '(cond b b a a c c))))
  (testing "if, and else-if, and else"
    (is (= (dart->clojure "if (b) b; else if (a) a; else c;")
           '(cond b b a a c)))))

(deftest comments-test
  (testing "line comment"
    (is (= (dart->clojure "Text(
                             'name', // comment
                           )")
           '(m/Text "name"))))
  (testing "block comment"
    (is (= (dart->clojure "Text(widget.photo.user!.name /* comment */ )")
           '(m/Text (-> widget .photo .user .name)))))
  (testing "comment-like structure inside string"
    (is (= (dart->clojure "Text(
                                'http://looks-like-comment', 
                                '/* one more */'
                                )")
           '(m/Text "http://looks-like-comment" "/* one more */")))))

(deftest strings-test
  (testing "special symbols inside string test"
    (is 
      (= 1 
         (->> "'\n\t\\s\"'"
              clean
              (insta/parses widget-parser) 
              count))))
  (testing "multiline string with inner comments" 
    (is 
      (= 1 
         (->>
           "\"\"\"
           multiline // comment like 
           \" some string inside multiline string \"
           /* another comment */ \"\"\""
           clean
           (insta/parses widget-parser) 
           count)))))

(deftest assignment-test
  (testing "typeless var assignment"
    (is (= (dart->clojure  "var a = b") :unidiomatic)))
  (testing "final assignment"
    (is (= (dart->clojure  "final String s = '1'; ") :unidiomatic)))
  (testing "const assignment"
    (is (= (dart->clojure  "const bar = 1000000") :unidiomatic))))

(deftest await-test
  (is (= (dart->clojure  "await a") '(await a))))

(deftest statement-test
  (testing "several expressions | statements in a row with | without ';'"
    (is (= (dart->clojure  "a = 2;
                            if (item.isLoading) {
                               print(1)
                            }
                            a") 
           '(do :unidiomatic (when (.isLoading item) (print 1)) a)))))
