# GraalVM LLVM Runtime

The GraalVM LLVM runtime can execute programming languages that can be transformed to LLVM bitcode. This includes languages like C/C++, Fortran and others.

In contrast to static compilation that is normally used for LLVM based
languages, GraalVM implementation of the `lli` tool first interprets the bitcode and then dynamically compiles the
hot parts of the program using the GraalVM compiler. This allows seamless
interoperability with the dynamic languages supported by GraalVM.

The syntax to execute programs in LLVM bitcode format with GraalVM is:
```
lli [LLI Options] [GraalVM Options] [Polyglot Options] filename [program args]
```
where `filename` is a single executable that contains LLVM bitcode.

Note: LLVM bitcode is platform dependent. The program must be compiled to
bitcode for an appropriate platform.
