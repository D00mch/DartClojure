(ns dumch.improve-test
  (:require [clojure.test :refer [deftest is testing]]
            [dumch.improve :refer [simplify wrap-nest]]
            [dumch.parse :refer [dart->clojure]]
            [rewrite-clj.zip :as z]
            [rewrite-clj.parser :as p]))

(def ^:private simplify-clj-str (comp z/sexpr simplify p/parse-string))

(deftest nesting-test
  (testing "3 nested .child should be replaced with f/nest macro"
    (is 
      (= '(f/widget (Container) (Box) (Padding) (Text "2")) 
         (-> 
           "(Container 
              .child 
              (Box .child (Padding .child (Text \"2\"))))"
           z/of-string
           wrap-nest
           z/sexpr))))

  (testing "2- nested .child should be unchanged"
    (is 
      (= '(Box .child (Padding .child (Text "2"))) 
         (-> "(Box .child (Padding .child (Text \"2\")))"
             z/of-string
             wrap-nest
             z/sexpr)))))

(deftest redundant-do 
  (testing "fn with do body"
    (is (= '(fn [a] a b)
           (simplify-clj-str "(fn [a] (do a b))"))))
  (testing "defn with do body"
    (is (= '(defn main [a] a b)
           (simplify-clj-str "(defn main [a] (do a b))")))))

(deftest print-test
  (is (= '(fn [a b] (when a a) (dart:core/print 1) (when c c) b)
         (simplify-clj-str 
           "(fn [a b] (do (when a a) (print 1) (when c c) b))"))))

(deftest material-alias-test
  (testing "No alias on core"
    (is (= '(Duration .milliseconds 250)
           (simplify-clj-str "(Duration .milliseconds 250)"))))
  (testing "material alias with slash"
    (is (= '(m/MyDuration .milliseconds 250)
           (simplify-clj-str "(MyDuration .milliseconds 250)"))))
  (testing "material alias with dot"
    (is (= '(m.My/Duration .milliseconds 250)
           (simplify-clj-str "(My.Duration .milliseconds 250)")))))
