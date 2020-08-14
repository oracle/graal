# GraalVM LLVM Runtime

The GraalVM LLVM runtime can execute programming languages that can be transformed to LLVM bitcode. This includes languages like C/C++, Fortran and others.

In contrast to static compilation that is normally used for LLVM based
languages, GraalVM implementation of the `lli` tool first interprets the bitcode and then dynamically compiles the
hot parts of the program using the GraalVM compiler. This allows seamless
interoperability with the dynamic languages supported by GraalVM.

### Running LLVM bitcode programs on GraalVM

The syntax to execute programs in LLVM bitcode format with GraalVM is:
```
lli [LLI Options] [GraalVM Options] [Polyglot Options] <bitcode file> [program args]
```
where `<bitcode file>` is a [compiled program with embedded LLVM bitcode](Compiling.md). See [LLI Command Options](Options.md) or use `lli --help` for documentation of the options.

Note: LLVM bitcode is platform dependent. The program must be compiled to
bitcode for an appropriate platform.

### Compiling to LLVM bitcode format

GraalVM can execute C/C++, Fortran, and other programs that can be compiled to
LLVM bitcode. For that, GraalVM needs the binaries to be compiled with embedded
bitcode. The [Compiling](Compiling.md) guide provides information on the expected
file format and on compiling to bitcode.

### Quick Start
GraalVM comes with a pre-built LLVM toolchain for compiling C/C++ to LLVM bitcode.
It is used as follows:

```shell
# install the toolchain (only needed once)
$GRAALVM_HOME/bin/gu install llvm-toolchain
# get the path to the toolchain
export LLVM_TOOLCHAIN=$($GRAALVM_HOME/bin/lli --print-toolchain-path)
# compile a C file using the bundled `clang`
$TOOLCHAIN_PATH/clang example.c -o example
# run the result on GraalVM
$GRAALVM_HOME/bin/lli example
```

See the [Compiling](Compiling.md) guide for more details.
