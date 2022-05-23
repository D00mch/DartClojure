# dartclojure

App to convert dart code with flutter widgets into ClojureDart.

The intention is to simplify rewriting widgets from dart, not to 
fully convert any dart code. For most of the cases this utility 
generates decent enough code.

## Supported

- constructors invocation;
- static, factory methods invocation;
- named arguments;
- constants;
- simple ternary operators;
- lambdas arguments;

## Not supported

- lambdas body;
- expressions;
- complex ternary expressions;
- class declarations;
- methods declarations;

## Installation

Download from https://github.com/dumch/dartclojure

## Usage

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
