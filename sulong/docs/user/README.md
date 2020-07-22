# GraalVM LLVM Runtime

The GraalVM LLVM runtime can execute programming languages that can be transformed
to LLVM bitcode on GraalVM. This includes languages like C/C++, Fortran, and others.
To execute a program, you have to compile the program to LLVM bitcode with an LLVM
front end such as `clang`.

## Running LLVM bitcode programs on GraalVM

```
$GRAALVM_HOME/bin/lli [options] <bitcode file> [program args]
```

Where `<bitcode file>` is a [compiled program with embedded LLVM bitcode](COMPILING.md).
Note: LLVM bitcode is platform dependent. The program must be compiled for
the appropriate platform.

See [COMMANDLINE](COMMANDLINE.md) or use `lli --help` for documentation of the options.

## Compiling to LLVM bitcode format

GraalVM can execute C/C++, Fortran, and other programs that can be compiled to
LLVM bitcode. For that, GraalVM needs the binaries to be compiled with embedded
bitcode. The [COMPILING](COMPILING.md) document provides information on the expected
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

See [COMPILING](COMPILING.md) for more details.

## Debugging

GraalVM supports debugging programs with the Chrome Inspector. To enable debugging,
use the `lli --inspect` flag.

See [DEBUGGING](DEBUGGING.md) for more information.
