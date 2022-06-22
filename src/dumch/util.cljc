(ns dumch.util
  (:require [rewrite-clj.node :as n]
            #?(:clj [clojure.java.io :as io]))
  #?(:cljs (:require-macros [dumch.util])))

#?(:clj (set! *warn-on-reflection* true))

(def ws (n/spaces 1))
(defn spaces [sq] (interpose ws sq))
(defn lists* [& args] (spaces (apply list* args)))
(defn maps [f sq] (spaces (map f sq)))
(defn mapcats [f sq] (spaces (mapcat f sq)))

(def nl (n/newlines 1))
(defn mapn [f sq] (interpose nl (map f sq)))
(defn mapcatn [f sq] (interpose nl (mapcat f sq)))

#?(:clj
   (defmacro inline-resource [resource-path]
     (slurp (clojure.java.io/resource resource-path))))

(defn upper-case? [s]
  #?(:clj (Character/isUpperCase ^Character s)
     :cljs (and (= s (.toUpperCase s))
                (not= s (.toLowerCase s)))))
