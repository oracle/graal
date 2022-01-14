---
layout: docs
toc_group: reference-manual
link_title: Graal Compiler
permalink: /reference-manual/compiler/
---

# Graal Compiler

* [Compiler Advantages](#compiler-advantages)
* [Graph Compilation](#graph-compilation)
* [Ahead-of-time Compilation](#ahead-of-time-compilation)
* [Compiler Operating Modes](#compiler-operating-modes)
* [Diagnostic Data](#diagnostic-data)

The Graal compiler is a dynamic just-in-time (JIT) compiler, written in Java, that transforms bytecode into machine code.
The Graal compiler integrates with the Java HotSpot VM, which supports a compatible version of the JVM Compiler Interface (JVMCI).
JVMCI is a privileged, low-level interface to the JVM, enabling a compiler written in Java to be used by the JVM as a dynamic compiler (see [JEP 243](https://openjdk.java.net/jeps/243)).
It can read metadata from the VM, such as method bytecode, and install machine code into the VM .
GraalVM includes a version of the HotSpot JVM that supports JVMCI.

## Compiler Advantages

The Graal compiler provides optimized performance for programs running on the JVM through unique approaches to code analysis and optimization.
It includes multiple optimization algorithms (called “Phases”), like aggressive inlining, polymorphic inlining, and others.
For example, the compiler in GraalVM Enterprise includes 62 optimization phases, of which 27 are patented.

The Graal compiler assures performance advantages for highly-abstracted programs due to its ability to remove costly object allocations.
Code using more abstraction and modern Java features like Streams or Lambdas will see greater speedups.
Low-level code or code that converges to things like I/O, memory allocation, or garbage collection will see less improvement.
Consequently, an application running on GraalVM needs to spend less time doing memory management and garbage collection.
For more information on performance tuning, refer to [Compiler Configuration on JVM](Options.md).

## Graph Compilation

To run guest programming languages, namely JavaScript, Ruby, R, Python, and WebAssembly in the same runtime as the host JVM-based language, the compiler should work with a language-independent intermediate representation (IR) between the source language and the machine code to be generated.
A *graph* was selected for this role.

The graph can represent similar statements of different languages in the same way, like "if" statements or loops, which makes it possible to mix languages in the same program.
The Graal compiler can then perform language-independent optimization and generate machine code on this graph.

GraalVM also includes the [Truffle language implementation framework](../../../truffle/docs/README.md) -- a library, written in Java -- to build interpreters for programming languages, which then run on GraalVM.
These languages can consequently benefit from the optimization possibilities of the Graal compiler.
The pipeline for such compilation is:

* The Truffle framework code and data (Abstract Syntax Trees) is partially evaluated to produce a compilation graph. When such an Abstract Syntax Tree (AST) is hot (i.e., called many times), it is scheduled for compilation by the compiler.
* The compilation graph is optimized by the Graal compiler to produce machine code.
* JVMCI installs this machine code in the VM's code cache.
* The AST will automatically redirect execution to the installed machine code once it is available.

## Ahead-of-time Compilation

Besides the Truffle framework, GraalVM incorporates its optimizing compiler into an advanced ahead-of-time (AOT) compilation technology -- [Native Image](../native-image/README.md) -- which translates Java and JVM-based code into a native platform executable.
These native executables start nearly instantaneously, are smaller, and consume less resources of the same Java application, making them ideal for cloud deployments and microservices.
For more information about AOT compilation, go to [Native Image](../native-image/README.md).

## Compiler Operating Modes

There are two operating modes of the Graal compiler when used as the HotSpot JIT compiler: as pre-compiled machine code ("libgraal"), or as dynamically executed Java bytecode ("jargraal").

**libgraal:** the Graal compiler is compiled ahead-of-time into a native shared library.
In this operating mode, the shared library is loaded by the HotSpot VM.
The compiler uses memory separate from the HotSpot heap.
It runs fast from the start since it does not need to warm up.
This is the default and recommended mode of operation.

**jargraal:** the Graal compiler goes through the same warm-up phase that the rest of the Java application does.
That is, it is first interpreted before its hot methods are compiled.
This mode is selected with the `-XX:-UseJVMCINativeLibrary` command line option.

## Diagnostic Data

If an uncaught exception is thrown by the compiler, the compilation is simply discarded and execution continues.
The Graal compiler can instead produce diagnostic data (such as immediate representation graphs) that can be submitted along with a bug report.
This is enabled with `-Dgraal.CompilationFailureAction=Diagnose`.
The default location of the diagnostics output is in `graal_dumps/` under the current working directory of the process but can be changed with the `-Dgraal.DumpPath` option.
During the VM shutdown, the location of the archive containing the diagnostic data is printed to the console.

Furthermore, diagnostic data can be produced for any compilation performed by the Graal compiler with the `-Dgraal.Dump` option.
This will produce diagnostic data for every method compiled by the compiler.
To refine the set of methods for which diagnostic data is produced, use the `-Dgraal.MethodFilter=<class>.<method>` option.
For example, `-Dgraal.MethodFilter=java.lang.String.*,HashMap.get` will produce diagnostic data only for methods in the `java.lang.String` class as well as methods named `get` in a class whose non-qualified name is `HashMap`.

Instead of being written to a file, diagnostic data can also be sent over the network to the [Ideal Graph Visualizer](../../tools/ideal-graph-visualizer.md).
This requires the `-Dgraal.PrintGraph=Network` option, upon which the compiler will try to send diagnostic data to _127.0.0.1:4445_.
This network endpoint can be configured with the `-Dgraal.PrintGraphHost` and `-Dgraal.PrintGraphPort` options.

Note: the Ideal Graph Visualizer is available with Oracle GraalVM Enterprise Edition.
