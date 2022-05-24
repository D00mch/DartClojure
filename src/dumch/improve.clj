(ns dumch.improve 
  (:require
    [clojure.zip :as z :refer [left right up down node seq-zip]]
    [clojure.walk :as walk]))

(defn- unplug-child [f]
  #_(println :unplug-child f)
  (let [child-i (.indexOf f :child)
        value-i (inc child-i)
        parent (keep-indexed #(when-not (#{child-i value-i} %1) %2) f)
        child (nth f value-i)]
    `(~parent ~child)))

(defn- nest-flatten [f]
  (loop [rslt []
         f f]
    (if (< (.indexOf f :child) 0)
      `(f/nest ~@rslt ~f)
      (let [[parent child] (unplug-child f)]
        (recur (conj rslt parent) child)))))

(defn- loc-child? [loc]
  (-> loc z/node (= :child)))

(defn- iter-depth [zipper]
  (->> (iterate z/next zipper)
       (take-while (complement z/end?))))

(defn- iter-right [zipper]
  (->> (iterate right zipper)
       (take-while (complement nil?))))

(defn- find-child-node [iter zipper]
  (->> (iter zipper)
       (drop-while #(not (loc-child? %)))
       first))

(defn- has-children? [n loc]
  (loop [n n, loc loc]
    #_(println :n n :ch (some-> loc node))
    (cond (and loc (= 0 n)) true
          (not (some-> loc node seq?)) false 
          :else 
          (recur (dec n) 
                 ;; go next level and nested child is right after :child name
                 (some->> loc down (find-child-node iter-right) right)))))

(defn lists->vectors [widget]
  (clojure.walk/postwalk 
    (fn [node]
      (if (and (seq? node) (some-> node first str (= "quote")))
        (apply vec (rest node))
        node))
    widget))

(defn wrap-nest [widget]
  (loop [loc (seq-zip widget)]
    (if (z/end? loc)
      (lists->vectors (node loc))
      (if (has-children? 3 loc)
        (recur (->> loc node nest-flatten (z/replace loc) z/next))
        (recur (z/next loc))))))

(comment 

  (def nested '(:Container 
                 :child 
                 (:Box :child (:Padding :child (:Text "2")))))

  (def widget `(:Column
                 :children 
                 ((:Padding :child (:Text "1"))
                  ~nested)))

  (wrap-nest widget)

  (->> nested seq-zip (has-children? 3))

  (->> widget seq-zip iter-depth 
       (filter #(has-children? 3 %)) 
       (map node))

  (->>
    '(m/Column 
      :children 
      '((m/Text "name") 
       (m/Icon m.Icons/widgets)
       (m/IgnorePointer.
         :ignoring (boolean @open)
         :child
         (m/AnimatedContainer.
           :transformAlignment m.Alignment/center
           :transform (m.Matrix4/diagonal3Values
                        (if @open 0.7 1.0)
                        (if @open 0.7 1.0)
                        1.0)
           :duration ^:const (m/Duration. :milliseconds 250)
           :curve ^:const (m/Interval. 0.0 0.5 :curve m.Curves/easeOut)
           :child
           (m/AnimatedOpacity.
             :opacity (if @open 0.0 1.0)
             :curve ^:const (m/Interval. 0.25 1.0 :curve m.Curves/easeInOut)
             :duration ^:const (m/Duration. :milliseconds 250)
             :child
             (m/FloatingActionButton.
               :onPressed toggle
               :child
               (m/Icon. m.Icons/create)))))))

    wrap-nest
    lists->vectors)
  )
