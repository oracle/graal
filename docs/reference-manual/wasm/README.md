---
layout: docs-experimental
toc_group: wasm
link_title: GraalWasm 
permalink: /reference-manual/wasm/
---

# GraalWasm

GraalWasm is an open source WebAssembly runtime.
It runs WebAssembly programs in the binary format and can be used to embed and leverage WebAssembly modules in Java applications.
GraalWasm is under active development and is tracking a number of WebAssembly extensions.

## Running WebAssembly Embedded in Java

Compiled WebAssembly binary code can be accessed programmatically with [GraalVM SDK Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), which allows embedding WebAssembly into user applications. 

The example below demonstrates how to compile a C function to WebAssembly and run it embedded in a Java application. 
To run the demo, you need the following:
- [GraalVM JDK](https://www.graalvm.org/downloads/)
- [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html)
- [Maven](https://maven.apache.org/)

### Demo Part

1. Put the following C program in a file named _floyd.c_:
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
    Note that `floyd` is defined as a separate function and can be exported.

2. Compile the C code using the most recent version of the [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html):
    ```bash
    emcc --no-entry -s EXPORTED_FUNCTIONS=_floyd -o floyd.wasm floyd.c
    ```
    > The exported functions must be prefixed by `_`. If you reference that function in, for example, the Java code, the exported name should not contain the underscore.

    It produces a standalone file _floyd.wasm_ in the current working directory.

3. Add dependencies. The GraalVM SDK Polyglot API is not available by default, but can be easily added as a Maven dependency to your Java project.
The GraalWasm artifact should be on the Java module or classpath too. Add the following set of dependencies to the project configuration file (_pom.xml_ in case of Maven).

    - To enable the GraalVM polyglot runtime:
        ```xml
        <dependency>
            <groupId>org.graalvm.polyglot</groupId>
            <artifactId>polyglot</artifactId> 
            <version>${graalvm.polyglot.version}</version>
        </dependency>
        ```
    - To enable Wasm:
        ```xml
        <dependency>
            <groupId>org.graalvm.polyglot</groupId>
            <artifactId>wasm</artifactId> 
            <version>${graalvm.polyglot.version}</version>
            <type>pom</type>
        </dependency>
        ```

4. Now you can embed this WebAssembly function in a Java application, for example:

    ```java
    import org.graalvm.polyglot.*;
    import org.graalvm.polyglot.io.ByteSequence;

    // Load the WebAssembly contents into a byte array
    byte[] binary = Files.readAllBytes(Path.of("path", "to", "wasm", "file", "floyd.wasm"));

    // Setup context
    Context.Builder contextBuilder = Context.newBuilder("wasm").option("wasm.Builtins", "wasi_snapshot_preview1");
    Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "example");
    Source source = sourceBuilder.build();
    Context context = contextBuilder.build();

    // Evaluate the WebAssembly module
    context.eval(source);

    // Execute the floyd function
    context.getBindings("wasm").getMember("example").getMember("_initialize").executeVoid();
    Value mainFunction =context.getBindings("wasm").getMember("example").getMember("floyd");
    mainFunction.execute();
    context.close();
    ```

5. Compile and run this Java application with Maven as usual.

### Related Documentation

- [Embedding Languages documentation](../embedding/embed-languages.md)
- [GraalWasm](https://github.com/oracle/graal/tree/master/wasm)