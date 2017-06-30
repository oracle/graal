![Sulong Logo](https://raw.githubusercontent.com/mrigger/sulong-logos/master/sulong_black_with_text_transparent_300x185.png)

Sulong (Graal LLVM) is an interpreter for LLVM IR written in
Java using the Truffle language implementation framework and Graal as a
just-in-time (JIT) compiler.

With Sulong you can execute C/C++, Fortran, and other programs written
in a LLVM language on the JVM. To execute a program by Sulong, you have
to compile the program to LLVM IR by a LLVM front end such as Clang. By
using Truffle and Java the interpreter implementation is simple and is
thus a great platform for experimentation. On the other hand, dynamic
optimizations and JIT compilation with Graal still provides native
execution speed (improving performance is work in progress). Through
Truffle's language interoperability capabilities, you will soon be able
to call functions from/to other languages on Truffle such as Ruby,
JavaScript, or R.

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

    tar -zxf labsjdk-8u111-jvmci-0.23-linux-amd64.tar.gz

Set `JAVA_HOME` to point to the extracted labsjdk from above:

    echo JAVA_HOME=`pwd`/labsjdk1.8.0_111-jvmci-0.23 > sulong/mx.sulong/env

Finally, build the project:

    cd sulong && mx build

The mx tool will ask you to choose between its server and jvmci
configuration. For now, just select server. You can read the differences
between the configurations on
[the Graal wiki](https://wiki.openjdk.java.net/display/Graal/Instructions). The first
build will take some time because mx has not only to build Sulong,
but also its dependencies and the Graal VM.

Now, Sulong is ready to start. You can for example compile a C file named
`test.c` (see further below) with mx and then use Sulong to execute it:

    mx su-clang -c -emit-llvm -o test.bc test.c
    mx su-run test.bc

For best experience we suggest to use clang 3.8, though versions 3.2, 3.3 and
3.8 to 4.0 should also work. Additionally, if you compile with the `-g` option
Sulong can provide source-file information in stacktraces.

Libraries to load can be specified using the `-l` flag, as in a compiler:

    mx su-run -lz test.bc

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

If you want to inspect the command line that mx generates for a mx
command you can use the -v flag.

Sulong Library Files
--------------------

You can package LLVM bitcode and a list of library dependencies using the
`su-link` linker command to create a `.su` file which is easy to manage and
distribute. You can also specify other libraries to load when this library
is loaded using the `-l` flag:

    mx su-link -o test.su -lz test.bc

You can run this `.su` file directly and it will know to load dependencies that
you specified at link-time:

    mx su-run test.su

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
linker, and backends then operate on the LLVM IR, to finally produce
machine code. LLVM envisions that transformations and analyses can be
applied during compile-time, link-time, runtime, and offline.

What is LLVM IR?
----------------

LLVM IR is a language that resembles assembler, but which provides
type-safety and has virtual registers that are in Static Single
Assignment (SSA) form.

Consider the following C program:

```C
#include <stdio.h>

int main() {
    printf("Hello World \n");
}
```

When compiling the C file with Clang to human readable LLVM IR with
`clang -O3 -emit-llvm -c -o test.bc test.c` and looking at the `test.ll`
file, one can see a LLVM IR program that looks similar to the following:

```
; ModuleID = 'test.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-
    i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-
    s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@.str = private unnamed_addr constant [14 x i8]
    c"Hello World \0A\00", align 1
@str = internal constant [13 x i8] c"Hello World \00"

define i32 @main() nounwind uwtable {
  %puts = tail call i32 @puts(i8* getelementptr inbounds
    ([13 x i8]* @str, i64 0, i64 0))
  ret i32 0
}

declare i32 @puts(i8* nocapture) nounwind
```

The file contains a `datalayout` and `triple` that specifies how data
should be laid out in memory, and which architecture should be targeted
in the backend. One can also see global the global variables `@.str` and
`@str`, the `@main` function as an entry to the program, and the
`@puts` function declaration that refers to the C standard library
`puts` function.

What is Truffle?
----------------

[Truffle](https://github.com/graalvm/graal/tree/master/truffle) is a language
implementation framework written in Java. It allows language designers
to implement a (guest) language as an Abstract Syntax Tree (AST)
interpreter. Additionally, Truffle provides many language independent
facilities to the host language such as profiling, debugging, and
language interoperability. When a Truffle AST is executed often and then
JIT-compiled with Graal, Graal can exploit its knowledge about the
Truffle framework and produce efficient machine code. Normally, the
Truffle implementation can also run on any other JVM.
However, Truffle LLVM relies on the Foreign Function Interface (FFI) of
Graal to provide native interoperability (e.g., to call the native
malloc) and thus has a direct dependency on Graal.

What is Graal?
-------------

[Graal](https://github.com/graalvm/graal/tree/master/compiler) is a JIT
compiler written in Java that receives Java bytecode as
an input and produces machine code. Currently, Graal is an alternative
to the C1 and C2 compilers of the HotSpot VM. The term GraalVM refers to
a HotSpot VM using Graal as its JIT compiler. Graal's focus is on
speculative optimizations, while it also provides an advanced partial
escape analysis.

Build Status
------------

Thanks to Travis CI, all commits of this repository are tested:
[![Build Status](https://travis-ci.org/graalvm/sulong.svg?branch=master)](https://travis-ci.org/graalvm/sulong)

Further Information
-------------------

The logo was designed by
[Valentina Caruso](https://www.behance.net/volantina).

Links:

* LLVM IR: [http://llvm.org/docs/LangRef.html](http://llvm.org/docs/LangRef.html)
* Instructions to build Graal:
    [https://wiki.openjdk.java.net/display/Graal/Instructions](https://wiki.openjdk.java.net/display/Graal/Instructions)
* Truffle and Graal publications, presentations, and videos:
    [https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations](https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations)
