# GraalWasm

GraalWasm is an open-source WebAssembly runtime compatible with the WebAssembly 1.0 specification.
It runs WebAssembly programs in binary format and can be used to embed and leverage WebAssembly modules in Java applications.

GraalWasm is in active development and implements a number of WebAssembly feature extensions.
Feedback, bug reports, and contributions are welcome.

## Embedding GraalWasm in Java

GraalWasm can be used via the [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), which allows embedding WebAssembly modules into Java applications.

GraalVM SDK Polyglot API can be easily added as a Maven dependency to your Java project.
The GraalWasm artifact should be on the Java module or class path too.

Add the following set of dependencies to the project configuration file (_pom.xml_ in the case of Maven).
- To add the Polyglot API:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>polyglot</artifactId>
        <version>${graalwasm.version}</version>
    </dependency>
    ```
- To add GraalWasm:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>wasm</artifactId>
        <version>${graalwasm.version}</version>
    </dependency>
    ```
- To add Truffle tools:
    ```xml
    <dependency>
        <groupId>org.graalvm.polyglot</groupId>
        <artifactId>tools</artifactId>
        <version>${graalwasm.version}</version>
    </dependency>
    ```

Now you can embed WebAssembly in your Java application.
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
    ```bash
    emcc --no-entry -s EXPORTED_FUNCTIONS=_floyd -o floyd.wasm floyd.c
    ```
   > The exported functions must be prefixed by `_`. If you reference that function in Java code, the exported name should not contain the underscore.

   It produces a standalone _floyd.wasm_ file in the current working directory.

2. Use the Polyglot API to load the WebAssembly module and access its exported functions.

    ```java
    try (Context context = Context.newBuilder("wasm").option("wasm.Builtins", "wasi_snapshot_preview1").build()) {
        // Evaluate the WebAssembly module
        Source source = Source.newBuilder("wasm", new File("path/to/floyd.wasm")).name("example").build();
        context.eval(source);

        // Initialize the module and execute the floyd function
        Value exampleModule = context.getBindings("wasm").getMember("example");
        exampleModule.getMember("_initialize").executeVoid();
        Value floydFunction = exampleModule.getMember("floyd");
        floydFunction.execute();
    }
    ```

## GraalWasm Standalone Distribution

GraalWasm is also available as a standalone distribution.

1. Download the distribution for your operating system:
   - Native standalone
      * [Linux x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.2.0-linux-amd64.tar.gz)
      * [Linux AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.2.0-linux-aarch64.tar.gz)
      * [macOS x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.2.0-macos-amd64.tar.gz)
      * [macOS AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.2.0-macos-aarch64.tar.gz)
      * [Windows x64](https://gds.oracle.com/download/wasm/archive/graalwasm-24.2.0-windows-amd64.zip)
   - JVM standalone
      * [Linux x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.2.0-linux-amd64.tar.gz)
      * [Linux AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.2.0-linux-aarch64.tar.gz)
      * [macOS x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.2.0-macos-amd64.tar.gz)
      * [macOS AArch64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.2.0-macos-aarch64.tar.gz)
      * [Windows x64](https://gds.oracle.com/download/wasm/archive/graalwasm-jvm-24.2.0-windows-amd64.zip)

2. Unzip the archive:

   > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine <archive>.tar.gz
    ```

   Extract:
    ```shell
    tar -xzf <archive>.tar.gz
    ```

3. The standalone runtime comes with a JVM in addition to its native launcher.
   Check the version to see if it is active:
    ```bash
    ./path/to/bin/wasm --version
    ```

Now you have the launcher which can run WebAssembly programs directly.

1. Compile _floyd.c_ from [the example above](#embedding-graalwasm-in-java) using the most recent version of the [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html):
    ```shell
    $ emcc -o floyd.wasm floyd.c
    ```
   It produces a standalone _floyd.wasm_ file in the current working directory.

2. Now you can run the compiled WebAssembly binary as follows:
    ```bash
    $ ./path/to/bin/wasm --Builtins=wasi_snapshot_preview1 floyd.wasm
    ```
   The option `--Builtins` specifies built-in modules that the Emscripten toolchain assumes.

## Compiling C Files with WASI_SDK

You can also use the WASI SDK toolchain to compile C programs into WebAssembly modules and run them on GraalWasm (either embedded through the Polyglot API or using the launcher).

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

### Documentation for Contributors

- [Building GraalWasm](docs/contributor/Building.md)
- [Testing and Benchmarking GraalWasm](docs/contributor/TestsAndBenchmarks.md)
- [Extracting the Internal GraalWasm Memory Layout Based on a Given WebAssembly Program](docs/contributor/MemoryLayout.md)
