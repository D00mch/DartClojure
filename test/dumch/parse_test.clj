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
  (testing "column children typeless list"
    (is 
      (= (-> "Column(
                     children: [
                                const Text('name'),
                                Icon(Icons.widgets),
                                ])" 
             dart->clojure)
         '(m/Column :children '((m/Text "name") (m/Icon m.Icons/widgets)))))))

(deftest map-test
    (is 
      (= (dart->clojure "ListCell.icon(m: {1 : 2, 'a' : b, c : 'd'})")
         '(.icon m/ListCell :m {1 2, "a" b, c "d"}))))

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

; (deftest complex-example-test
;   (is 
;     (= 
;       (-> "
; AnimatedContainer(
;       transformAlignment: Alignment.center,
;       transform: Matrix4.diagonal3Values(
;         _isOpened ? 0.7 : 1.0,
;         _isOpened ? 0.7 : 1.0,
;         1.0,
;       ),
;       duration: const Duration(milliseconds: 250),
;       curve: const Interval(0.0, 0.5, curve: Curves.easeOut),
;       child: AnimatedRotation(
;           turns: _isOpened ? -0.1 : 0,
;           curve: const Interval(0.25, 1.0, curve: Curves.easeInOut),
;           duration: const Duration(milliseconds: 250),
;           child: FloatingActionButton(
;             onPressed: () => widget.closeHint.value = !widget.closeHint.value,
;             backgroundColor: _isOpened ? Colors.white : theme.primaryColor,
;             child: Icon(Icons.add, color: _isOpened ? theme.primaryColor : Colors.white),
;           )),
; )
; "
;           dart->clojure)
;       '(m/AnimatedContainer
;          :transformAlignment m.Alignment/center
;          :transform (.diagonal3Values
;                       m/Matrix4
;                       (if _isOpened 0.7 1.0)
;                       (if _isOpened 0.7 1.0)
;                       1.0)
;          :duration (m/Duration :milliseconds 250)
;          :curve (m/Interval 0.0 0.5 :curve m.Curves/easeOut)
;          :child 
;          (m/AnimatedRotation
;            :turns (if _isOpened -0.1 0)
;            :curve (m/Interval 0.25 1.0 :curve m.Curves/easeInOut)
;            :duration (m/Duration :milliseconds 250)
;            :child 
;            (m/FloatingActionButton
;              :onPressed (fn [] :unknown)
;              :backgroundColor (if _isOpened m.Colors/white (.primaryColor theme))
;              :child
;              (m/Icon
;                m.Icons/add :color
;                (if _isOpened (.primaryColor theme) m.Colors/white))))))))
