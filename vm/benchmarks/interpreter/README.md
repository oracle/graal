## WebAssembly
The sieve benchmark counts the number of primes below 600000.
The correct result is 49098.

To generate `wat` files from C sources, one has to install `emcc` from emscripten and `wasm2wat` from WABT. Follow
the instructions in [the WASM README.](../../../wasm/README.md)

And then proceed by running the following commands:
```
emcc -s EXPORTED_FUNCTIONS='["_main", "_run"]' -o richards.wasm richards.c
wasm2wat -o richards.wat richards.wasm
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
