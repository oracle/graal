![Sulong Logo](https://raw.githubusercontent.com/mrigger/sulong-logos/master/sulong_black_with_text_transparent_300x185.png)

Sulong is a high-performance LLVM bitcode interpreter build on the
GraalVM by [Oracle Labs](https://labs.oracle.com).

Sulong is written in Java and uses the Truffle language implementation
framework and Graal as a dynamic compiler.

With Sulong you can execute C/C++, Fortran, and other programs that can
be transformed to LLVM bitcode on Graal VM. To execute a program, you
have to compile the program to LLVM bitcode by a LLVM front end such
as `clang`.

Graal VM
--------

Sulong is part of the [Graal VM](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index.html).
Graal VM supports Linux or Mac OS X on x86 64-bit systems.

1. Download the [Graal VM](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index.html)
binaries.
2. Extract the archive to your file system.
3. Add the Graal VM `/bin` folder to your `PATH` environment variable.

To run programs in LLVM bitcode format on Graal VM, use:


    lli [LLI Options] [Graal VM Options] [Polyglot Options] filename.bc [program args]


Where `filename.bc` is a single program source file in LLVM bitcode format.
Graal VM executes the LLVM bitcode using Sulong as an interpreter.
Note: LLVM bitcode is platform dependent. The program must be compiled to
bitcode for the appropriate platform.

#### LLI Options

- `-L <path>` sets a path where lli searches for libraries. You can specify `-L` multiple times.

- `--lib <libraries>` adds external library sources (e.g. `--lib /path/to/libexample.so` or `--lib /path/to/example.bc`). These library sources are
precompiled native libraries or bitcode files. You can specify `--lib` multiple times.
*Note:* You must specify the library `example` with `--lib /path/to/libexample.so` as opposed to common linker `-l` options.

#### Graal VM Options

- `--jvm` executes the application in JVM mode instead of executing the
Graal VM native image.

- `--jvm.<option>` passes JVM options to Graal VM.
List available JVM options with `--jvm.help`.

- `--graal.<property>=<value>` passes settings to the Graal compiler.
For example, `--graal.DumpOnError=true` sends the compiler intermediate
representation (IR) to dump handlers if errors occur.

#### Polyglot Options

- `--polyglot` enables you to interoperate with other programming languages.

- `--<languageID>.<property>=<value>` passes properties to guest languages
through the Graal Polyglot SDK.

#### Compiling to LLVM bitcode format

Graal VM can execute C/C++, Fortran, and other programs that can be compiled to
LLVM bitcode. As a first step, you have to compile the program to LLVM bitcode
using an LLVM frontend such as `clang`. C/C++ code can be compiled to LLVM
bitcode using `clang` with the `-emit-llvm` option.

Let's compile `test.c`

```c
#include <stdio.h>

int main() {
  printf("Hello from Sulong!");
  return 0;
}
```

to an LLVM bitcode file `test.bc`.

    clang -c -emit-llvm -o test.bc test.c

You can then run `test.bc` on Graal VM as follows:

    lli test.bc

Build Dependencies
------------------

Sulong is mostly implemented in Java. However, parts of Sulong are
implemented in C/C++ and will be compiled to a shared library or a bitcode
file. For a successful build you need to have LLVM (incl. `CLANG` and `OPT`
tool) v3.2 - v4.0 installed. Sulong also depends on `libc++` and `libc++abi`
(on Ubuntu, install `libc++1`, `libc++abi1`, `libc++-dev`, `libc++abi-dev`).
For a full list of external dependencies on Ubuntu you can look at our
Travis configuration.

MacOS: Apple's default LLVM does not contain the `opt` tool, which a Sulong
build needs. We recommend installing LLVM via `homebrew` and appending the
bin path to the `PATH`.

How to get started?
-------------------

First create a new directory, which will contain the needed GraalVM
projects:

    mkdir sulong-dev && cd sulong-dev

Then, download mx, which is the build tool used by Sulong:

    git clone https://github.com/graalvm/mx
    export PATH=$PWD/mx:$PATH

Next, use git to clone the Sulong project and its dependencies:

    git clone https://github.com/graalvm/sulong

Next, you need to download a recent
[labsjdk](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html).
Extract it inside the `sulong-dev` directory:

    tar -zxf labsjdk-8u121-jvmci-0.29-linux-amd64.tar.gz

Set `JAVA_HOME` to point to the extracted labsjdk from above:

    echo JAVA_HOME=`pwd`/labsjdk1.8.0_121-jvmci-0.29 > sulong/mx.sulong/env

Sulong partially consists of C/C++ code that is compiled using `make`. To speed
up the build process you can edit the `MAKEFLAGS` environment variable:

    echo MAKEFLAGS=-j9 > sulong/mx.sulong/env

Finally, build the project:

    cd sulong && mx build

The first build will take some time because `mx` has not only to build Sulong,
but also its dependencies and primary testsuite.

Now, Sulong is ready to start. You can for example compile a C file named
`test.c` (see further below) with clang and then use Sulong to execute it:

    clang -c -emit-llvm -o test.bc test.c
    mx lli test.bc

For best experience we suggest to use clang 3.8, though versions 3.2, 3.3 and
3.8 to 4.0 should also work. Additionally, if you compile with the `-g` option
Sulong can provide source-file information in stacktraces.

You can specify additional libraries to load with the `-Dpolyglot.llvm.libraries`
option. These can be precompiled libraries (\*.so / \*.dylib) as well as LLVM bitcode
files. The `-Dpolyglot.llvm.libraryPath` option can be used to amend the search
path for the specifed libraries with a relative path. Both options can be given
multiple arguments separated by `:`.

    mx lli -Dpolyglot.llvm.libraryPath=lib -Dpolyglot.llvm.libraries=liba.so test.bc

If you want to use the project from within Eclipse, use the following
command to generate the Eclipse project files (there is also mx ideinit
for other IDEs):

    mx eclipseinit

If you want to use the project from within Intellij Idea, use the following
command instead:

    mx intellijinit

If you also want to edit the mx configuration files from within Idea, you can
append the `--mx-python-modules` argument to this. Since the configuration files
consist of Python code, you will probably want to install the
[Python Language Support Plugin](https://plugins.jetbrains.com/plugin/631-python).

If you want to inspect the command line that `mx` generates for a `mx`
command you can use the `-v` flag.

From where does the project name originate?
-------------------------------------------

Sulong is the romanization of the Chinese term "速龙" (Velocisaurus).
The first character translates as fast, rapid or quick, while the second
character means dragon. A literal translation of the name giving Chinese
term is thus "fast dragon". The name relates to the
[LLVM logo](http://llvm.org/Logo.html) which is a dragon (more
specifically a wyvern), and is also in line with the LLVM Dragonegg
project.

What is LLVM?
-------------

LLVM is an umbrella project for a modular and reusable compiler
infrastructure written in C++. It includes a compiler frontend `clang`
for compiling C, C++, Objective C and Objective C++ to LLVM bitcode IR.
Many of the other tools such as the optimizer `opt`, assembler,
linker, and backends then operate on the LLVM bitcode, to finally produce
machine code. LLVM envisions that transformations and analyses can be
applied during compile-time, link-time, runtime, and offline.


What is Truffle?
----------------

[Truffle](https://github.com/graalvm/graal/tree/master/truffle) is a language
implementation framework written in Java. It allows language designers
to implement a (guest) language as an Abstract Syntax Tree (AST)
interpreter. Additionally, Truffle provides many language independent
facilities to the host language such as profiling, debugging, and
language interoperability. When a Truffle AST is executed often and then
dynamically compiled with Graal, Graal can exploit its knowledge about the
Truffle framework and produce efficient machine code.

Build Status
------------

Thanks to Travis CI, all commits of this repository are tested:
[![Build Status](https://travis-ci.org/graalvm/sulong.svg?branch=master)](https://travis-ci.org/graalvm/sulong)

Further Information
-------------------

The logo was designed by
[Valentina Caruso](https://www.behance.net/volantina).

Sulong is developed in a research collaboration with
[Johannes Kepler University, Linz](www.ssw.jku.at).
