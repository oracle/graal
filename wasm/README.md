
# GraalWasm

GraalWasm is a WebAssembly engine implemented in the GraalVM.
It can interpret and compile WebAssembly programs in the binary format,
or be embedded into other programs.

GraalWasm is currently in EXPERIMENTAL state.
We are working hard towards making GraalWasm more stable and more efficient,
as well as to implement various WebAssembly extensions.
Open-source contributions are welcome!


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


## Running WebAssembly programs using a launcher

We are working on adding a launcher for GraalWasm, which will allow running binary WebAssembly programs
directly from the command line.
Stay tuned!


## Embedding GraalWasm inside other programs


## Running the benchmarks


## Building the additional tests and benchmarks

The GraalWasm repository includes a set of additional tests and benchmarks
that are written in C, and are not a part of the default build.
To compile these programs, you will need to install additional dependencies on your system.

To build these additional tests and benchmarks, you need to:

1. Install the [Emscripten SDK](https://github.com/emscripten-core/emscripten).
   We currently test against Emscripten 1.38.45.
2. Set the `EMCC_DIR` variable to the `fastcomp/emscripten/` folder of the Emscripten SDK.
3. Run the following Mx command:

```
$ mx --dy /truffle,/compiler build --all
```

