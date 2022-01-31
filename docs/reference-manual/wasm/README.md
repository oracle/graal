---
layout: docs-experimental
toc_group: wasm
link_title: WebAssembly Reference
permalink: /reference-manual/wasm/
---

# GraalVM Implementation of WebAssembly

GraalVM can run programs compiled to WebAssembly.
It can interpret and compile WebAssembly code in the binary format or embed it into other programs.
The support for WebAssembly is in the early stages of its development.

## Installing Wasm

The support is not available by default, but you can add it to GraalVM using the [GraalVM Updater](../graalvm-updater.md) tool:
```shell
gu install wasm
```
The above command will install a community version of a component from the GitHub catalog.
For GraalVM Enterprise users, the [manual component installation](../graalvm-updater.md#component-installation) is required.

Then `wasm` launcher, which can run compiled WebAssembly binary code, becomes available.

## Running WebAssembly Programs

You can run a program written in the language that compiles to WebAssembly on GraalVM.
For example, put the following C program in a file named _floyd.c_:
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

Compile it using the most recent [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html) version. It should produce a standalone _floyd.wasm_ file in the current working directory:
```shell
emcc -o floyd.wasm floyd.c
```

Then you can run the compiled WebAssembly binary on GraalVM as follows:
```shell
wasm --Builtins=wasi_snapshot_preview1 floyd.wasm
```

In this example, the flag `--Builtins` specifies builtin modules that the [Emscripten toolchain](https://emscripten.org/index.html) requires.

## Embedding WebAssembly Programs

The compiled WebAssembly binary code can be accessed programmatically with [GraalVM Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), which allows embedding GraalVM WebAssembly into user programs. Here is a simple example of how to call WebAssembly code from a Java application:

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.ByteSequence;
//Load the WASM contents into a byte array
byte[] binary = readBytes("example.wasm");
Context.Builder contextBuilder = Context.newBuilder("wasm");
Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binary), "example");
Source source = sourceBuilder.build();
Context context = contextBuilder.build();

context.eval(source);

Value mainFunction = context.getBindings("wasm").getMember("main").getMember("_start");
mainFunction.execute();
```

For more polyglot examples, visit the [Polyglot Programming](../polyglot-programming.md) page.
