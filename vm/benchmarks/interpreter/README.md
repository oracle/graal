The sieve benchmark counts the number of primes below 600000.
The correct result is 49098.

To generate `wat` files from C sources, one has to install `emcc` from emscripten and `wasm2wat` from WABT. Follow
the instructions in [the WASM README.](../../../wasm/README.md)

And then proceed by running the following commands:
```
emcc -s EXPORTED_FUNCTIONS='["_main", "_run"]' -o richards.wasm richards.c
wasm2wat -o richards.wat richards.wasm
```
