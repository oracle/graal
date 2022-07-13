---
layout: docs
toc_group: llvm
link_title: LLVM Languages Reference
permalink: /reference-manual/llvm/
---
# GraalVM LLVM Runtime

The GraalVM LLVM runtime can execute programming languages that can be transformed to LLVM bitcode.
This includes languages like C/C++, Fortran and others.

In contrast to static compilation that is normally used for LLVM-based languages, GraalVM's implementation of the `lli` tool first interprets LLVM bitcode and then dynamically compiles the hot parts of the program using the Graal compiler.
This allows seamless interoperability with the dynamic languages supported by GraalVM.

## Install LLVM Runtime

Since GraalVM 22.2, the LLVM runtime is packaged in a separate GraalVM component. It can be installed with GraalVM Updater:

```shell
$GRAALVM_HOME/bin/gu install llvm
```

This installs GraalVM's implementation of `lli` in the `$GRAALVM_HOME/bin` directory.
With the LLVM runtime installed, you can execute programs in LLVM bitcode format on GraalVM.

Additionally to installing the LLVM runtime, you can add the LLVM toolchain:

```shell
gu install llvm-toolchain
export LLVM_TOOLCHAIN=$(lli --print-toolchain-path)
```

Now you can compile C/C++ code to LLVM bitcode using `clang` shipped with GraalVM via a prebuilt LLVM toolchain.

## Run LLVM Bitcode on GraalVM

To run LLVM-based languages on GraalVM, the binaries need to be compiled with embedded bitcode.
The [Compiling](Compiling.md) guide provides information on how to compile a program to LLVM bitcode and what file format is expected.

The syntax to execute programs in LLVM bitcode format on GraalVM is:
```shell
lli [LLI options] [GraalVM options] [polyglot options] <bitcode file> [program args]
```

Here, `<bitcode file>` is [a compiled program with embedded LLVM bitcode](Compiling.md).
See [LLI Command Options](Options.md) or use `lli --help` for options explanations.

For example, put this C code into a file named `hello.c`:
```c
#include <stdio.h>

int main() {
    printf("Hello from GraalVM!\n");
    return 0;
}
```

Then compile `hello.c` to an executable `hello` with embedded LLVM bitcode and run it as follows:
```shell
$LLVM_TOOLCHAIN/clang hello.c -o hello
lli hello
```

Note: LLVM bitcode is platform-dependent.
The program must be compiled to bitcode for an appropriate platform.

## Further Reading

- [LLVM Compatibility](Compatibility.md)
- [Compiling to LLVM Bitcode](Compiling.md)
- [Debugging on the GraalVM LLVM Runtime](Debugging.md)
- [Interoperability with Other Languages](Interoperability.md)
- [Interaction of GraalVM with Native Code](NativeExecution.md)
- [LLI Command Options](Options.md)