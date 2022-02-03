---
layout: docs
toc_group: docs
title: Architecture Overview
permalink: /docs/introduction/
---

# Introduction to GraalVM

GraalVM is a high-performance JDK distribution designed to accelerate the execution of applications written in Java and other JVM languages along with support for JavaScript, Ruby, Python, and a number of other popular languages.
GraalVM’s polyglot capabilities make it possible to mix multiple programming languages in a single application while eliminating foreign language call costs.

This page provides an architectural overview of GraalVM and its runtime modes, supported platforms, available distributions, core and additional functionalities, and support levels for various features.

* [GraalVM Architecture](#graalvm-architecture)
* [Runtime Modes](#runtime-modes)
* [Available Distributions](#available-distributions)
* [Distribution Components List](#distribution-components-list)
* [Licensing and Support](#licensing-and-support)
* [Features Support](#features-support)
* [What to Read Next](#what-to-read-next)

## GraalVM Architecture

![](/img/graalvm_architecture_community.png)

GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](https://github.com/oracle/graal/tree/master/compiler), which is written in Java, to the HotSpot Java Virtual Machine.

In addition to running Java and JVM-based languages, [GraalVM's language implementation framework (Truffle)](../truffle/docs/README.md) makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With GraalVM Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

GraalVM is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java applications can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [GraalVM compiler](reference-manual/java/compiler.md) as the top-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](../truffle/docs/README.md), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](reference-manual/native-image/README.md) is an innovative technology that compiles Java code into a standalone binary executable or a native shared library.
The Java bytecode that is processed during the native image build includes all application classes, dependencies, third party dependent libraries, and any JDK classes that are required.
A generated self-contained native executable is specific to each individual operating systems and machine architecture that does not require a JVM.

#### Java on Truffle
[Java on Truffle](reference-manual/java-on-truffle/README.md) is an implementation of the Java Virtual Machine Specification, built with the [Truffle language implementation framework](../truffle/docs/README.md).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.

## Available Distributions

GraalVM is available as **GraalVM Enterprise** and **GraalVM Community** editions and includes support for Java 11 and Java 17.
GraalVM Enterprise is based on Oracle JDK while GraalVM Community is based on OpenJDK.

GraalVM is available for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on ARM 64-bit system.
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.
See the [Getting Started guide](getting-started/graalvm-community/get-started-graalvm-community.md) for installation instructions.

## Distribution Components List

GraalVM consists of core and additional components.
The core components enable using GraalVM as a runtime platform for programs written in JVM-based languages or embeddable polyglot applications.

### Core Components
**Runtimes**
* Java HotSpot VM
* JavaScript runtime
* LLVM runtime

**Libraries (JAR files)**
* GraalVM compiler - the top-tier JIT compiler
* Polyglot API – the APIs for combining programming languages in a shared runtime

**Utilities**
* JavaScript REPL with the JavaScript interpreter
* `lli` tool to directly execute programs from LLVM bitcode
* [GraalVM Updater](reference-manual/graalvm-updater.md) to install additional functionalities

### Additional Components
GraalVM core installation can be extended with more languages runtimes and utilities.

Tools/Utilities:

* [Native Image](reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native executable.
* [LLVM toolchain](reference-manual/llvm/README.md) --  a set of tools and APIs for compiling native programs to bitcode that can be executed with on the GraalVM runtime.

Runtimes:

* [Java on Truffle](reference-manual/java-on-truffle/README.md) -- a JVM implementation built upon the [Truffle framework](../truffle/docs/README.md) to run Java via a Java bytecode interpreter.
* [Node.js](reference-manual/js/README.md) -- the Node.js 14.18.1 runtime for JavaScript
* [Python](reference-manual/python/README.md) -- Python 3.8.5 compatible
* [Ruby](reference-manual/ruby/README.md) -- Ruby 3.0.2 compatible
* [R](reference-manual/r/README.md) -- GNU R 4.0.3 compatible
* [GraalWasm](reference-manual/wasm/README.md) -- WebAssembly (Wasm)

## Licensing and Support

GraalVM Community Edition is open source software built from the sources available on [GitHub](https://github.com/oracle/graal) and distributed under [version 2 of the GNU General Public  License with the “Classpath” Exception](https://github.com/oracle/graal/blob/master/LICENSE), which are the same terms as for Java.
Check the [licenses](https://github.com/oracle/graal#license) of individual GraalVM components which are generally derivative of the license of a particular language and may differ.
GraalVM Community is free to use for any purpose and comes with no strings attached, but also no guarantees or support.

## Features Support

GraalVM technologies are distributed as production-ready and experimental.

Experimental features are being considered for future versions of GraalVM and are not meant to be used in production.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

The following table lists production-ready and experimental features in GraalVM Community Edition 21 by platform.

| Feature | Linux AMD64 | Linux ARM64 | macOS | Windows |
|--------------------|---------------|---------------|---------------|
| Native Image | stable | stable | stable | stable |
| LLVM runtime | stable | stable | stable | not available |
| LLVM toolchain | stable | stable | stable | not available |
| JavaScript | stable | stable | stable | stable |
| Node.js  | stable | stable | stable | stable |
| Java on Truffle | experimental | experimental | experimental | experimental |
| Python | experimental | not available | experimental | not available |
| Ruby | experimental | experimental | experimental | not available |
| R | experimental | not available | experimental | not available |
| WebAssembly | experimental | experimental | experimental | experimental |

## What to Read Next

Whether you are new to GraalVM or have little experience using it, continue to [Get Started with GraalVM](getting-started/graalvm-community/get-started-graalvm-community.md).
Install GraalVM on your local machine, try running the examples provided in the guide, or test GraalVM with your workload.
After that we suggest you to look at more complex [Examples Applications](examples/examples.md).

Developers who have GraalVM already installed or have experience using, can skip the getting started guide and proceed to the [Reference Manuals](reference-manual/reference-manuals.md) for in-depth coverage of GraalVM technologies.

To start coding with the GraalVM Polyglot APIs, check out the [GraalVM SDK Java API Reference](http://www.graalvm.org/sdk/javadoc).

If you cannot find the answer you need in the available documentation or have a troubleshooting query, you can ask for help in a [slack channel](/slack-invitation/) or [submit a GitHub issue](https://github.com/oracle/graal/issues).
