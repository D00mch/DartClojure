# DartClojure

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.liverm0r/dartclojure.svg)](https://clojars.org/org.clojars.liverm0r/dartclojure)

Opinionated Dart to Clojure converter for Flutter widgets.
It doesn't (and probably will not) convert classes, setters, annotations —
only the part that could be reused after translation.

<img src="https://github.com/d00mch/DartClojure/blob/main/images/Screenshot%202022-05-28%20at%2020.11.09.png?raw=true" alt="alt text" width="632" height="302">

## How to use

There are 9 options now:
1. [Calva (VSCode);](https://github.com/d00mch/DartClojure/#api-from-calva)
2. [Intellij Idea;](https://github.com/d00mch/DartClojure/#api-from-intellij-idea)
3. [Jvm/Js REPL;](https://github.com/d00mch/DartClojure/#api-from-jvmjs-repl)
4. [Clojure Cli;](https://github.com/d00mch/DartClojure/#api-from-cli)
5. [Jar;](https://github.com/d00mch/DartClojure/#api-with-jar)
6. [Native image;](https://github.com/d00mch/DartClojure/#api-with-native-image)
7. [Native image, emacs;](https://github.com/D00mch/DartClojure#api-with-native-image-via-emacs)
8. [NPM CLI;](https://github.com/d00mch/DartClojure/#api-with-npm-cli)
9. [NPM library;](https://github.com/d00mch/DartClojure/#api-with-npm-library)

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
 .child (m/Text
     (if _active "Active" "Inactive")
     .style (m/TextStyle .fontSize 32.0 .color m.Colors/white)))
```

3 more examples:

![Screen Recording](https://user-images.githubusercontent.com/14236531/170881526-82983262-fd41-45e4-a90d-270859431890.gif)


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

- [ ] test classes and methods extensively;
- [ ] handle early exit from lambdas/if/for/methods with `return`;


## How to use

### API from Calva

[Calva][6] (a VSCode Clojure extension) packs DartClojure conversion into a command. This makes it easy to paste some Dart code and convert it.

<video src="https://user-images.githubusercontent.com/30010/173013020-b1a267b1-6839-4c0f-8ebb-c5826a4e5b80.mp4" data-canonical-src="https://user-images.githubusercontent.com/30010/173013020-b1a267b1-6839-4c0f-8ebb-c5826a4e5b80.mp4" controls="controls" muted="muted" class="" style="width: 100%;"></video>

See [calva clojuredart docs][5] for some (tiny) bit more on the subject.

### API from Intellij Idea 

You could use available [api](https://github.com/d00mch/DartClojure/#how-to-use) (4, 5, 6, 7) directly from Idea with [External Tools][3].

Preferences —> Tools —> External Tools —> +  

`Program`: any terminal command. For example, if you have npm, install
dartclojure globally and put `dartclojure` in the `Program` field. If you
downloaded native image, put path to native image here:
`/Users/PcName/Downloads/dartclojure-aarch64-darwin`

`Arguments`: `"$SelectedText$"` 

![image](https://user-images.githubusercontent.com/14236531/175103257-cd2894ab-e20d-4990-9469-c4280ac228ed.png)

Thats it. Select code, press `shift + shift` (or `cmd + shift + a`), type
`DartClojure` and press enter (or setup hotkey on it). Little [video][7] how it
looks.
 
### API from jvm/js REPL

[Clojars][2].

Add Cli/deps:
```clojure
{:deps
    {
     org.clojars.liverm0r/dartclojure {:mvn/version "0.2.23-SNAPSHOT"}
     }}
```

Or Leiningen/Boot:
```clojure
[org.clojars.liverm0r/dartclojure "0.2.23-SNAPSHOT"]
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

(-> "(Container .child (Box .child (Padding .child (:Text \"2\"))))"
    z/of-string
    impr/wrap-nest
    z/sexpr)
; => (f/nest (Container) (Box) (Padding) (:Text "2"))
```

### API from cli

```bash
clojure -Sdeps \
'{:deps {org.clojars.liverm0r/dartclojure {:mvn/version "0.2.23-SNAPSHOT"}}}' \
-e "(require '[dumch.convert :refer [convert]]) (convert \"Text('1')\" :material \"m\" :flutter \"f\")"
```

### API with Jar

There are two ways to interact with the [jar][4]. First one is to run it each time:

```
$ java -jar dartclojure.jar "Divider(
    height: 0,
    color: Colors.grey,
)"

(m/Divider .height 0 .color m.Colors/grey)
```

Second one is to run repl-like console (and send code into it with your editor/idea):
```
$ java -jar dartclojure.jar -r true -m "material"
Paste dart code below, press enter and see the result:

Divider(
    height: 0,
    color: Colors.grey,
)

(material/Divider .height 0 .color material.Colors/grey)
```

Colors are also supported:

![image](https://user-images.githubusercontent.com/14236531/172026319-0f770f8c-5a33-4703-91d0-37cbd3772700.png)

For example, you may start the repl-like console app with -e key:
```
$ java -jar dartclojure.jar -r true -e :end
```

For all the arguments see:
```bash
$ java -jar dartclojure.jar -h
```

### API with native image:

Same api as with jar:

    ./dartclojure -h

    ./dartclojure "Text('a')" -m "m"

    ./dartclojure --path main.dart

    ./dartclojure -r true

If there is no build for your architecture on [release page][4],
see how to build it with graalvm below.

### API with native image via Emacs

You can use the Emacs package from [dartclojure.el](https://github.com/burinc/dartclojure.el) via your favorite Emacs package manager.

### API with npm CLI

Same api as with jar and native image:

```sh
npm i dartclojure

npx dartclojure -h

npx dartclojure "Text('a')" -m "m"
```

You can of course also install globally:

```sh
npm i -g dartclojure

dartclojure -h

dartclojure "Text('a')" -m "m"
```

### API with npm library

```js
const converter = require('dartclojure');

const clojureCode = converter.convert("Text('a')");
```

## Contribution

Build jar:

    $ clj -T:build uber :version '"0.2.22"'

Run tests:

    $ clj -X:test ; clj -M:test-cljs

Run docker, build graalvm image:

    $ cp target/dartclojure*.jar dartclojure.jar 
    $ docker build --pull --no-cache --tag dartclojure .
    $ docker run --name dc -it dartclojure ./dartclojure "Text('should work')"
    $ docker cp dc:/usr/src/app/dartclojure.

Compile native image with graalvm locally:

    # install graalvm
    $ chmod +x compile.sh
    $ ./compile.sh

Remember to comment out line with `-H:+StaticExecutableWithDynamicLibC` for osx m1
builds.

Start shadow-cljs watcher and REPL:

```
clj -M:shadow-cljs watch :app :lib :test
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
[2]: https://clojars.org/org.clojars.liverm0r/dartclojure/versions/0.2.23-SNAPSHOT
[3]: https://www.jetbrains.com/help/idea/configuring-third-party-tools.html
[4]: https://github.com/d00mch/DartClojure/releases/tag/0.2.23
[5]: https://calva.io/clojuredart/
[6]: https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva
[7]: https://www.reddit.com/r/Clojure/comments/vib5ie/how_to_translate_dart_to_clojuredart_inside/
