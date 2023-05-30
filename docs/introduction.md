---
layout: docs
toc_group: docs
title: Architecture Overview
permalink: /docs/introduction/
redirect_from: /$version/docs/introduction/
---

# Introduction to GraalVM

GraalVM is a high-performance JDK distribution designed to accelerate Java applications using the advanced Graal compiler.
In addition to the standard just-in-time (JIT) setup on HotSpot, Graal can also be used as an ahead-of-time (AOT) compiler.
This enables a new execution mode that makes applications smaller, leaner, and more secure, without compromising run-time performance.
Moreover, GraalVM also provides runtimes for JavaScript, Python, and several other languages that can interoperate with Java and between each other.

Here you can find information about an architectural overview of GraalVM, its runtime modes, supported platforms, available distributions, core and additional functionalities, and support levels for various features.

* [Architecture](#architecture)
* [Runtime Modes](#runtime-modes)
* [Available Distributions](#available-distributions)
* [Distribution Components List](#distribution-components-list)
* [Licensing and Support](#licensing-and-support)
* [Features Support](#features-support)
* [What to Read Next](#what-to-read-next)

## Architecture

![GraalVM architecture](/img/graalvm_architecture_community.png)

GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](https://github.com/oracle/graal/tree/master/compiler), which is written in Java, to the HotSpot Java Virtual Machine.

GraalVM includes [Native Image](reference-manual/native-image/README.md): a technology to build Java code into a native binary for a specific operating system and architecture. 

In addition to running Java and JVM-based languages, [GraalVM's language implementation framework (Truffle)](../truffle/docs/README.md) makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

GraalVM is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java applications can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [Graal compiler](reference-manual/java/compiler.md) as the top-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](../truffle/docs/README.md), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](reference-manual/native-image/README.md) is an innovative technology that compiles Java code into a standalone native executable or a native shared library.
The Java bytecode that is processed during the build of a native executable includes all application classes, dependencies, third party dependent libraries, and any JDK classes that are required.
A generated self-contained native executable is specific to the operating system and machine architecture that does not require a JVM to run.

#### Java on Truffle
[Java on Truffle](reference-manual/java-on-truffle/README.md) is an implementation of the Java Virtual Machine Specification, built with [Truffle](../truffle/docs/README.md).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.

## Available Distributions

GraalVM is available as **Oracle GraalVM** and **GraalVM Community Edition**.
Oracle GraalVM is based on Oracle JDK while GraalVM Community Edition is based on OpenJDK.

GraalVM is available for Linux and macOS on x64 and AArch64 architectures, and for Windows on the x64 architecture.
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.
See the [Getting Started guide](getting-started/graalvm-community/get-started-graalvm-community.md) for installation instructions.

## Distribution Components List

GraalVM consists of core and additional components.
The core components enable using GraalVM as a runtime platform for programs written in JVM-based languages or embeddable polyglot applications.

### Core Components

* Java HotSpot VM
* [Graal compiler](reference-manual/java/compiler.md) -- the top-tier JIT compiler
* [Native Image](reference-manual/native-image/README.md) -- a technology to compile a Java application ahead-of-time into a binary
* [GraalVM Updater](reference-manual/graalvm-updater.md) -- a utility to install additional functionalities
* Polyglot API – the APIs for combining programming languages in a shared runtime

### Additional Components

GraalVM core installation can be extended with more language runtimes and utilities.

* [GraalWasm](reference-manual/wasm/README.md) -- WebAssembly (Wasm)
* [JavaScript](reference-manual/js/README.md) -- REPL with the JavaScript interpreter
* [Java on Truffle](reference-manual/java-on-truffle/README.md) -- a JVM implementation built upon [Truffle](../truffle/docs/README.md) to run Java via a Java bytecode interpreter
* [LLVM toolchain](reference-manual/llvm/README.md) -- a set of tools and APIs for compiling native programs to bitcode
* [LLVM](reference-manual/llvm/README.md) -- LLVM runtime with `lli` tool to directly execute programs from LLVM bitcode
* [Node.js](reference-manual/js/NodeJS.md) -- the Node.js runtime for JavaScript
* [Python](reference-manual/python/README.md) -- Python 3.8.x compatible runtime
* [Ruby](reference-manual/ruby/README.md) -- Ruby 3.x runtime

## Licensing and Support

GraalVM Community Edition is open-source software built from the sources available on [GitHub](https://github.com/oracle/graal) and distributed under [version 2 of the GNU General Public  License with the “Classpath” Exception](https://github.com/oracle/graal/blob/master/LICENSE), which are the same terms as for Java.
Check the [licenses](https://github.com/oracle/graal#license) of individual GraalVM components which are generally derivative of the license of a particular language and may differ.
GraalVM Community Edition is free to use for any purpose and comes with no strings attached, but also no guarantees or support.

## Features Support

GraalVM technologies are distributed as stable and experimental.

Experimental features are being considered for future versions of GraalVM and are not meant to be used in production.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered stable.

The following table lists stable and experimental features in GraalVM Community Edition by platform.

| Feature         | Linux AMD64  | Linux AArch64 | macOS AMD64  | macOS AArch64 | Windows AMD64 |
|-----------------|--------------|---------------|--------------|---------------|---------------|
| Native Image    | stable       | stable        | stable       | stable        | stable        |
| LLVM runtime    | stable       | stable        | stable       | stable        | not available |
| LLVM toolchain  | stable       | stable        | stable       | stable        | not available |
| JavaScript      | stable       | stable        | stable       | stable        | stable        |
| Node.js         | stable       | stable        | stable       | stable        | stable        |
| Java on Truffle | stable       | experimental  | experimental | experimental  | experimental  |
| Python          | experimental | experimental  | experimental | experimental  | not available |
| Ruby            | experimental | experimental  | experimental | experimental  | not available |
| WebAssembly     | experimental | experimental  | experimental | not available | experimental  |

## What to Read Next

Whether you are new to GraalVM or have little experience using it, continue to [Get Started with GraalVM](getting-started/graalvm-community/get-started-graalvm-community.md).
Install GraalVM on your local machine, try running the examples provided in the guide, or test GraalVM with your workload.
After that we suggest you to look at more complex [GraalVM Demos on GitHub](https://github.com/graalvm/graalvm-demos).

Developers who have GraalVM already installed or have experience using, can skip the getting started guide and proceed to the [Reference Manuals](reference-manual/reference-manuals.md) for in-depth coverage of GraalVM technologies.

To start coding with the GraalVM Polyglot APIs, check the [GraalVM SDK Java API Reference](http://www.graalvm.org/sdk/javadoc).

If you cannot find the answer you need in the available documentation or have a troubleshooting query, you can ask for help in a [slack channel](/slack-invitation/) or [submit a GitHub issue](https://github.com/oracle/graal/issues).
