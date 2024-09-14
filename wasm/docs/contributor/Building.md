# Building GraalWasm

## Prerequisites

- Python 3 (required by `mx`)
- GIT (to download, update, and locate repositories)
- JDK 11+
- emscripten or wasi-sdk for translating C files

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
    $ mx --dy /truffle,/compiler build
    ```

These steps will build the `wasm.jar` file in the `mxbuild/dists/jdk<version>` directory, which contains GraalWasm.
