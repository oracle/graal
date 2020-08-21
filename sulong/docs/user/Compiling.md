# Compiling to LLVM Bitcode

GraalVM can execute C/C++, Rust, and other languages that can be compiled to
LLVM bitcode. As a first step, you have to compile the program to LLVM bitcode
using an LLVM frontend such as `clang`.

### File Format
While the GraalVM LLVM runtime can execute [plain bitcode files](https://llvm.org/docs/BitCodeFormat.html),
the preferred format is a _native executable_ with _embedded bitcode_. The executable file formats differ on Linux and macOS.
Linux by default uses ELF files. The bitcode is stored in a section called `.llvmbc`.
The macOS platform uses Mach-O files. The bitcode is in the `__bundle` section of the `__LLVM` segment.

Using native executables with embedded bitcode offers two advantages over plain bitcode files.
First, build systems for native projects, for example a `Makefile`, expect the result to be an executable.
Embedding the bitcode instead of changing the output format improves compatibility with existing projects.
Second, executables allow specifying library dependencies which is not possible with LLVM bitcode.
The GraalVM LLVM runtime utilizes this information to find and load dependencies.

### LLVM Toolchain for compiling C/C++
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

The resulting executable `hello` can be executed with GraalVM using `lli`:
```shell
$GRAALVM_HOME/bin/lli hello
```

### External library dependencies
If the bitcode file depends on external libraries, GraalVM will automatically
pick up the dependencies from the binary headers. For example:
```c
#include <unistd.h>
#include <ncurses.h>

int main() {
    initscr();
    printw("Hello, Curses!");
    refresh();
    sleep(1);
    endwin();
    return 0;
}
```

This can be run with:
```shell
$LLVM_TOOLCHAIN/clang hello-curses.c -lncurses -o hello-curses
lli hello-curses
```

## Running C++

For running C++ code, the GraalVM LLVM runtime requires the
[`libc++`](https://libcxx.llvm.org) standard library from the LLVM project. The
LLVM toolchain shipped with GraalVM automatically links against `libc++`.

```c++
#include <iostream>

int main() {
    std::cout << "Hello, C++ World!" << std::endl;
}
```

Compile the code with `clang++`:

```shell
$LLVM_TOOLCHAIN/clang++ hello-c++.cpp -o hello-c++
lli hello-c++
Hello, C++ World!
```

## Running Rust

The LLVM toolchain that is bundled with GraalVM does not come with the Rust
compiler. To install Rust, run the following in your terminal, then follow the
onscreen instructions:
```shell
curl https://sh.rustup.rs -sSf | sh
```

Here is an example Rust program:

```
fn main() {
    println!("Hello Rust!");
}
```

This can be compiled to bitcode with the `--emit=llvm-bc` flag:
```shell
rustc --emit=llvm-bc hello-rust.rs
```

To run the Rust program, we have to tell GraalVM where to find the Rust
standard libraries.

```shell
lli --lib $(rustc --print sysroot)/lib/libstd-* hello-rust.bc
Hello Rust!
```
