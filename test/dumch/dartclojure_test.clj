(ns dumch.dartclojure-test
  (:require [clojure.test :refer :all]
            [dumch.dartclojure :refer :all]))

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

(deftest complex-example
  (is 
    (= 
      (-> "
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
          )),
)"
          dart->clojure)
      '(m/AnimatedContainer
         :transformAlignment m.Alignment/center
         :transform (.diagonal3Values
                      m/Matrix4
                      (if _isOpened 0.7 1.0)
                      (if _isOpened 0.7 1.0)
                      1.0)
         :duration (m/Duration :milliseconds 250)
         :curve (m/Interval 0.0 0.5 :curve m.Curves/easeOut)
         :child 
         (m/AnimatedRotation
           :turns (if _isOpened -0.1 0)
           :curve (m/Interval 0.25 1.0 :curve m.Curves/easeInOut)
           :duration (m/Duration :milliseconds 250)
           :child 
           (m/FloatingActionButton
             :onPressed (fn [] :unknown)
             :backgroundColor (if _isOpened m.Colors/white (.primaryColor theme))
             :child
             (m/Icon
               m.Icons/add :color
               (if _isOpened (.primaryColor theme) m.Colors/white))))))))
