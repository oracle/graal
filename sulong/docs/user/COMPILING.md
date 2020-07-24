# Compiling Native Projects to LLVM Bitcode

The GraalVM LLVM runtime executes LLVM bitcode. LLVM bitcode is a [binary format](https://llvm.org/docs/BitCodeFormat.html)
that is usually not written by hand. Instead, frontends compile a source language to bitcode.
For example, [clang](https://clang.llvm.org/) is a frontend that transforms C/C++ into bitcode.

## File Format

While the GraalVM LLVM runtime can execute [plain bitcode files](https://llvm.org/docs/BitCodeFormat.html),
the preferred format is a _native executable_ with _embedded bitcode_. The executable file formats differ on Linux and MacOS.
Linux by default uses ELF files. The bitcode is stored in a section called `.llvmbc`.
MacOS uses Mach-O files. The bitcode is in the `__bundle` section of the `__LLVM` segment.

Using native executables with embedded bitcode offers two advantages over plain bitcode files.
First, build systems for native projects, for example a `Makefile`, expect the result to be an executable.
Embedding the bitcode instead of changing the output format improves compatibility with existing projects.
Second, executables allow specifying library dependencies which is not possible with LLVM bitcode.
The GraalVM LLVM runtime utilizes this information to find and load dependencies. 

## Using the pre-built LLVM Toolchain for compiling C/C++

To simplify compiling C/C++ to executables with embedded bitcode, GraalVM comes with a pre-built LLVM toolchain.
The LLVM toolchain can be installed using the `gu` command:

```shell
$GRAALVM_HOME/bin/gu install llvm-toolchain
```

To get the location of the toolchain, use the `--print-toolchain-path` argument of `lli`:

```shell
export LLVM_TOOLCHAIN=$($GRAALVM_HOME/bin/lli --print-toolchain-path)
```

The toolchain contains compilers such as `clang` for C or `clang++` for C++, but also other tools that are needed
for building native projects such as a linker (`ld`), or an archiver (`ar`) for creating static libraries. See the content of
the toolchain path for a list of available tools:

```shell
ls $LLVM_TOOLCHAIN
```

Use those tools just as you would do for native compilation. For example, the C code file `hello.c`:

```c
#include <stdio.h>

int main() {
    printf("Hello from GraalVM!\n");
    return 0;
}
```

You can compile `hello.c` to an executable with embedded LLVM bitcode as follows:
```shell
$LLVM_TOOLCHAIN/clang hello.c -o hello
```

The resulting executable `hello` can be executed on GraalVM using `lli`:
```shell
$GRAALVM_HOME/bin/lli hello
```
## Where to continue?

There is a [blog post](https://medium.com/graalvm/graalvm-llvm-toolchain-f606f995bf) covering our LLVM toolchain which
contains more complex examples and background information on compiling to bitcode.
