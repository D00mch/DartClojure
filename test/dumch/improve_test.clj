(ns dumch.improve-test
  (:require [clojure.test :refer [deftest is testing]]
            [dumch.improve :refer [simplify wrap-nest]]
            [dumch.parse :refer [dart->clojure]]
            [rewrite-clj.zip :as z]
            [rewrite-clj.parser :as p]))

(deftest nesting-test
  (testing "3 nested :child should be replaced with f/nest macro"
    (is 
      (= (-> 
           "(Container 
              :child 
              (Box :child (Padding :child (Text \"2\"))))"
           z/of-string
           wrap-nest
           z/sexpr) 
         '(f/nest (Container) (Box) (Padding) (Text "2")))))

  (testing "2- nested :child should be unchanged"
    (is 
      (= (-> "(Box :child (Padding :child (Text \"2\")))"
             z/of-string
             wrap-nest
             z/sexpr) 
         '(Box :child (Padding :child (Text "2")))))))

(deftest redundant-do 
  (is (= (-> "(fn [a] (do a b))" p/parse-string simplify z/sexpr)
         '(fn [a] a b))))
