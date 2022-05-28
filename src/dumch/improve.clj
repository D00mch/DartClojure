(ns dumch.improve 
  (:require
    [better-cond.core :refer [defnc-]]
    [clojure.zip :as z]
    [clojure.walk :as walk]))

(comment 
  ; After parse.clj converted dart to clojure, we may introduce some improvements;
  ; by using nest macro to eliminate wrap-hell, or flattern some forms.
  ; The data we are working with is clojure symbols, like
  '(+ a b)

  ; `loc` below means `location`
  )


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

(defn- iter-depth [zipper]
  (->> (iterate z/next zipper)
       (take-while (complement z/end?))))

(defn- iter-right [zipper]
  (->> (iterate z/right zipper)
       (take-while (complement nil?))))

(defn- find-node [iterfn node zipper]
  (->> (iterfn zipper)
       (drop-while #(not= (z/node %) node))
       first))

(defn- has-children? [times loc]
  (loop [n times, loc loc]
    #_(println :n n :ch (some-> loc z/node))
    (cond (and loc (= 0 n)) true
          (not (some-> loc z/node seq?)) false 
          :else 
          (recur (dec n) 
                 ;; go level down, find naved arg :child and take it's value
                 (some->> loc z/down (find-node iter-right :child) z/right)))))

(defn- flatten-same-node [[f & params]]
  `(~f ~@(mapcat #(if (and (seq? %) (= (first %) f)) 
                    (next %)
                    [%])
                 params)))

(defnc- flatten-same-for-and [[parent-fn & params :as node]]
  ;; (and (<= a b) (<= b c)) -> (<= a b c)
  ;; returns nil if not appropriate structure
  :when (= parent-fn 'and) 
  :when-let [only-seq-params (not (seq (drop-while seq? params)))
             proper-fn  (#{'> '< '>= '<=} (-> params first first))
             same-fn    (->> params (map first) (apply =))
             grouped-params (partition 2 1 params)
             fitargs? #(->> % 
                            (map (fn [[p1 p2]] (= (last p1) (second p2))))
                            (apply = true))]
  (fitargs? grouped-params) ;; like (> 4 3) (> 3 2)
  `(~(-> params first first) 
         ~@(->> params
                (mapcat #(if (seq? %) (next %) [%]))
                distinct))
  (fitargs? (map reverse grouped-params)) ;; like (> 3 2) (> 4 3)
  `(~(-> params first first) 
         ~@(->> params 
                reverse
                (mapcat #(if (seq? %) (next %) [%]))
                distinct)))

(defn wrap-nest [widget & {f :flutter :or {f "f"}}]
  (loop [loc (z/seq-zip widget)]
    (if (z/end? loc)
      (z/node loc)
      (if (has-children? 3 loc)
        (recur (->> loc z/node (nest-flatten f) (z/replace loc) z/next))
        (recur (z/next loc))))))

(defn simplify [form]
  (clojure.walk/postwalk 
    (fn [node]
      (cond (-> node seq? not) node
            (some-> node first (= 'quote)) (apply vec (rest node))
            (some-> node first (#{'+ '* 'or 'and })) 
            (flatten-same-node (or (flatten-same-for-and node) node))
            :else node))
    form))

(comment 
  (require '[flow-storm.api :as fs-api])
  (fs-api/local-connect)

  (def sum-form '(not= 
                   (and (and 1 4) (and 4 5))
                   (+ 1 (+ 2 3) 4 
                      (- (- 1 (- 3 (- 4) (- 3 4)) (- 2)) (* (* 3 2) 2 )))))

  (simplify '(and (and (> a b) (> b c)) (> c d)))

  (doseq [sym ['+ '* 'or 'and]]
    (= (simplify `(~sym (~sym 1 (~sym 2 3) 4) 5))
       `(~sym 1 2 3 4 5)))

  (let [sym '+]
    (simplify `(~sym (~sym 1 (~sym 2 3) 4) 5)))

  (def nested '(:Container 
                 :child 
                 (:Box :child (:Padding :child (:Text "2")))))

  (nest-flatten "f" nested)

  (def widget `(:Column
                 :children 
                 ((:Padding :child (:Text "1"))
                  ~nested)))

  (simplify (wrap-nest widget))

  (->> widget z/seq-zip iter-depth 
       (filter #(has-children? % 2)) 
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
             :opacity (if @open 0.0 (+ (+ 2.0 1.0) (+ -3.0) 1.0))
             :curve ^:const (m/Interval. 0.25 1.0 :curve m.Curves/easeInOut)
             :duration ^:const (m/Duration. :milliseconds 250)
             :child
             (m/FloatingActionButton.
               :onPressed toggle
               :child
               (m/Icon. m.Icons/create)))))))
    wrap-nest
    simplify)
  )
