# Tests and Benchmarks

## Test Setup

Building GraalWasm using the `mx build` command will also create the `wasm-tests.jar`, which contains the main test cases.
To run these tests, the WebAssembly binary toolkit is needed.

1. Download the binary of the [WebAssembly binary toolkit(wabt)](https://github.com/WebAssembly/wabt) and extract it.

2. Set `WABT_DIR`:
    ```bash
    $ export WABT_DIR=[path to wabt]/bin
    ```

## Run Basic Tests

After building GraalWasm, the `WasmTestSuite` can be run as follows:

```bash
$ mx --dy /compiler unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm \
  WasmTestSuite
```

To run a specific test, you can specify a regex for its name with the `testFilter` flag.
Here is an example command that runs all the tests that mention `branch` in their name:

```bash
$ mx --dy /compiler unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm \
  -Dwasmtest.testFilter="^.*branch.*\$" \
  WasmTestSuite
```

This command results in the following output:

```
--------------------------------------------------------------------------------
Running: BranchBlockSuite (4/16 tests - you have enabled filters)
--------------------------------------------------------------------------------
Using runtime: com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime@7b1d7fff
😍😍😍😍
Finished running: BranchBlockSuite
🍀 4/4 Wasm tests passed.
```

The `WasmTestSuite` is the aggregation of all basic tests.

## Test Setup for Additional Tests and Benchmarks

The GraalWasm repository includes a set of additional tests and benchmarks that are written in C, and are not part of the default build.
To compile these programs, you will need to install additional dependencies on your system.

To build these additional tests and benchmarks, you need to:

1. Install the [Emscripten SDK](https://emscripten.org/docs/getting_started/downloads.html).
    We currently test against Emscripten **4.0.10**.
    ```bash
    $ cd [preferred emsdk install location]

    # Clone repository
    $ git clone https://github.com/emscripten-core/emsdk.git

    # Move to folder
    $ cd emsdk

    # Install sdk
    $ ./emsdk install [version number]

    # Activate sdk
    $ ./emsdk activate [version number]

    # Set up environment
    $ source ./emsdk_env.sh
    ```

2. Set `EMCC_DIR` and `GCC_DIR`:
    ```bash
    $ export EMCC_DIR=[path to emsdk]/upstream/emscripten
    $ export GCC_DIR=[path to gcc (usually /usr/bin)]
    ```

3. Run `emscripten-init`:
    ```bash
    $ cd grallvm/graal/wasm
    $ mx emscripten-init ~/.emscripten [path to emsdk] --local
    ```

4. Build with additional dependencies:
    ```bash
    $ mx --dy /truffle,/compiler build --all
    ```

This will build several additional JARs in `mxbuild/dists/jdk<version>`: `wasm-testcases.jar` and `wasm-benchmarkcases.jar`.
These JAR files contain `.wasm` files that correspond to the tests and the benchmarks whose source code is in C.

## Run Additional Tests

You can run the additional tests as follows:

```bash
$ mx --dy /compiler unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm \
  CSuite
```

This will result in the following output:

```
--------------------------------------------------------------------------------
Running: CSuite (1 tests)
--------------------------------------------------------------------------------
Using runtime: com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime@368239c8
😍
Finished running: CSuite
🍀 1/1 Wasm tests passed.
```

We currently have the following extra test suites:
- `CSuite` -- set of programs written in the C language
- `WatSuite` -- set of programs written in textual WebAssembly

## Run Benchmarks

The GraalWasm project includes a custom JMH-based benchmark suite, which is capable of running WebAssembly benchmark programs.
The benchmark programs consist of several special functions, most notably `benchmarkRun`, which runs the body of the benchmark.
The benchmarks are kept in the `src/com.oracle.truffle.wasm.benchcases` MX project.

For the benchmarks to run, `NODE_DIR` has to be set. You can use the node version that is part of Emscripten, for example:

```bash
$ export NODE_DIR=[path to emsdk]/node/22.16.0_64bit/bin
```

After building the additional benchmarks, as described in the last section, they can be executed as follows:

```bash
$ mx --dy /compiler benchmark wasm:WASM_BENCHMARKCASES -- \
  -Dwasmbench.benchmarkName=[benchmark-name] -- \
  CMicroBenchmarkSuite
```

In the previous command, replace `[benchmark-name]` with the particular benchmark name, for example, `cdf`.
This runs the JMH wrapper for the test, and produces output similar to the following:

```
# Warmup: 10 iterations, 10 s each
# Measurement: 10 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: com.oracle.truffle.wasm.benchcases.bench.CBenchmarkSuite.run

# Run progress: 0.00% complete, ETA 00:03:20
# Fork: 1 of 1
# Warmup Iteration   1: 0.123 ops/s
# Warmup Iteration   2: 0.298 ops/s
# Warmup Iteration   3: 0.707 ops/s
...
Iteration   9: 0.723 ops/s
Iteration  10: 0.736 ops/s


Result "com.oracle.truffle.wasm.benchcases.bench.CBenchmarkSuite.run":
  0.725 ±(99.9%) 0.012 ops/s [Average]
  (min, avg, max) = (0.711, 0.725, 0.736), stdev = 0.008
  CI (99.9%): [0.714, 0.737] (assumes normal distribution)


# Run complete. Total time: 00:03:47
```

We currently have the following benchmark suites:

- `CMicroBenchmarkSuite` -- set of programs written in C
- `WatBenchmarkSuite` -- set of programs written in textual WebAssembly
