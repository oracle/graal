
How to build these benchmarks.

```
~/bin/emsdk/emscripten/emscripten-1.39.13/emcc -s EXPORTED_FUNCTIONS='["_main", "_run"]' \
  -o benchmarks/wasm/interpreter/sieve.wasm \
  benchmarks/wasm/interpreter/sieve.c
```
