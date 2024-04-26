---
layout: docs
toc_group: java
link_title: Graal Compiler
permalink: /reference-manual/java/compiler/
redirect_from: /reference-manual/compiler/
---

# Graal Compiler

The Graal compiler is a dynamic compiler, written in Java, that transforms bytecode into machine code.
The Graal just-in-time (JIT) compiler is integrated with the [Java HotSpot Virtual Machine](https://docs.oracle.com/en/java/javase/22/vm/java-virtual-machine-technology-overview.html) and [GraalVM](README.md).
(The open source code of the Graal JIT compiler is available on [GitHub](https://github.com/oracle/graal/tree/master/compiler).)


## Compiler Advantages

The Graal JIT compiler provides optimized performance for applications running on a Java Virtual Machine (JVM) through unique approaches to code analysis and optimization.
It includes multiple optimization algorithms (called “Phases”), such as aggressive inlining, polymorphic inlining, and others. 

<!--
    Add an anchor so that the JPG docs can link to a section on partial escape analysis.
-->
<a name="partial-escape-analysis"></a>
The Graal compiler can bring performance advantages for highly-abstracted programs. 
For example, it includes a [partial-escape-analysis optimization](https://github.com/oracle/graal/blob/master/compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/core/phases/CEOptimization.java#L176){:target="_blank"} that can remove the costly allocations of certain objects. 
The optimization determines when a new object is accessible outside a compilation unit and only allocates it on paths that "escape" the compilation unit (for example, if the object is passed as a parameter, stored in a field, or returned from a method). 
This approach can greatly improve the performance of an application by reducing the number of heap allocations. 
Code that uses more modern Java features such as Streams or Lambdas will see greater improvements in performance as this type of code involves a significant number of such non- or partially-escaping objects.
Code bound by characteristics such as I/O or memory allocations that cannot be removed by the compiler will see less improvement. 
For more information on performance tuning, refer to [Graal JIT Compiler Configuration](Options.md).

## Graph Compilation

To run guest programming languages (namely JavaScript, Python, and Ruby) in the same runtime as the host JVM-based language, the compiler works with a language-independent intermediate graph representation between the source language and the machine code to be generated. (For more information on language interoperability, see [Interoperability](README.md#interoperability).)

The graph can represent similar statements of different languages in the same way, such as "if" statements or loops, which makes it possible to mix languages in the same application.
The Graal compiler can then perform language-independent optimization and generate machine code on this graph.

## Diagnostic Data

If an uncaught exception is thrown by the compiler, the compilation is typically discarded and execution continues.
The Graal compiler can instead produce diagnostic data (such as immediate representation graphs) that can be submitted along with a bug report.
This is enabled with the `-Djdk.graal.CompilationFailureAction=Diagnose` option.
The default location of the diagnostics output is in the _graal_dumps/_ directory under the current working directory of the process but can be changed with the `-Djdk.graal.DumpPath` option.
During the JVM shutdown, the location of the archive containing the diagnostic data is printed to the console.

Furthermore, diagnostic data can be produced for any compilation performed by the Graal compiler with the `-Djdk.graal.Dump` option.
This will produce diagnostic data for every method compiled by the compiler.

To refine the set of methods for which diagnostic data is produced, use the `-Djdk.graal.MethodFilter=<class>.<method>` option.
For example, `-Djdk.graal.MethodFilter=java.lang.String.*,HashMap.get` will produce diagnostic data only for methods in the `java.lang.String` class as well as methods named `get` in a class whose non-qualified name is `HashMap`.

### Related Documentation

- [Graal JIT Compiler Operations Manual](Operations.md)
- [Graal JIT Compiler Configuration](Options.md)