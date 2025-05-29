# Building GraalWasm

## Prerequisites

### Required dependencies

- Python 3 (required by `mx`)
- `git` (to download, update, and locate repositories)
- [JDK 21+](https://www.oracle.com/java/technologies/downloads/)

### Optional dependencies
- [WABT (WebAssembly Binary Toolkit)](https://github.com/WebAssembly/wabt) for translating _.wat_ files
- [Emscripten](https://emscripten.org/docs/getting_started/downloads.html) or [WASI SDK](https://github.com/WebAssembly/wasi-sdk) for translating C files

## Building

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
    $ mx --dynamicimports /compiler build
    ```

These steps will build the `wasm.jar` file in the `mxbuild/dists/jdk<version>` directory, which contains GraalWasm.

## Testing

To run a _.wasm_ file, you can use the following command:
    ```bash
    $ mx --dynamicimports /compiler wasm somefile.wasm
    ```

For instructions how to run the tests, see [Tests and Benchmarks](TestsAndBenchmarks.md).
