(ns dumch.improve-test
  (:require [clojure.test :refer :all]
            [dumch.improve :refer :all]))

(deftest list->vector-test
  (is (= (lists->vectors 
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
