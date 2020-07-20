# FAQ

## General

### How can I get started understanding Sulong?

A good starting point is to read and look through the
[papers and presentations about Sulong](PUBLICATIONS.md).
The paper [Sulong - Execution of LLVM-Based Languages on the JVM](http://2016.ecoop.org/event/icooolps-2016-sulong-execution-of-llvm-based-languages-on-the-jvm)
provides a minimal description of Sulong and describes its main goals.

To understand more about Truffle, on which Sulong is implemented, is to
read the papers [Self-optimizing AST interpreters](http://dl.acm.org/citation.cfm?id=2384587)
and [One VM to rule them all](http://dl.acm.org/citation.cfm?id=2509581).
A heavily documented Truffle language sample implementation is
[SimpleLanguage](https://github.com/graalvm/simplelanguage).

### From where does the project name originate?

Sulong is the romanization of the Chinese term "速龙" (Velocisaurus).
The first character translates as fast, rapid or quick, while the second
character means dragon. A literal translation of the name giving Chinese
term is thus "fast dragon". The name relates to the
[LLVM logo](http://llvm.org/Logo.html) which is a dragon (more
specifically a wyvern), and is also in line with the LLVM Dragonegg
project.

### What is Truffle?

[Truffle](../../../truffle) is a language implementation framework written
in Java. It allows language designers to implement a (guest) language as
an Abstract Syntax Tree (AST) interpreter. Additionally, Truffle provides
many language independent services to the host language such as a profiler,
a debugger, and language interoperability. If a Truffle AST is executed often,
it will be dynamically compiled by the [GraalVM compiler](../../../compiler).
The GraalVM compiler can exploit its knowledge about the Truffle framework and
produce efficient machine code.

### What is LLVM?

LLVM is an umbrella project for a modular and reusable compiler
infrastructure written in C++. It includes a compiler frontend `clang`
for compiling C, C++, Objective C and Objective C++ to LLVM bitcode IR,
other tools such as the optimizer `opt`, assembler, linker, and back-ends
that operate on the LLVM bitcode to finally produce machine code. LLVM
envisions that transformations and analyses can be applied during
compile-time, link-time, runtime, and offline.

## Mx

### How does mx execute a specific command?

To get a command line command you can use the `-v` mx option. For
example, `mx -v lli test.bc` prints the command line mx uses to run
the file `test.bc` with Sulong.

### What can I do when Java files got corrupted?

You can clean your workspace with `mx clean`. If you only want to clean
Java or native projects you can use `mx clean` with the options
`--no-native` or `--no-java`.

## IDEs

### How can I develop Sulong with Eclipse?

In order to work with Eclipse, use `mx eclipseinit` to generate the
Eclipse project files. Then import all projects into your workspace
(File>Import>General, then select "Existing Projects into Workspace",
then browse to your repository checkout).

In addition to the Sulong projects, you also have to import its
dependencies Truffle (from `workspace/graal/truffle`) and the GraalVM
SDK (from `workspace/graal/sdk`).

### How can I develop Sulong with Netbeans?

For generating Netbeans project files, use `mx netbeansinit`. Then
open the projects you are interested in (File>Open Project).

### How can I develop Sulong with IntelliJ?

IntelliJ project files can be generated with `mx intellijinit`.

### How can I debug Sulong with my IDE?

To debug the execution of a bitcode file in an IDE, first start an mx
command with the `-d` flag, e.g.:

```
$ mx -d lli test.ll
Listening for transport dt_socket at address: 8000
```

Then you can attach your IDE to the running Java process on port 8000.

See also [Debugging the GraalVM Runtime](DEBUGGING.md).
