(ns dumch.integration-test 
  (:require [clojure.test :refer [deftest is testing]]
            [dumch.dartclojure :refer [convert]]
            [dumch.improve :refer [simplify]]
            [dumch.parse :refer [dart->clojure widget-parser]]
            [instaparse.core :as insta]))

(def ^:private code "
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
          )
      )
);
")

(deftest complex-example-test
  (testing "no ambiguity"
    (is (= 1 (count (insta/parses widget-parser code)))))
  (testing "dart->clojure, using nest macro"
    (is 
      (= 
        (convert code)
        '(f/nest
           (m/AnimatedContainer
             :transformAlignment (.center m/Alignment) 
             :transform (.diagonal3Values
                          m/Matrix4
                          (if _isOpened 0.7 1.0)
                          (if _isOpened 0.7 1.0)
                          1.0)
             :duration (m/Duration :milliseconds 250)
             :curve (m/Interval 0.0 0.5 :curve (.easeOut m/Curves)))
           (m/AnimatedRotation
             :turns (if _isOpened -0.1 0)
             :curve (m/Interval 0.25 1.0 :curve (.easeInOut m/Curves))
             :duration (m/Duration :milliseconds 250))
           (m/FloatingActionButton
             :onPressed
             (fn [] :unidiomatic)
             :backgroundColor 
             (if _isOpened (.white m/Colors) (.primaryColor theme)))
           (m/Icon
             (.add m/Icons)
             :color (if _isOpened (.primaryColor theme) (.white m/Colors))))))))

(def code2 "
Column(
  children: [
    Question(
      questions[_questionIndex]['questionText'],
    ),
    (questions[_questionIndex]['answers'] as List<String>)
        .map((answer) {
      return Answer(_answerQuestion, answer);
    }).toList(),
    Numb(sorted: (1 < 2 && 2 < 3 && 3 < a) && (a == b)),
  ],
),
           ")

(deftest complex-example-test2
  (testing "no ambiguity"
    (is (= 1 (count (insta/parses widget-parser code2)))))
  (testing "dart->clojure, using nest macro"
    (is 
      (= 
        (-> code2 convert)
        '(m/Column
           :children
           [(m/Question ((questions _questionIndex) "questionText"))
            (->
              ((questions _questionIndex) "answers")
              (.map (fn [answer] (m/Answer _answerQuestion answer)))
              (.toList))
            (m/Numb :sorted (and (= a b) (< 1 2 3 a)))])))))
