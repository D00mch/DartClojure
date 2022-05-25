(ns dumch.parse-test
  (:require [clojure.test :refer :all]
            [dumch.improve :refer [lists->vectors]]
            [dumch.parse :refer :all]))

(deftest invocations-name-test
  (testing "simple constructor"
    (is (= (dart->clojure "Text('text')")
           (-> '(m/Text "text")))))

  (testing "line invocations"
    (is (= (dart->clojure "One(1).two().three(2, 3)")
           (-> '(-> (m/One 1) (.two) (.three 2 3))))))

  (testing "instance method invocation"
    (is (= (dart->clojure "_pinPut.copyWith(border: 1)")
           '(.copyWith _pinPut :border 1))))

  (testing "field's field.method invocation"
    (is (= (dart->clojure "field.pinPut.copyWith(border: 1)")
           '(-> field .pinPut (.copyWith :border 1)))))

  (testing "static field method invocation"
    (is (= (dart->clojure "SClass.field.copyWith(border: 1)")
        '(-> m/SClass .field (.copyWith :border 1))))))

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

(deftest or-test ;; TODO: support ? on names 
  (is (= (dart->clojure "_packageInfo?.version ?? 1.0")
         '(or (.version _packageInfo?) 1.0))))

(deftest not-test
  (is (= (dart->clojure "!a") '(not a))))

(deftest set!-test
  (is (= (dart->clojure "a = b") '(set! a b))))

(deftest get-test
  (testing "single get"
   (is (= (dart->clojure "tabs[2]") '(get tabs 2))))
  (testing "serveral get in a row"
    (is (= (dart->clojure "questions[1]['two']") 
           '(get (get questions 1) "two")))))
