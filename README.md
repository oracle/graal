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

The project bases on the LLVM IR parser of the
[llvm-ir-editor project](https://github.com/amishne/llvm-ir-editor)
by Alon Mishne.

External Dependencies
---------------------

Make sure you have GCC-4.6, G++-4.6, and GFortran-4.6 installed. For
a full list of external dependencies on Ubuntu you can look at our
[Travis configuration](https://github.com/graalvm/sulong/blob/master/.travis.yml).

On the Mac you can use Homebrew:

    brew tap homebrew/versions
    brew install gcc46 --with-fortran
    brew link --force gmp4

On some versions of Mac OS X, `gcc46` [may fail to install with a segmentation
fault](https://github.com/Homebrew/homebrew-versions/issues/515). A fix for this
is to `brew edit gcc46` and replace the patch `p0` with
[patch-10.10.diff](https://gist.githubusercontent.com/chrisseaton/7008085997269e3478e1/raw/46bff773d37914d6e66d0d8e1cab0d50d77e7280/patch-10.10.diff),
shasum `51814a0d79a9f21344c76f2d4235b59d9a4bc1601117e8ca5bfabdb82305aad0`.

However you install GCC on the Mac, you may then need to manually link the
gcc libraries we use into a location where they can be found, as
`DYLD_LIBRARY_PATH` cannot normally be set on the Mac.

    ln -s /usr/local/Cellar/gcc46/4.6.4/lib/gcc/4.6/libgfortran.3.dylib /usr/local/lib

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

    mx su-clang -S -emit-llvm -o test.ll test.c
    mx su-run test.ll

Libraries to load can be specified using the `-l` flag, as in a compiler:

    mx su-run -lz test.ll

If you want to use the project from within Eclipse, use the following
command to generate the Eclipse project files (there is also mx ideinit
for other IDEs):

    mx eclipseinit

If you want to inspect the command line that mx generates for a mx
command you can use the -v flag.

Sulong Library Files
--------------------

You can package LLVM bitcode and a list of library dependencies using the
`su-link` linker command to create a `.su` file which is easy to manage and
distribute. You can also specify other libraries to load when this library
is loaded using the `-l` flag:

    mx su-link -o test.su -lz test.ll

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
`clang -O3 -S -emit-llvm -o test.ll test.c` and looking at the `test.ll`
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

[Truffle](https://github.com/graalvm/truffle) is a language
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

Graal is a JIT compiler written in Java that receives Java bytecode as
an input and produces machine code. Currently, Graal is an alternative
to the C1 and C2 compilers of the HotSpot VM. The term GraalVM refers to
a HotSpot VM using Graal as its JIT compiler. Graal's focus is on
speculative optimizations, while it also provides an advanced partial
escape analysis.

How can I trace compilation?
----------------------------

You can enable textual notifications about compilations:

```
mx su-run <file> -Dgraal.TraceTruffleCompilation=true
```

To visualize Graal's graphs you can use the Ideal Graph Visualizer:

```
mx igv
mx su-run <file> -Dgraal.Dump=Truffle
```

Build Status
------------

Thanks to Travis CI, all commits of this repository are tested:
[![Build Status](https://travis-ci.org/graalvm/sulong.svg?branch=master)](https://travis-ci.org/graalvm/sulong)

Further Information
-------------------

The parser of the project bases on the LLVM IR editor plugin for Eclipse
by [Alon Mishne](https://github.com/amishne/llvm-ir-editor).

The logo was designed by
[Valentina Caruso](https://www.behance.net/volantina).

Links:

* LLVM IR: [http://llvm.org/docs/LangRef.html](http://llvm.org/docs/LangRef.html)
* Instructions to build Graal:
    [https://wiki.openjdk.java.net/display/Graal/Instructions](https://wiki.openjdk.java.net/display/Graal/Instructions)
* Truffle and Graal publications, presentations, and videos:
    [https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations](https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations)
