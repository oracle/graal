---
layout: ohc
permalink: /overview/architecture/
---

# Oracle GraalVM Architecture Overview

Oracle GraalVM is a high-performance JDK distribution designed to accelerate Java applications using the advanced Graal compiler.
In addition to the standard just-in-time (JIT) setup on HotSpot, Graal can also be used as an ahead-of-time (AOT) compiler.
This enables a new execution mode that makes applications smaller, leaner, and more secure, without compromising run-time performance.
Moreover, Oracle GraalVM also provides runtimes for JavaScript, Python, and several other languages that can interoperate with Java and between each other.

Here you can find information about the architectural overview of Oracle GraalVM, runtime modes, certified platforms, available distributions, core and additional components, and support levels for various features.
The conceptual overview and advantages of Oracle GraalVM are described on the [Solutions Overview](solutions-overview.md) page.

* [Architecture](#graalvm-enterprise-architecture)
* [Runtime Modes](#runtime-modes)
* [Distribution Components List](#distribution-components-list)
* [What to Read Next](#what-to-read-next)

## Architecture

Oracle GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](../reference-manual/java/compiler.md), which is written in Java, to the HotSpot Java Virtual Machine.

Oracle GraalVM includes [Native Image](../reference-manual/native-image/README.md): a technology that can compile Java applications into binaries for a specific operating system and architecture.

In addition to running Java and JVM-based languages, [Oracle GraalVM's language implementation framework (Truffle)](../../truffle/docs/README.md), makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

Oracle GraalVM is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java application can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [Graal compiler](../reference-manual/java/compiler.md) as the last-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](../../truffle/docs/README.md), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](../reference-manual/native-image/README.md) is an innovative technology that compiles Java code into a standalone binary executable or a native shared library.
The Java bytecode that is processed when building a native executable includes all application classes, dependencies, third-party dependent libraries, and any JDK classes that are required.
A self-contained native executable is generated specifically for one operating system and machine architecture and no longer requires a JVM to run.

#### Java on Truffle
[Java on Truffle](../reference-manual/java-on-truffle/README.md) is an implementation of the Java Virtual Machine Specification, built with the [Truffle framework](../../truffle/docs/README.md).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.

## Distribution Components List

Oracle GraalVM consists of core and additional functionalities.

### Core Components

* Java HotSpot VM
* [Graal compiler](..reference-manual/java/compiler.md) -- the top-tier JIT compiler
* [Native Image](../reference-manual/native-image/README.md) -- a technology to compile a Java application ahead-of-time into a binary
* [GraalVM Updater](../reference-manual/graalvm-updater.md) -- a utility to install additional features
* Polyglot API – the APIs for combining programming languages in a shared runtime

### Additional Functionalities

Oracle GraalVM JDK installation can be extended with more language runtimes and utilities.

* [JavaScript](../reference-manual/js/README.md) -- REPL with the JavaScript interpreter
* [Java on Truffle](../reference-manual/java-on-truffle/README.md) -- a JVM implementation built upon the [Truffle framework](../../truffle/docs/README.md) to run Java via a Java bytecode interpreter
* [LLVM toolchain](../reference-manual/llvm/README.md) -- a set of tools and APIs for compiling native programs to bitcode
* [LLVM](../reference-manual/llvm/README.md) -- LLVM runtime with `lli` tool to directly execute programs from LLVM bitcode
* [Node.js](../../reference-manual/js/NodeJS.md) -- the Node.js 16.14.2 runtime for JavaScript
## What to Read Next

Users who are new to Oracle GraalVM or have little experience using it, continue to [Getting Started with Oracle GraalVM](../getting-started/graalvm-enterprise/get-started-graalvm-enterprise.md).
Download and install Oracle GraalVM on your local machine, try running the examples provided in the guide, or test Oracle GraalVM with your workload.

Developers, who have Oracle GraalVM already installed or have experience using it in the past, can skip the getting started guide and proceed to the [Reference Manuals](../reference-manual/reference-manuals.md) for in-depth coverage of Oracle GraalVM technologies.
