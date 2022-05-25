(ns dumch.integration-test 
  (:require [clojure.test :refer :all]
            [dumch.improve :refer :all]
            [dumch.parse :refer :all]))

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
          )),
)")

(deftest complex-example-test
  (testing "dart->clojure, using nest macro"
    (is 
      (= 
        (-> code dart->clojure wrap-nest)
        '(f/nest
           (m/AnimatedContainer
             :transformAlignment m.Alignment/center
             :transform (.diagonal3Values
                          m/Matrix4
                          (if _isOpened 0.7 1.0)
                          (if _isOpened 0.7 1.0)
                          1.0)
             :duration (m/Duration :milliseconds 250)
             :curve (m/Interval 0.0 0.5 :curve m.Curves/easeOut))
           (m/AnimatedRotation
             :turns (if _isOpened -0.1 0)
             :curve (m/Interval 0.25 1.0 :curve m.Curves/easeInOut)
             :duration (m/Duration :milliseconds 250))
           (m/FloatingActionButton
             :onPressed
             (fn []
               (set!
                 (-> widget .closeHint .value)
                 (not (-> widget .closeHint .value))))
             :backgroundColor (if _isOpened m.Colors/white (.primaryColor theme)))
           (m/Icon
             m.Icons/add
             :color (if _isOpened (.primaryColor theme) m.Colors/white)))))))
