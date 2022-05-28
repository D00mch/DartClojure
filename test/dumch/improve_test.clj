(ns dumch.improve-test
  (:require [clojure.test :refer [deftest is testing]]
            [dumch.improve :refer [simplify wrap-nest]]
            [dumch.parse :refer [dart->clojure]]))

(deftest list->vector-test
  (is (= (simplify 
           '(:Column
              :children 
              '((:Padding :child (:Text "1"))))) 

         '(:Column
            :children
            [(:Padding :child (:Text "1"))]))))

(deftest nesting-test
  (testing "3 nested :child should be replaced with f/nest macro"
    (is 
      (= (wrap-nest
           '(:Container 
              :child 
              (:Box :child (:Padding :child (:Text "2"))))) 

         '(f/nest (:Container) (:Box) (:Padding) (:Text "2")))))

  (testing "2- nested :child should be unchanged"
    (is 
      (= (wrap-nest
           '(:Box :child (:Padding :child (:Text "2")))) 
         '(:Box :child (:Padding :child (:Text "2")))))))

(deftest flatten-same-operators
  (testing "calculation results"
    (is (-> "(2 + 1 + 1 == 4 && true) && (1 == 1 || false) && 3 == 3"
            dart->clojure
            simplify
            eval)))
  (testing "+, *, and, or"
    (doseq [sym ['+ '* 'or 'and]]
      (is (= (simplify `(~sym (~sym 1 (~sym 2 3) 4) 5))
             `(~sym 1 2 3 4 5)))))
  (testing "flatten compare operators when possible"
    (is (= (-> '(and (and (> 4 3) (> 3 2) (> 2 1)) true) simplify)
           '(and (> 4 3 2 1) true)))
    (is (= (-> '(and (>= 4 3) (>= 3 2) (>= 2 1) true) simplify)
           '(and (>= 4 3) (>= 3 2) (>= 2 1) true)))
    (is (= (-> '(and (> 2 1) (> 3 2) (> 4 3)) simplify)
           '(> 4 3 2 1)))))
