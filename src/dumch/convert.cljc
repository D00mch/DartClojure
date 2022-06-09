(ns dumch.convert
  (:require [dumch.improve :as improve]
            [dumch.parse :as parse]
            [instaparse.core :as insta]
            [rewrite-clj.zip :as z]
            [zprint.core :as zpr]))

(defn convert
  "get dart code, return clojure code
   pass keyword arguments, like: (convert code :format :sexpr, :material \"m\")
   :material — material alias name, default to \"m\";
   :flutter — alias for ClojureDart helper macro, default to \"f\";
   :format — or of :string (default), :sexpr, :zipper, :node; last 2 about rewrite-clj"
  [code & {m :material f :flutter frm :format :or {m "m", f "f"}}]
  (let [ast (parse/dart->ast code)
        bad? (insta/failure? ast)]
    (if bad?
      (str "Can't convert the code: " (:text ast))
      (let [rslt (improve/simplify (parse/ast->clj ast) :flutter f :material m)]
        (case frm
          :zipper rslt
          :sexpr (z/sexpr rslt)
          :node (z/node rslt)
          (-> rslt z/string (zpr/zprint-str {:parse-string? true})))))))

(comment
  (->
   "
    (context, index) {
      if (index == 0) {
        return const Padding(
          padding: EdgeInsets.only(left: 15, top: 16, bottom: 8),
          child: Text(
            'Some $field and ${Factory.create()}:',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w500,
            ),
          ),
        );
      }
      return const SongPlaceholderTile();
    };
    "
   (convert :format :sexpr)))