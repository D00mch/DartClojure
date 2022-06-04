# DartClojure

Opinionated dart to clojure converter for flutter widgets. 
It doesn't convert classs, methods, and assignments —
only the part with widget creation.

<img src="https://github.com/Liverm0r/DartClojure/blob/main/resources/Screenshot%202022-05-28%20at%2020.11.09.png" alt="alt text" width="632" height="302">

## Why is it  not a full dart->clojure converter?

Converted code would not be idiomatic. Instead of using classes there is 
a [widget][1] macro. 

Assignments are also useless, there would be a `:state` atom
and `swap!` or `reset!` functions for changing the state.

So I see little value in converting everything.

But rewriting widgets is a routine part and most of the time 
translates literally:

```dart 
Center(
  child: Text(
    _active ? 'Active' : 'Inactive',
    style: const TextStyle(fontSize: 32.0, color: Colors.white),
  ),
);
```

```clojure
(m/Center
 :child (m/Text
     (if _active "Active" "Inactive")
     :style (m/TextStyle :fontSize 32.0 :color m.Colors/white)))
```

3 more examples:

![Screen Recording 2022-05-29 at 19 38 44](https://user-images.githubusercontent.com/14236531/170881526-82983262-fd41-45e4-a90d-270859431890.gif)


## Supported

- constructors invocation;
- static, factory methods invocation;
- named arguments;
- (typed) lists, maps;
- math and logical expressions;
- ternary operators;
- lambdas;
- comments (will be removed);
- nesting children with f/nest macro;
- constants;
- variables in strings with$;
- raw (interpreted) strings like `r'some string $dollar'`

## Not supported

- class/methods declarations;
- bitwise operators;
- assignment (unideomatic);
- early exits from lambdas;
- `...` and `..` operators;
- proper aliases for everything, it's not possible to get this info generally;

## TODO:

- [ ] think on how to integrate it into the editors;
- [X] proper invocation (it's a dirty immoral crutch now, some chaing of dots like
`a.b().c.d()` wont work;
- [ ] handle early exit from lambdas with `return`;
- [X] support variables in string `"${a}, $b"`;
- [ ] support cascade `..`.
- [ ] do not insert material import on core classes, like `Duration`;

## How to use

Two things to note:

1. Copy-paste only widget's part, like `return Column(...),`, without method
declaration.
2. Typographical quotes `“`, `’` are not supported. 

There are two options now: 
1. jar; 
2. jvm repl.

### API with Jar

There are two ways to interact with the [jar][4]. First one is to run it each time:

```
$ java -jar dartclj.jar "Divider(        
    height: 0,
    color: Colors.grey,
)"       

(m/Divider :height 0 :color m.Colors/grey)
```

Second one is to run repl-like console (and send code into it with your editor/idea):
```
$ java -jar dartclj.jar -r true -m "material"
Paste dart code below, press enter and see the result:

Divider(        
    height: 0,
    color: Colors.grey,
)

(material/Divider :height 0 :color material.Colors/grey)
```

For example, you may start the repl-like console app with -e key: 
```
$ java -jar dartclj.jar -r true -e :end
```
And then send code directly from Idea with hotkeys with 
[Send To Terminal][3] plugin. [Example video](https://youtu.be/b5M-d_CYH6w)


For all the arguments see:
```bash
$ java -jar dartclj.jar -h
```

### API from JVM-repl

[Clojars][2].

Add Cli/deps:
```clojure
{:deps 
    {
     org.clojars.liverm0r/dartclojure {:mvn/version "0.1.7-SNAPSHOT"}
     }}
```

Or Leiningen/Boot: 
```clojure
[org.clojars.liverm0r/dartclojure "0.1.7-SNAPSHOT"]
```

Convert dart code (simplify and wrap-nest under the hood):
```clojure
(require '[dumch.dartclojure :refer [convert]])

(convert "1 + 1 + 2 * 1;" :format :sexpr) ; => (+ 1 1 (* 2 1))
```

You may pass aliases for material and flutter-macro:
```clojure
(convert "Text('1')" :material "m" :flutter "f") ; => "(m/Text "1")" 
```

If you just need to wrap clojure code with nest:
```clojure
(require
  '[dumch.improve :as impr]
  '[rewrite-clj.zip :as z])

(-> "(Container :child (Box :child (Padding :child (:Text \"2\"))))"
    z/of-string
    impr/wrap-nest
    z/sexpr)
; => (f/nest (Container) (Box) (Padding) (:Text "2"))
```

## Contribution

Build jar:
  
    $ clj -X:depstar

Run tests:

    $ clj -X:test

## License

Copyright © 2022 Artur Dumchev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[1]: https://github.com/Tensegritics/ClojureDart/blob/main/doc/flutter-helpers.md#widget-macro
[2]: https://clojars.org/org.clojars.liverm0r/dartclojure/versions/0.1.7-SNAPSHOT
[3]: https://plugins.jetbrains.com/plugin/9409-send-to-terminal
[4]: https://github.com/Liverm0r/DartClojure/releases/tag/0.1.7
