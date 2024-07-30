# GraalWasm

GraalWasm is an open source WebAssembly runtime.
It runs WebAssembly programs in the binary format and can be used to embed and leverage WebAssembly modules in Java applications.

GraalWasm is a work in progress and is tracking the development of a number of WebAssembly extensions. 
Feedback, bug reports, and contributions are welcome.

## Building GraalWasm

### Prerequisites

- Python 3 (required by `mx`)
- GIT (to download, update, and locate repositories)
- JDK 11+
- emscripten or wasi-sdk for translating C files

### Building

To build GraalWasm, you need to follow the standard workflow for Graal projects.
We summarize the basic steps below:

1. Create a new folder where your repositories `mx` and `graal` should be located:
    ```bash
    $ mkdir graalvm
    $ cd graalvm
    ```

2. Clone `mx` and add it to the `PATH`:
    ```bash
    $ git clone https://github.com/graalvm/mx.git
    $ export PATH=$PWD/mx:$PATH
    ```

3. Clone the `graal` repository and enter the wasm directory:
    ```bash
    $ git clone https://github.com/oracle/graal.git
    $ cd graal/wasm
    ```

4. Set `JAVA_HOME`:
    ```bash
    $ export JAVA_HOME=[path to JDK]
    ```

5. Build the project:
    ```bash
    $ mx --dy /truffle,/compiler build
    ```

These steps will build the `wasm.jar` file in the `mxbuild/dists/jdk<version>` directory, which contains GraalWasm.

## Tests and Benchmarks

### Test Setup

The `build` command will also create the `wasm-tests.jar`, which contains the main test cases. To run these tests, the
WebAssembly binary toolkit is needed.

1. Download the binary of the [WebAssembly binary toolkit(wabt)](https://github.com/WebAssembly/wabt) and extract it.

2. Set `WABT_DIR`:
    ```bash
    $ export WABT_DIR=[path to wabt]/bin
    ```

### Run Basic Tests

After building GraalWasm, the `WasmTestSuite` can be run as follows:

```bash
$ mx --dy /truffle,/compiler --jdk jvmci unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm \
  -Dwasmtest.testFilter="^.*\$" \
  WasmTestSuite
```

To run a specific test, you can specify a regex for its name with the `testFilter` flag.
Here is an example command that runs all the tests that mention `branch` in their name:

```bash
$ mx --dy /truffle,/compiler --jdk jvmci unittest \
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

### Test Setup for Additional Tests and Benchmarks

The GraalWasm repository includes a set of additional tests and benchmarks
that are written in C, and are not part of the default build.
To compile these programs, you will need to install additional dependencies on your system.

To build these additional tests and benchmarks, you need to:

1. Install the [Emscripten SDK](https://emscripten.org/docs/getting_started/downloads.html). We currently test against  Emscripten **1.39.13**.
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

This will build several additional JARs in `mxbuild/dists/jdk<version>`:
`wasm-testcases.jar` and `wasm-benchmarkcases.jar`.
These JAR files contain `.wasm` files that correspond to the tests and the benchmarks
whose source code is in C.

### Run Additional Tests

You can run the additional tests as follows:

```bash
$ mx --dy /truffle,/compiler --jdk jvmci unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR/wat2wasm \
  -Dwasmtest.testFilter="^.*\$" \
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

### Run Benchmarks

The GraalWasm project includes a custom JMH-based benchmark suite,
which is capable of running WebAssembly benchmark programs.
The benchmark programs consist of several special functions,
most notably `benchmarkRun`, which runs the body of the benchmark.
The benchmarks are kept in the `src/com.oracle.truffle.wasm.benchcases` MX project.

For the benchmarks to run `NODE_DIR` has to be set. You can use the node version that is part of Emscripten, for example:

```bash
$ export NODE_DIR=[path to emsdk]/node/14.15.5_64bit/bin
```

After building the additional benchmarks, as described in the last section, they can be executed as follows:

```bash
$ mx --dy /compiler benchmark wasm:WASM_BENCHMARKCASES -- \
  -Dwasmbench.benchmarkName=[benchmark-name] -- \
  CMicroBenchmarkSuite
```

In the previous command, replace `[benchmark-name]` with the particular benchmark name,
for example, `cdf`.
This runs the JMH wrapper for the test, and produces an output similar to the following:

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

## Extracting the Internal GraalWasm Memory Layout Based on a Given WebAssembly Program

GraalWasm contains a tool for extracting the internal memory layout for a given WebAssembly application. This is useful
for detecting the causes of memory overhead.

To execute the memory layout extractor, run:

```bash
$ mx --dy /compiler wasm-memory-layout -Djol.magicFieldOffset=true -- [wasm-file]
```

This prints the memory layout tree of the given file to the console. The application provides additional options:

* --warmup-iterations: to set the number of warmup iterations.
* --entry-point: to set the entry point of the application. This is used to perform linking
* --output: to extract the memory layout into a file instead of the console.

You can also pass all other options available in GraalWasm such as `--wasm.Builtins=wasi_snapshot_preview1`.

The resulting tree represents a recursive representation of the Objects alive in GraalWasm starting from
the `WasmContext`. The output looks similar to this:

```
-context: 6598280 Byte [100%]
  -equivalenceClasses: 1320 Byte [0%]
    -table: 80 Byte [0%]
    -table[0]: 384 Byte [0%]
      -key: 72 Byte [0%]
        -paramTypes: 24 Byte [0%]
        -resultTypes: 24 Byte [0%]
      -next: 280 Byte [0%]
        -key: 64 Byte [0%]
          -paramTypes: 24 Byte [0%]
          -resultTypes: 16 Byte [0%]
        -next: 184 Byte [0%]
          -key: 56 Byte [0%]
            -paramTypes: 16 Byte [0%]
            -resultTypes: 16 Byte [0%]
          -next: 96 Byte [0%]
            -key: 64 Byte [0%]
              -paramTypes: 24 Byte [0%]
              -resultTypes: 16 Byte [0%]
    -table[2]: 208 Byte [0%]
      -key: 72 Byte [0%]
        -paramTypes: 24 Byte [0%]
        -resultTypes: 24 Byte [0%]
      -next: 104 Byte [0%]
...
```

The **names** represent the names of fields in classes. For example `equivalenceClasses` is a field in `WasmContext`.
The **values** next to the names represent the absolute amount of memory in bytes while the number in brackets represent
the relative contribution to the overall memory overhead.
**Names** with indices represent array entries such as `table[0]`.

## GraalWasm Standalone Distribution

You can run WebAssembly programs in the binary format on GraalWasm.
GraalWasm is also available as a standalone distribution.
You can download the distribution based on Oracle GraalVM or GraalVM Community Edition.

1. Download the distribution for your operating system:
   - Native standalone
      * [Linux x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.0.1-linux-amd64.tar.gz)
      * [Linux AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.0.1-linux-aarch64.tar.gz)
      * [macOS x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.0.1-macos-amd64.tar.gz)
      * [macOS AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.0.1-macos-aarch64.tar.gz)
      * [Windows x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.0.1-windows-amd64.zip)
   - JVM standalone
      * [Linux x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.0.1-linux-amd64.tar.gz)
      * [Linux AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.0.1-linux-aarch64.tar.gz)
      * [macOS x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.0.1-macos-amd64.tar.gz)
      * [macOS AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.0.1-macos-aarch64.tar.gz)
      * [Windows x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.0.1-windows-amd64.zip)

2. Unzip the archive:

    > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine <archive>.tar.gz
    ```

    Extract:
    ```shell
    tar -xzf <archive>.tar.gz
    ```
   
3. The standalone runtime comes with a JVM in addition to its native launcher. Check the version to see if it is active:
    ```bash
    ./path/to/bin/wasm --version
    ```

Now you have the launcher which can run WebAssembly programs directly.
For example, assuming you have the following C program:
```c
#include <stdio.h>

void floyd() {
    int number = 1;
    int rows = 10;
    for (int i = 1; i <= rows; i++) {
        for (int j = 1; j <= i; j++) {
            printf("%d ", number);
            ++number;
        }
        printf(".\n");
    }
}

int main() {
    floyd();
    return 0;
}
```

The `floyd` function is defined as separate and can be later exported.

1. Compile _floyd.c_ using the most recent version of the [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html):
    ```shell
    $ emcc -o floyd.wasm floyd.c
    ```
    It produces a standalone _floyd.wasm_ file in the current working directory.

2. Now you can run the compiled WebAssembly binary as follows:
    ```bash
    $ ./path/to/bin/wasm --Builtins=wasi_snapshot_preview1 floyd.wasm
    ```
    The option `--Builtins` specifies built-in modules that the Emscripten toolchain assumes.

## Embedding GraalWasm in Java

GraalWasm can be accessed programmatically with the [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), which allows embedding GraalWasm into user applications.

GraalVM SDK Polyglot API is not available by default, but can be easily added as a Maven dependency to your Java project.  
The GraalWasm artifact should be on the Java module or class path too.

Add the following set of dependencies to the project configuration file (_pom.xml_ in case of Maven).
  - To enable the polyglot runtime:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>polyglot</artifactId> 
        <version>${graalvm.polyglot.version}</version>
    </dependency>
    ```
  - To enable GraalWasm:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>wasm</artifactId> 
        <version>${graalvm.polyglot.version}</version>
    </dependency>
    ```
  - To enable the Truffle tools:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>tools</artifactId>
        <version>${graalvm.polyglot.version}</version>
    </dependency>
    ```

Now you can embed WebAssembly in a Java application. 
To learn more, see [Running WebAssembly Embedded in Java](https://www.graalvm.org/reference-manual/wasm/).

## Compiling C Files with WASI_SDK

1. Download the [`wasi-sdk`](https://github.com/WebAssembly/wasi-sdk/releases) and unpack it.

2. Set `WASI_SDK`:
   ```bash
   $ export WASI_SDK=[path to wasi-sdk]
   ```

3. Compile the C files:
   ```bash
   $ $WASI_SDK/bin/clang -O3 -o test.wasm test.c
   ```
   To export a specific function use the linker flag `-Wl,--export="[function name]"`.

4. Most applications compiled with the wasi-sdk require WASI. To run a file with WASI enabled use the following command:
   ```bash
   $ ./bin/wasm --Builtins=wasi_snapshot_preview1 test.wasm 
   ```

## License

GraalWasm is licensed under the [Universal Permissive License](https://oss.oracle.com/licenses/upl/).