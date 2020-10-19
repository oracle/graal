# GraalVM LLVM Runtime

GraalVM provides the LLVM runtime to execute C/C++, Rust, and other languages that can be transformed to
LLVM bitcode.

In contrast to static compilation that is normally used for LLVM-based languages, GraalVM's implementation of the `lli` tool first interprets the bitcode and then dynamically compiles the hot parts of the program using the GraalVM compiler.
This allows seamless interoperability with the dynamic languages supported by GraalVM.

## Running LLVM Bitcode on GraalVM

To run LLVM-based languages on GraalVM, the binaries need to be compiled with embedded
bitcode. The [Compiling](Compiling.md) guide provides information on
how to compile a program to LLVM bitcode and what file format is expected.

The syntax to execute programs in LLVM bitcode format on GraalVM is:
```shell
lli [LLI options] [GraalVM options] [polyglot options] <bitcode file> [program args]
```
Here, `<bitcode file>` is a compiled program with embedded LLVM bitcode. See [LLI Command Options](Options.md) or use `lli --help` for options exmplanations.

Note: LLVM bitcode is platform-dependent. The program must be compiled to
bitcode for an appropriate platform.
