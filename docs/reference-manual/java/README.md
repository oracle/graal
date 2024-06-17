---
layout: docs
toc_group: java
link_title: GraalVM as a Java Virtual Machine
permalink: /reference-manual/java/
---

# GraalVM as a Java Virtual Machine

GraalVM is based on the [Java HotSpot Virtual Machine](https://docs.oracle.com/en/java/javase/22/vm/java-virtual-machine-technology-overview.html), so any application that runs on the Java HotSpot Virtual Machine will also on GraalVM.

GraalVM includes an advanced compiler written in Java, known as the _Graal compiler_.
At runtime, just like any other Java Virtual Machine (JVM), GraalVM loads an application and analyzes its code to detect performance bottlenecks, or _hot spots_. 
GraalVM passes the performance-critical code to the Graal just-in-time (JIT) compiler, which compiles it to machine code and then returns it.

The Graal compiler can improve the efficiency and the speed of applications written in Java, Scala, Kotlin, or other JVM languages through its unique approaches to code analysis and optimization.
For example, it assures performance advantages for highly-abstracted applications due to its ability to remove costly object allocations.
For more information, see the [platform-independent compiler optimizations in GraalVM Community Edition](https://github.com/oracle/graal/blob/master/compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/core/phases/CEOptimization.java){:target="_blank"}.

> The Graal (JIT) compiler is now also integrated with the [Java HotSpot Virtual Machine](https://docs.oracle.com/en/java/javase/22/vm/java-virtual-machine-technology-overview.html). 
To find out more, see [Graal Compiler](compiler.md).

## Interoperability

GraalVM also includes the [Truffle language implementation framework](../../../truffle/docs/README.md)&mdash;a library, written in Java&mdash;to build interpreters for programming languages, which then run on GraalVM.
These "Graal languages" can consequently benefit from the optimization possibilities of the Graal compiler.
The pipeline for such compilation is:

* The Truffle framework code and data (Abstract Syntax Trees) is partially evaluated to produce a compilation graph. When such an Abstract Syntax Tree (AST) is "hot" (that is, called many times), it is scheduled for compilation by the compiler.
* The compilation graph is optimized by the Graal compiler to produce machine code.
* JVMCI installs this machine code in the JVM's code cache.
* The AST will automatically redirect execution to the installed machine code once it is available.

See the [Polyglot Programming](../polyglot-programming.md) and [Embedding Languages](../embedding/embed-languages.md) guides for more information about interoperability with other programming languages.

## Ahead-of-time Compilation

Besides the Truffle framework, GraalVM incorporates its compiler into an advanced ahead-of-time (AOT) compilation technology&mdash;[Native Image](../native-image/README.md)&mdash;which translates Java and JVM-based code into a native platform executable.
These native executables start nearly instantaneously, are smaller, and consume less resources than their JVM counterparts, making them ideal for cloud deployments and microservices.
For more information about AOT compilation, see [Native Image](../native-image/README.md).

### Related Documentation

- [Graal Compiler](compiler.md)
- [Graal JIT Compiler Configuration](Options.md)
- [Graal JIT Compiler Operations Manual](Operations.md)
- [Native Image](../native-image/README.md)
