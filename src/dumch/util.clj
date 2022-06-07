(ns dumch.util
  (:require [rewrite-clj.node :as n]))

(def ws (n/spaces 1))
(defn spaces [sq] (interpose ws sq))
(defn lists* [& args] (spaces (apply list* args)))
(defn maps [f sq] (spaces (map f sq)))
(defn mapcats [f sq] (spaces (mapcat f sq)))

(def nl (n/newlines 1))
(defn mapn [f sq] (interpose nl (map f sq)))

(defn with-print [arg]
  (println :with-print arg)
  arg)
