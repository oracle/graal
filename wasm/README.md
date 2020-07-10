
# GraalWasm

GraalWasm is a WebAssembly engine implemented in the GraalVM.
It can interpret and compile WebAssembly programs in the binary format,
or be embedded into other programs.

We are working hard towards making GraalWasm more stable and more efficient,
as well as to implement various WebAssembly extensions.
Feedback, bug reports, and open-source contributions are welcome!


## Building GraalWasm

To build GraalWasm, you need to follow the standard workflow for Graal projects.
We summarize the basic steps below:

0. Download the Mx tool from [GitHub](https://github.com/graalvm/mx), and put the `mx` script to your `PATH`.
1. Clone GraalVM from [GitHub](https://github.com/oracle/graal).
2. Make sure that you have the latest [JVMCI-enabled JDK](https://github.com/graalvm/openjdk8-jvmci-builder).
3. Set `JAVA_HOME` to point to your JVMCI-enabled JDK.
4. In the `wasm` subdirectory of the GraalVM project, run:

```
$ mx --dy /truffle,/compiler build
```

These steps will build the `wasm.jar` file in `mxbuild/dists/jdk<version>` directory,
which contains the GraalWasm implementation.


## Running the tests

The `build` command will also create the `wasm-tests.jar`, which contains the main test cases.
After building GraalWasm, the tests can be run as follows:

1. Download the binary of the [WebAssembly binary toolkit](https://github.com/WebAssembly/wabt).
2. Set the `WABT_DIR` variable to the path to the root folder of the WebAssembly binary toolkit.
3. Run a specific test suite. The following command runs all the test suites:

```
mx --dy /truffle,/compiler --jdk jvmci unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR \
  -Dwasmtest.testFilter="^.*\$" \
  WasmTestSuite
```

4. To run a specific test, you can specify a regex for its name with the `testFilter` flag.
   Here is an example command that runs all the tests that mention `branch` in their name:

```
mx --dy /truffle,/compiler --jdk jvmci unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR \
  -Dwasmtest.testFilter="^.*branch.*\$" \
  WasmTestSuite
```

This command results with the following output:

```
--------------------------------------------------------------------------------
Running: BranchBlockSuite (4/16 tests - you have enabled filters)
--------------------------------------------------------------------------------
Using runtime: org.graalvm.compiler.truffle.runtime.hotspot.java.HotSpotTruffleRuntime@7b1d7fff
😍😍😍😍                                       
Finished running: BranchBlockSuite
🍀 4/4 Wasm tests passed.
```


## Building the additional tests and benchmarks

The GraalWasm repository includes a set of additional tests and benchmarks
that are written in C, and are not a part of the default build.
To compile these programs, you will need to install additional dependencies on your system.

To build these additional tests and benchmarks, you need to:

1. Install the [Emscripten SDK]( https://github.com/emscripten-core/emsdk).
   We currently test against Emscripten 1.39.13.
2. Set the `EMCC_DIR` variable to the `fastcomp/emscripten/` folder of the Emscripten SDK.
2. Set the `GCC_DIR` variable to the path to your GCC binary folder (usually `/usr/bin`).
3. Run the following Mx command:

```
$ mx --dy /truffle,/compiler build --all
```

This will build several additional JARs in `mxbuild/dists/jdk<version>`:
`wasm-testcases.jar` and `wasm-benchmarkcases.jar`.
These JAR files contain `.wasm` files that correspond to the tests and the benchmarks
whose source code is in C.

You can run the additional tests as follows:

```
mx --dy /truffle,/compiler --jdk jvmci unittest \
  -Dwasmtest.watToWasmExecutable=$WABT_DIR \
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


## Running the benchmarks

The GraalWasm project includes a custom JMH-based benchmark suite,
which is capable of running WebAssembly benchmark programs.
The benchmark programs consist of several special functions,
most notably `benchmarkRun`, which runs the body of the benchmark.
The benchmarks are kept in the `src/com.oracle.truffle.wasm.benchcases` Mx project.

After building the additional benchmarks, as described in the last section,
they can be executed as follows:

```
mx --dy /compiler benchmark wasm:WASM_BENCHMARKCASES -- \
  -Dwasmbench.benchmarkName=<-benchmark-name-> -- \
  CBenchmarkSuite
```

In the previous command, replace `<-benchmark-name->` with the particular benchmark name,
for example, `loop-posterize`.
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


## Running WebAssembly programs using a launcher

For the latest GraalWasm release, see
[the GraalVM dev builds page](https://github.com/graalvm/graalvm-ce-dev-builds/releases).
If downloading GraalWasm as a separate GraalVM component,
you can download it as follows (replace JDK and GraalVM versions with appropriate values):

```
# graalvm-ce-java8-19.3.0/bin/gu install --force -L wasm-installable-java8-linux-<version>.jar
```

This will install a launcher, which runs WebAssembly modules.
For example, assuming that compiled the following C program with Emscripten:

```
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

```
graalvm/bin/wasm --Builtins=memory,env:emscripten floyd.wasm
```

In this example, the flag `--Builtins` specifies built-in modules
that the Emscripten toolchain assumes.


## Embedding GraalWasm inside other programs

GraalWasm can be accessed programmatically with the Polyglot API,
which allows embedding GraalWasm into user programs.

Here is a simple example of how to run a WebAssembly program using GraalWasm
from a Java application:

```
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.ByteSequence;

byte[] binary = readBytes("example.wasm"); // You need to load the .wasm contents into a byte array.
Context.Builder contextBuilder = Context.newBuilder("wasm");
Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "example");
Source source = sourceBuilder.build();
Context context = contextBuilder.build();

context.eval(source);

Value mainFunction = context.getBindings("wasm").getMember("_main");
mainFunction.execute();
```

For more Polyglot-related examples, consult the documentation at the
[GraalVM website](https://www.graalvm.org/docs/reference-manual/polyglot/).

