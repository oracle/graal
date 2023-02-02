---
layout: ohc
permalink: /overview/architecture/
---

# Oracle GraalVM Enterprise Edition Architecture Overview

Oracle GraalVM Enterprise Edition (GraalVM Enterprise) is a high performance JDK distribution, built on the global standard for application development.
It is designed to accelerate the execution of applications written in Java and other JVM languages while also providing runtimes for JavaScript, Python, and a number of other popular languages. 
GraalVM Enterprise's polyglot capabilities make it possible to mix multiple programming languages in a single application while eliminating any foreign language call costs.

This page provides developers, solution architects, and infrastructure architects with an architectural overview of GraalVM Enterprise, as well as information about runtime modes, certified platforms, available distributions, core and additional components, and support levels for various features.
The conceptual overview and advantages of GraalVM Enterprise are described on the [Solutions Overview](solutions-overview.md) page.

* [Architecture](#graalvm-enterprise-architecture)
* [Runtime Modes](#runtime-modes)
* [Distribution Components List](#distribution-components-list)
* [What to Read Next](#what-to-read-next)

## Architecture

![](/img/graalvm_architecture.png)

*Figure 1. GraalVM Enterprise Runtime*

The preceding diagram illustrates a complete high-level architecture of GraalVM Enterprise.

GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](../reference-manual/java/compiler.md), which is written in Java, to the HotSpot Java Virtual Machine.

In addition to running Java and JVM-based languages, [GraalVM's language implementation framework (Truffle)](../../truffle/docs/README.md), makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

GraalVM Enterprise is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java application can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [Graal compiler](../reference-manual/java/compiler.md) as the top-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](../../truffle/docs/README.md), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](../reference-manual/native-image/README.md) is an innovative technology that compiles Java code into a standalone binary executable or a native shared library.
The Java bytecode that is processed when building a native executable includes all application classes, dependencies, third party dependent libraries, and any JDK classes that are required.
A generated self-contained native executable is specific to each individual operating systems and machine architecture that does not require a JVM.

#### Java on Truffle
[Java on Truffle](../reference-manual/java-on-truffle/README.md) is an implementation of the Java Virtual Machine Specification, built with the [Truffle framework](../../truffle/docs/README.md).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.

## Distribution Components List

GraalVM Enterprise consists of core and additional functionalities.

### Core Components

* Java HotSpot VM
* Graal compiler - the top-tier JIT compiler
* Polyglot API – the APIs for combining programming languages in a shared runtime
* [GraalVM Updater](../reference-manual/graalvm-updater.md) - a utility to install additional functionalities

### Additional Functionalities
GraalVM Enterprise JDK installation can be extended with more languages runtimes and utilities.

Tools/Utilities:

* [Native Image](../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native platform executable.
* [LLVM toolchain](../reference-manual/llvm/README.md) --  a set of tools and APIs for compiling native programs to bitcode that can be executed on GraalVM.

Runtimes:

* [JavaScript](../reference-manual/js/README.md) -- REPL with the JavaScript interpreter
* [Node.js](../../reference-manual/js/NodeJS.md) -- the Node.js 16.14.2 runtime for JavaScript
* [LLVM](../reference-manual/llvm/README.md) -- LLVM runtime with `lli` tool to directly execute programs from LLVM bitcode
* [Java on Truffle](../reference-manual/java-on-truffle/README.md) -- a JVM implementation built upon the [Truffle framework](../../truffle/docs/README.md) to run Java via a Java bytecode interpreter.
* [Python](../reference-manual/python/README.md) -- Python 3.8.5 compatible runtime
* [Ruby](../reference-manual/ruby/README.md) -- Ruby 3.0.3 compatible runtime
* [R](../reference-manual/r/README.md) -- GNU R 4.0.3 compatible runtime
* [GraalWasm](../reference-manual/wasm/README.md) -- WebAssembly (Wasm) runtime

## What to Read Next

Users who are new to GraalVM Enterprise or have little experience using it, continue to [Getting Started with GraalVM Enterprise](../getting-started/graalvm-enterprise/get-started-graalvm-enterprise.md).
Download and install GraalVM Enterprise on your local machine, try running the examples provided in the guide, or test GraalVM Enterprise with your workload.

Developers, who have GraalVM Enterprise already installed or have experience using it in the past, can skip the getting started guide and proceed to the [Reference Manuals](../reference-manual/reference-manuals.md) for
in-depth coverage of GraalVM Enterprise technologies.
