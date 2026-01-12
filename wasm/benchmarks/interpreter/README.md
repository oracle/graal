## Benchmark generation

The `wat` files in this folder were generated from the provided C sources (which are included for reproducibility). To re-generate the `wat` files, you can use the provided `regenerate_wat.sh` script. First, ensure that `WASI_SDK` and `WABT_DIR` are set in your environment, then run:
```
./regenerate_wat.sh
```
This script uses `clang` from `WASI_SDK` and `wasm2wat` from `WABT_DIR` to regenerate all `.wat` files from the C sources.

Follow the instructions in [the WASM README.](../../README.md)
