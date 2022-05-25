(ns dumch.improve 
  (:require
    [clojure.zip :as z]
    [clojure.walk :as walk]))

(defn- unplug-child [f]
  #_(println :unplug-child f)
  (let [child-i (.indexOf f :child)
        value-i (inc child-i)
        parent (keep-indexed #(when-not (#{child-i value-i} %1) %2) f)
        child (nth f value-i)]
    `(~parent ~child)))

(defn- nest-flatten [flutter form]
  (loop [rslt []
         f form]
    (if (< (.indexOf f :child) 0)
      `(~(symbol (str flutter "/nest")) ~@rslt ~f)
      (let [[parent child] (unplug-child f)]
        (recur (conj rslt parent) child)))))

(defn- loc-child? [loc]
  (-> loc z/node (= :child)))

(defn- iter-depth [zipper]
  (->> (iterate z/next zipper)
       (take-while (complement z/end?))))

(defn- iter-right [zipper]
  (->> (iterate z/right zipper)
       (take-while (complement nil?))))

(defn- find-child-node [iter zipper]
  (->> (iter zipper)
       (drop-while #(not (loc-child? %)))
       first))

(defn- has-children? [n loc]
  (loop [n n, loc loc]
    #_(println :n n :ch (some-> loc node))
    (cond (and loc (= 0 n)) true
          (not (some-> loc z/node seq?)) false 
          :else 
          (recur (dec n) 
                 ;; go next level and nested child is right after :child name
                 (some->> loc z/down (find-child-node iter-right) z/right)))))

(defn lists->vectors [widget]
  (clojure.walk/postwalk 
    (fn [node]
      (if (and (seq? node) (some-> node first str (= "quote")))
        (apply vec (rest node))
        node))
    widget))

(defn wrap-nest [widget & {f :flutter :or {f "f"}}]
  (loop [loc (z/seq-zip widget)]
    (if (z/end? loc)
      (lists->vectors (z/node loc))
      (if (has-children? 3 loc)
        (recur (->> loc z/node (nest-flatten f) (z/replace loc) z/next))
        (recur (z/next loc))))))

(comment 

  (def nested '(:Container 
                 :child 
                 (:Box :child (:Padding :child (:Text "2")))))

  (nest-flatten "f" nested)

  (def widget `(:Column
                 :children 
                 ((:Padding :child (:Text "1"))
                  ~nested)))

  (lists->vectors (wrap-nest widget))

  (->> nested z/seq-zip (has-children? 3))

  (->> widget z/seq-zip iter-depth 
       (filter #(has-children? 3 %)) 
       (map z/node))

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
