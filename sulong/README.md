![Sulong Logo](https://raw.githubusercontent.com/mrigger/sulong-logos/master/sulong_black_with_text_transparent_300x185.png)

Sulong is a high-performance LLVM bitcode interpreter built on the
GraalVM by [Oracle Labs](https://labs.oracle.com).

Sulong is written in Java and uses the Truffle language implementation
framework and Graal as a dynamic compiler.

With Sulong you can execute C/C++, Fortran, and other programming languages
that can be transformed to LLVM bitcode on GraalVM. To execute a program,
you have to compile the program to LLVM bitcode by a LLVM front end such
as `clang`.

GraalVM
-------

Sulong is part of the [GraalVM](http://www.graalvm.org).
GraalVM supports Linux or Mac OS X on x86 64-bit systems.

1. Download the [GraalVM](http://www.graalvm.org/downloads/) binaries.
2. Extract the archive to your file system.
3. Add the GraalVM `/bin` folder to your `PATH` environment variable.

To run programs in LLVM bitcode format on GraalVM, use:

    lli [LLI Options] [GraalVM Options] [Polyglot Options] file.bc [program args]

Where `file.bc` is a single program source file in LLVM bitcode format.
GraalVM executes the LLVM bitcode using Sulong as an interpreter.
Note: LLVM bitcode is platform dependent. The program must be compiled to
bitcode for the appropriate platform.

#### LLI Options

- `-L <path>` sets a path where lli searches for libraries. You can specify `-L`
multiple times.

- `--lib <libraries>` adds external library sources (e.g. `--lib /path/to/libexample.so`
or `--lib /path/to/example.bc`). These library sources are precompiled native libraries
or bitcode files. You can specify `--lib` multiple times. *Note:* You must specify
the library `example` with `--lib /path/to/libexample.so` as opposed to common linker
`-l` options.

#### GraalVM Options

- `--jvm` executes the application in JVM mode instead of executing the
GraalVM native image.

- `--vm.<option>` passes VM options to GraalVM.
List available JVM options with `--help:vm`.

- `--vm.Dgraal.<property>=<value>` passes settings to the GraalVM compiler.
For example, `--vm.Dgraal.DumpOnError=true` sends the compiler intermediate
representation (IR) to dump handlers if errors occur.

#### Polyglot Options

- `--polyglot` enables you to interoperate with other programming languages.

- `--<languageID>.<property>=<value>` passes properties to guest languages
through the Graal Polyglot SDK.

#### Compiling to LLVM bitcode format

GraalVM can execute C/C++, Fortran, and other programs that can be compiled to
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

    clang -O1 -c -emit-llvm -o test.bc test.c

You can then run `test.bc` on GraalVM as follows:

    lli test.bc

Note the `-O1` flag in the compile command. Compiling without optimizations is
not recommended with Sulong. In particular, cross-language interoperability
with Java or another Truffle language will not work when the bitcode is
compiled without optimizations.

##### Compiling C++

You need to add `-stdlib=libc++` when compiling C++ code in order to use
the right standard library.

    clang++ -O1 -c -emit-llvm -stdlib=libc++ -o test.bc test.cpp

Build Dependencies
------------------

Sulong is mostly implemented in Java. However, parts of Sulong are
implemented in C/C++ and will be compiled to a shared library or a bitcode
file. For a successful build you need to have LLVM (incl. `CLANG` and `OPT`
tool) in one of the supported versions (v3.8 - v7.0) installed. For best
experience we suggest to install either LLVM 4 or LLVM 6.

#### Linux
On a Linux-based operating system you can usually use its included package
manager to install a supported version. Note, however, that the LLVM that
is shipped with MacOS does not contain the `opt` tool, which a Sulong
build needs. On MacOS, we recommend installing LLVM via `homebrew` and
appending the bin path to the `PATH`.

#### MacOS
To install Clang and LLVM 4 on MacOS using `homebrew`, you can use the
following commands:

```bash
brew install llvm@4
export PATH="/usr/local/opt/llvm@4/bin:$PATH"
```

On macOS Mojave 10.14 and later, you may run into a build error like
this:
```
Building com.oracle.truffle.llvm.libraries.bitcode with GNU Make... [rebuild needed by GNU Make]
../graal/sulong/projects/com.oracle.truffle.llvm.libraries.bitcode/src/abort.c:30:10: fatal error: 'stdio.h' file not found
#include <stdio.h>
         ^~~~~~~~~
1 error generated.
make: *** [bin/abort.noopt.bc] Error 1

Building com.oracle.truffle.llvm.libraries.bitcode with GNU Make failed
1 build tasks failed
```

In this case, please install the macOS SDK headers with the following
commands:

```bash
xcode-select --install
open /Library/Developer/CommandLineTools/Packages/macOS_SDK_headers_for_macOS_10.14.pkg
```

Runtime Dependencies
--------------------

LLVM is only needed for compiling the bitcode files. For running compiled
bitcode files, there are no special runtime dependencies, but additional
libraries might be needed if the user code has external dependencies.

In particular, for running C++ code, you need libc++ (the C++ standard
library from the LLVM project).

How to get started?
-------------------

First create a new directory, which will contain the needed GraalVM
projects:

    mkdir sulong-dev && cd sulong-dev

Then, download mx, which is the build tool used by Sulong:

    git clone https://github.com/graalvm/mx
    export PATH=$PWD/mx:$PATH

Next, use git to clone the Sulong project and its dependencies:

    git clone https://github.com/oracle/graal

Next, you need to download a recent
[JVMCI-enabled JDK 8](https://github.com/graalvm/openjdk8-jvmci-builder/releases).
Extract it inside the `sulong-dev` directory:

    tar -zxf oraclejdk-8u212-jvmci-20-b01-linux-amd64.tar.gz

Set `JAVA_HOME` to point to the extracted JDK from above:

    echo JAVA_HOME=`pwd`/oraclejdk1.8.0_212-jvmci-20-b01 > graal/sulong/mx.sulong/env

Sulong partially consists of C/C++ code that is compiled using `make`. To speed
up the build process you can edit the `MAKEFLAGS` environment variable:

    echo MAKEFLAGS=-j9 >> graal/sulong/mx.sulong/env

Finally, build the project:

    cd graal/sulong && mx build

The first build will take some time because `mx` has not only to build Sulong,
but also its dependencies and primary testsuite.

Now, Sulong is ready to start. You can for example compile a C file named
`test.c` (see further below) with clang and then use Sulong to execute it:

    clang -c -emit-llvm -o test.bc test.c
    mx lli test.bc

For best experience we suggest to use clang 4.0 or 6.0, though all versions between
3.8 and 7.0 should also work. Additionally, if you compile with the `-g` option
Sulong can provide source-file information in stacktraces.

You can specify additional libraries to load with the `-Dpolyglot.llvm.libraries`
option. These can be precompiled libraries (\*.so / \*.dylib) as well as LLVM bitcode
files. The `-Dpolyglot.llvm.libraryPath` option can be used to amend the search
path for the specifed libraries with a relative path. Both options can be given
multiple arguments separated by `:`.

    mx lli -Dpolyglot.llvm.libraryPath=lib -Dpolyglot.llvm.libraries=liba.so test.bc

#### Running with the GraalVM compiler

In contrast to GraalVM, `mx lli` will by default  *not* optimize your program.
If you are interested in high performance, you might want to import the Graal
compiler. To do so, first ensure that the compiler is built:

    mx --dynamicimport /compiler build

Once the compiler is ready

    mx --dynamicimport /compiler --jdk jvmci lli test.bc

#### IDE Setup

If you want to use the project from within Eclipse, use the following
command to generate the Eclipse project files (there is also mx ideinit
for other IDEs):

    mx eclipseinit

If you want to use the project from within Intellij Idea, use the following
command instead:

    mx intellijinit

Since Sulong's configuration files for `mx` consist of Python code, you will
probably want to install the [Python Language Support Plugin](https://plugins.jetbrains.com/plugin/631-python).

You can also develop Sulong in Netbeans. The following command will generate the
project files and print instructions on how to import them into the IDE:

    mx netbeansinit

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
linker, and back-ends then operate on the LLVM bitcode, to finally produce
machine code. LLVM envisions that transformations and analyses can be
applied during compile-time, link-time, runtime, and offline.

What is Truffle?
----------------

[Truffle](https://github.com/oracle/graal/tree/master/truffle) is a language
implementation framework written in Java. It allows language designers
to implement a (guest) language as an Abstract Syntax Tree (AST)
interpreter. Additionally, Truffle provides many language independent
facilities to the host language such as profiling, debugging, and
language interoperability. When a Truffle AST is executed often and then
dynamically compiled with Graal, Graal can exploit its knowledge about the
Truffle framework and produce efficient machine code.

Further Information
-------------------

The logo was designed by
[Valentina Caruso](https://www.behance.net/volantina-).

Sulong is developed in a research collaboration with
[Johannes Kepler University, Linz](http://www.ssw.jku.at).
