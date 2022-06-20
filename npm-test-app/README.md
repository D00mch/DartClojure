# DartCLojure NPM test app

Testing that we can consume the npm package.

Install the local npm package:

```
npm i ..
```

Start the REPL something like so:

```sh
npx shadow-cljs -d cider/cider-nrepl:0.27.4 watch :app
```

Build the app for release:

```sh
npm i dartclojure
npm run release
```
