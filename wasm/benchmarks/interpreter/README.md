## Benchmark generation

The `wat` files in this folder were generated from the provided C sources (which are included for reproducibility). To re-generate the `wat` files, one has to use the `clang` compiler contained in the `wasi-sdk` and `wasm2wat` from WABT. Follow the instructions in [the WASM README.](../../README.md)

And then proceed by running the following commands:
```
$WASI_SDK/bin/clang -Wl,--export="run" -O3 -o richards.wasm richards.c
$WABT_DIR/wasm2wat -o richards.wat richards.wasm
```

In the case of `sieve`, an additional linker flag must be passed to clang, since the default stack size (64kb) is too small.
```
$WASI_SDK/bin/clang -Wl,-export="run" -Wl,-z,stack-size=4194304 -O3 -o sieve.wasm sieve.c
$WABT_DIR/wasm2wat -o sieve.wat sieve.wasm
```
