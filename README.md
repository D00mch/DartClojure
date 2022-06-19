# DartClojure

Opinionated dart to clojure converter for flutter widgets. 
It doesn't (and probably will not) convert classes, setters, annotations —
only the part that could be reused after translation.

<img src="https://github.com/Liverm0r/DartClojure/blob/main/resources/Screenshot%202022-05-28%20at%2020.11.09.png?raw=true" alt="alt text" width="632" height="302">

## Why is it  not a full dart->clojure converter?

All the converted code would not be idiomatic. Instead of using 
classes there is a [widget][1] macro. 

Setters are also useless, there would be a `:state` atom
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

![Screen Recording 20.2.15-29 at 19 38 44](https://user-images.githubusercontent.com/14236531/170881526-82983262-fd41-45e4-a90d-270859431890.gif)


## Supported

- constructors invocation;
- static, factory methods invocation;
- named arguments;
- (typed) lists, maps, cascade operator (`..`);
- math and logical expressions;
- ternary operators;
- lambdas;
- comments (will be removed);
- nesting children with f/nest macro;
- constants;
- variables in strings with$;
- raw (interpreted) strings like `r'some string $dollar'`
- class/methods/fields declarations (not tested well, pre-alpha);
- try-catch;
- for in;
- switch with breaks and returns;

## Not supported

- bitwise operators;
- proper aliases for everything, it's not possible to get this info generally;
- enums;
- external keywork;
- yield;
- exports;
- switch with continue, withouth breaks or returns (considered unidiomatic);
- for with declare, conditions and increment (considered unidiomatic)
- while (considered unidiomatic)
- early exits from lambdas (ignored);
- `...` operator (ignored);
- typedefs (ignored);
- annotations (ignored);

## TODO:

- [X] support cljc to be able to work with Calva (thanks to [PEZ](https://github.com/PEZ)!);
- [X] proper invocation (it's a dirty immoral crutch now, some chaing of dots like
`a.b().c.d()` wont work;
- [X] support variables in string `"${a}, $b"`;
- [X] support cascade `..`.
- [X] do not insert material import on core classes, like `Duration`;
- [X] support for, while;
- [X] support switch;
- [X] support native artifacts
- [ ] test classes and methods extensively;
- [ ] convert files;
- [ ] handle early exit from lambdas/if/for/methods with `return`;

## How to use

There are 5 options now: 
1. Calva (VSCode);
2. Jvm/Js REPL;
3. Clojure Cli;
4. Jar. 
5. Native image
6. NPM CLI
7. NPM library

### API From Calva

[Calva][6] (a VSCode Clojure extension) packs DartClojure conversion into a command. This makes it easy to paste some Dart code and convert it.

<video src="https://user-images.githubusercontent.com/30010/173013020-b1a267b1-6839-4c0f-8ebb-c5826a4e5b80.mp4" data-canonical-src="https://user-images.githubusercontent.com/30010/173013020-b1a267b1-6839-4c0f-8ebb-c5826a4e5b80.mp4" controls="controls" muted="muted" class="" style="width: 100%;"></video>

See [calva clojuredart docs][5] for some (tiny) bit more on the subject.

### API from jvm/js REPL

[Clojars][2].

Add Cli/deps:
```clojure
{:deps 
    {
     org.clojars.liverm0r/dartclojure {:mvn/version "0.2.1-SNAPSHOT"}
     }}
```

Or Leiningen/Boot: 
```clojure
[org.clojars.liverm0r/dartclojure "0.2.1-SNAPSHOT"]
```

Convert dart code (simplify and wrap-nest under the hood):
```clojure
(require '[dumch.convert :refer [convert]])

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

### API from cli

```bash
clojure -Sdeps \
'{:deps {org.clojars.liverm0r/dartclojure {:mvn/version "0.2.1-SNAPSHOT"}}}' \
-e "(require '[dumch.convert :refer [convert]]) (convert \"Text('1')\" :material \"m\" :flutter \"f\")"
```

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

Colors are also supported:

![image](https://user-images.githubusercontent.com/14236531/172026319-0f770f8c-5a33-4703-91d0-37cbd3772700.png)

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

### API with native image:

Same api as with jar:

    ./dartclj -h

    ./dartclj "Text('a')" -m "m"

If there is no build for your architecture on [release page][4], 
see how to build it with graalvm below.


### API with npm CLI

Same api as with jar and native image:

```sh
npx dartclojure -h

npx dartclojure "Text('a')" -m "m"
```

### API with npm library

```js
const converter = require('dartclojure');

const clojureCode = converter.convert("Text('a')");
```

## Contribution

Build jar:
  
    $ clj -X:depstar

Run tests:

    $ clj -X:test ; clj -M:test-cljs

Run docker, build graalvm image:

    $ docker build --tag dartclojure .
    $ docker run -it dartclojure ./dartclj "Text('should work')"
    $ docker cp dc:/usr/src/app/dartclj . 

Compile native image with graalvm locally:

    # install graalvm
    $ chmod +x compile.sh 
    $ ./compile.sh

Remember to comment out line with `-H:+StaticExecutableWithDynamicLibC` for osx m1
builds.

Start shadow-cljs watcher and REPL:

```
npx shadow-cljs -d cider/cider-nrepl:0.28.3 watch :app :lib :test
```

(You can use this for developing both the Clojure and ClojureScript library, but the test watcher will only be watching and running the ClojureScript tests.)

Install the local npm package:

```
npm link dartclojure
```

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
[2]: https://clojars.org/org.clojars.liverm0r/dartclojure/versions/0.2.1-SNAPSHOT
[3]: https://plugins.jetbrains.com/plugin/9409-send-to-terminal
[4]: https://github.com/Liverm0r/DartClojure/releases/tag/0.2.1
[5]: https://calva.io/clojuredart/
[6]: https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva
