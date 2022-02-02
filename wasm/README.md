
# GraalWasm

GraalWasm is a WebAssembly engine implemented in GraalVM.
It can interpret and compile WebAssembly programs in the binary format,
or be embedded into other programs.

We are working hard towards making GraalWasm more stable and more efficient,
as well as to implement various WebAssembly extensions.
Feedback, bug reports, and open-source contributions are welcome!


## Building GraalWasm

### Prerequisites

- Python 3 (required by `mx`)
- GIT (to download, update, and locate repositories)
- A [JVMCI-enabled JDK 8](https://github.com/graalvm/graal-jvmci-8/releases) or a newer JDK version (JDK 9+)
- GCC for translating C files

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

These steps will build the `wasm.jar` file in the `mxbuild/dists/jdk<version>` directory,
which contains the GraalWasm implementation.


## Tests and Benchmarks

### Test setup

The `build` command will also create the `wasm-tests.jar`, which contains the main test cases. To run these tests, the WebAssembly binary toolkit is needed.

1. Download the binary of the [WebAssembly binary toolkit(wabt)](https://github.com/WebAssembly/wabt) and extract it.
2. Set `WABT_DIR`:

```bash
$ export WABT_DIR=[path to wabt]/bin
```

### Run basic tests

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
Using runtime: org.graalvm.compiler.truffle.runtime.hotspot.java.HotSpotTruffleRuntime@7b1d7fff
😍😍😍😍
Finished running: BranchBlockSuite
🍀 4/4 Wasm tests passed.
```

The `WasmTestSuite` is the aggregation of all basic tests.

### Test setup for additional tests and benchmarks

The GraalWasm repository includes a set of additional tests and benchmarks
that are written in C, and are not part of the default build.
To compile these programs, you will need to install additional dependencies on your system.

To build these additional tests and benchmarks, you need to:

1. Install the [Emscripten SDK](https://emscripten.org/docs/getting_started/downloads.html). We currently test against Emscripten **1.39.13**.

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

### Run additional tests

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
Using runtime: org.graalvm.compiler.truffle.runtime.hotspot.java.HotSpotTruffleRuntime@368239c8
😍
Finished running: CSuite
🍀 1/1 Wasm tests passed.
```

We currently have the following extra test suites:

- `CSuite` -- set of programs written in the C language
- `WatSuite` -- set of programs written in textual WebAssembly


### Run benchmarks

The GraalWasm project includes a custom JMH-based benchmark suite,
which is capable of running WebAssembly benchmark programs.
The benchmark programs consist of several special functions,
most notably `benchmarkRun`, which runs the body of the benchmark.
The benchmarks are kept in the `src/com.oracle.truffle.wasm.benchcases` MX project.

For the benchmarks to run `NODE_DIR` has to be set. You can use the node version that is part of Emscripten, for example:

```bash
$ export NODE_DIR=[path to emsdk]/node/14.15.5_64bit/bin
```

After building the additional benchmarks, as described in the last section,
they can be executed as follows:

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


## Running WebAssembly programs using a launcher

For the latest GraalWasm release, see
[the GraalVM dev builds page](https://github.com/graalvm/graalvm-ce-dev-builds/releases).
If downloading GraalWasm as a separate GraalVM component,
you can download it as follows (replace JDK and GraalVM versions with appropriate values):

```bash
$ graalvm-ce-java8-21.2.0/bin/gu install --force -L wasm-installable-java8-linux-<version>.jar
```

This will install a launcher, which runs WebAssembly modules.
For example, assuming that compiled the following C program with Emscripten:

```c
#include <stdio.h>

int main() {
  int number = 1;
  int rows = 10;
  for (int i = 1; i <= rows; i++) {
    for (int j = 1; j <= i; j++) {
      printf("%d ", number);
      ++number;
    }
    printf(".\n");
  }
  return 0;
}
```

You can run the compiled WebAssembly binary as follows:

```bash
$ graalvm/bin/wasm --Builtins=memory,env:emscripten floyd.wasm
```

In this example, the flag `--Builtins` specifies built-in modules
that the Emscripten toolchain assumes.


## Embedding GraalWasm inside other programs

GraalWasm can be accessed programmatically with the Polyglot API,
which allows embedding GraalWasm into user programs.

Here is a simple example of how to run a WebAssembly program using GraalWasm
from a Java application:

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.ByteSequence;

byte[] binary = readBytes("example.wasm"); // You need to load the .wasm contents into a byte array.
Context.Builder contextBuilder = Context.newBuilder("wasm");
Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "example");
Source source = sourceBuilder.build();
Context context = contextBuilder.build();

context.eval(source);

Value mainFunction = context.getBindings("wasm").getMember("example").getMember("_main");
mainFunction.execute();
```

For more polyglot-related examples, consult the documentation at the
[GraalVM website](https://www.graalvm.org/reference-manual/polyglot-programming/).
