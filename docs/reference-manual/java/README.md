---
layout: docs
toc_group: java
link_title: Java Reference
permalink: /reference-manual/java/
---

# Java Reference

Any JVM-based application that runs on Java HotSpot VM can run on GraalVM.
GraalVM is based on Java HotSpot VM, but integrates an advanced just-in-time (JIT) compiler, written in Java - the Graal compiler.
At runtime, the application is loaded and executed normally on the JVM.
The JVM passes bytecode to the Graal compiler, which compiles that to the machine code and returns it to the JVM.

GraalVM's dynamic compiler can improve the efficiency and the speed of applications written in Java, Scala, Kotlin, or other JVM languages through unique approaches to code analysis and optimization.
For example, it assures performance advantages for highly abstracted programs due to its ability to remove costly object allocations.
To learn more, go to the Graal [compiler](compiler.md) page.
The open source compiler's code is available on [GitHub](https://github.com/oracle/graal/tree/master/compiler).

## Compiler Operating Modes

There are two operating modes of Graal when used as a HotSpot JIT compiler:
- **libgraal**: the Graal compiler is compiled ahead-of-time into a native shared library.
In this operating mode, the shared library is loaded by the HotSpot VM.
The compiler uses memory separate from the HotSpot heap and runs fast from the start since it does not need to warmup.
This is the default and recommended mode of operation.

- **jargraal**: the Graal compiler goes through the same warm-up phase that the rest of the Java application does. That is, it is first interpreted before its hot methods are compiled.
This mode is selected with the `-XX:-UseJVMCINativeLibrary` command-line option.
This will delay the time to reach peak performance as the compiler itself needs to be compiled before it produces code quickly.
This mode allows you to [debug the Graal compiler with a Java debugger](Operations.md#troubleshooting-the-graalvm-compiler).

## Interoperability

In addition to running JVM-based languages on GraalVM, you can also call any other language implemented with the [Truffle language implementation framework](../../../truffle/docs/README.md) directly from Java.
See the [Polyglot Programming](../polyglot-programming.md) and [Embedding Languages](../embedding/embed-languages.md) guides for more information about interoperability with other programming languages.
