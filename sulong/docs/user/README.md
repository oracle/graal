# GraalVM LLVM Runtime

The GraalVM LLVM runtime can execute programming languages that can be transformed
to LLVM bitcode on GraalVM. This includes languages like C/C++, Fortran, and others.
To execute a program, you have to compile the program to LLVM bitcode with an LLVM
front end such as `clang`.

## Running LLVM bitcode programs on GraalVM

```
$GRAALVM_HOME/bin/lli [options] <bitcode file> [program args]
```

Where `<bitcode file>` is a compiled program with embedded LLVM bitcode.
Note: LLVM bitcode is platform dependent. The program must be compiled for
the appropriate platform.

## Compiling to LLVM bitcode format

GraalVM can execute C/C++, Fortran, and other programs that can be compiled to
LLVM bitcode. For that, GraalVM needs the binaries to be compiled with embedded
bitcode.

GraalVM comes with a pre-built LLVM toolchain for producing binaries with embedded
bitcode from C/C++ code. This can be installed with:

```
$GRAALVM_HOME/bin/gu install llvm-toolchain
```

The path to the LLVM toolchain can be found with:

```
$GRAALVM_HOME/bin/lli --print-toolchain-path
```

In the following we assume that the `TOOLCHAIN_PATH` environment variable is set
to this path.

As a first step, you have to compile the program to LLVM bitcode
using an LLVM frontend such as `clang`.

Let's compile `test.c`

```c
#include <stdio.h>

int main() {
  printf("Hello from Sulong!");
  return 0;
}
```

to a binary with embedded bitcode:

```
$TOOLCHAIN_PATH/clang test.c -o test
```

You can then run `test` on GraalVM as follows:

```
$GRAALVM_HOME/bin/lli test
```

## Debugging

GraalVM supports debugging programs with the Chrome Inspector. To enable debugging,
use the `lli --inspect` flag.

See [DEBUGGING](DEBUGGING.md) for more information.
