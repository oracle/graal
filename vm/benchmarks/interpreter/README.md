## WebAssembly
The sieve benchmark counts the number of primes below 600000.
The correct result is 49098.

To generate `wat` files from C sources, one has to use the `clang` compiler contained in the `wasi-sdk` and `wasm2wat` from WABT. Follow
the instructions in [the WASM README.](../../../wasm/README.md)

And then proceed by running the following commands:
```
$WASI_SDK/bin/clang -Wl,--export="run" -O3 -o richards.wasm richards.c
$WABT_DIR/wasm2wat -o richards.wat richards.wasm
```

In case of `sieve`, an additional linker flag must be passed to clang, since the default stack size (64kb) is to small.

```
$WASI_SDK/clang -Wl,-export="run" -Wl,-z,stack-size=4194304 -O3 -o sieve.wasm sieve.c
$WABT_DIR/wasm2wat -o sieve.wat sieve.wasm
```

## Espresso
Polybench can run Espresso (Java) benchmarks using a standalone .jar file provided with `--path`.
By default it will run the jar's main class, but the main class can be specified with `--class-name`.
New benchmarks can be added in `benchmarks/interpereter/java`, in a folder named as the fully qualified name of the main class. e.g. `benchmarks/interpereter/java/my.benchmark.Name`
This configuration will generate and include `Name.jar`, with `my.benchmark.Name` as main class in the `POLYBENCH_BENCHMARKS` distribution.

To list available benchmarks:
```bash
mx --env polybench-ce benchmark polybench --list
# or
ls `mx --env polybench-ce paths --output POLYBENCH_BENCHMARKS`/interpreter
```

To run the Espresso Sieve (Java) benchmarks (native-image):
```bash
`mx --env polybench-ce graalvm-home`/bin/polybench \
    --mode interpreter \
    --path `mx --env polybench-ce paths --output POLYBENCH_BENCHMARKS`/interpreter/Sieve.jar
```

Or via `mx benchmark` (HotSpot):
```bash
LD_DEBUG=unused mx --env polybench-ce benchmark polybench:interpreter/Sieve.jar -- --mode interpreter
```
