# Web Image

## Building

```bash
mx build
```

## Usage

Use this as you would regular the regular `native-image` tool, but with an
additional `--tool:svm-wasm` flag to enable the WebAssembly backend:

```bash
mx native-image --tool:svm-wasm -cp ... HelloWorld
```

This produces `helloworld.js` and `helloworld.js.wasm` in your working
directory. The JavaScript file is a wrapper that loads and runs the WebAssembly
code and can be run with [Node.js](https://nodejs.org/en) 22 or later:

The `--tool:svm-wasm` flag should be the first argument if possible. If any
experimental options specific to the Wasm backend are used, they can only be
added after the `--tool:svm-wasm` flag.

```bash
$ node helloworld.js
Hello World
```

## Contributors

- Aleksandar Prokopec
- Christoph Schobesberger
- David Leopoldseder
- Fengyun Liu
- Patrick Ziegler
