---
layout: ohc
permalink: /overview/architecture/
---

# Oracle GraalVM Enterprise Edition Architecture Overview

Oracle GraalVM Enterprise Edition (GraalVM Enterprise) is a highly productive JDK distribution.
It is designed to accelerate the execution of applications written in Java and other JVM languages while also providing a high-performance runtime for JavaScript, Ruby, Python, and a number of other popular languages.
GraalVM Enterprise's polyglot capabilities make it possible to mix multiple programming languages in a single application while eliminating any foreign language call costs.

This page provides developers, solution architects, and infrastructure architects with an architectural overview of GraalVM Enterprise, as well as information about runtime modes, supported platforms, available distributions, core and additional functionalities, and support levels for various features.
The conceptual overview and advantages of GraalVM Enterprise are described on the [Solutions Overview](https://docs.oracle.com/en/graalvm/enterprise/21/docs/overview/) page.

* [GraalVM Enterprise Architecture](#graalvm-enterprise-architecture)
* [Runtime Modes](#runtime-modes)
* [Available Distributions](#available-distributions)
* [Supported Platforms](#supported-platforms)
* [Distribution Components List](#distribution-components-list)
* [Licensing and Support](#licensing-and-support)
* [Experimental and Early Adopter Features](#experimental-and-early-adopter-features)
* [What to Read Next](#what-to-read-next)

## GraalVM Enterprise Architecture

![](/img/graalvm_architecture.png)

*Figure 1. GraalVM Enterprise Runtime*

The preceding diagram illustrates a complete high-level architecture of GraalVM Enterprise.

GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](/reference-manual/compiler/), which is written in Java, to the HotSpot Java Virtual Machine.

In addition to running Java and JVM-based languages, GraalVM's [Truffle language implementation framework](/graalvm-as-a-platform/language-implementation-framework/) makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With GraalVM Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

GraalVM Enterprise is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java applications can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [GraalVM compiler](/reference-manual/compiler/) as the top-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](/graalvm-as-a-platform/language-implementation-framework/), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](/reference-manual/native-image/) is an innovative technology that compiles Java code into a standalone binary executable or a native shared library.
The Java bytecode that is processed during the native image build includes all application classes, dependencies, third party dependent libraries, and any JDK classes that are required.
A generated self-contained native executable is specific to each individual operating systems and machine architecture that does not require a JVM.

#### Java on Truffle
[Java on Truffle](/reference-manual/java-on-truffle/) is an implementation of the Java Virtual Machine Specification, built with the [Truffle language implementation framework](/graalvm-as-a-platform/language-implementation-framework/).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.
Java on Trufle is an experimental technology in GraalVM, available as of version 21.0.0.

## Available Distributions

GraalVM Enterprise distributions are based on Oracle JDK 8, 11, and 16.
GraalVM Enterprise releases include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

GraalVM Enterprise is available for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on ARM 64-bit system.
The base GraalVM binary including all components is experimental on Linux/ARM and Windows.
The GraalVM Enterprise distribution based on Oracle JDK 16 is experimental with [several known limitations](https://docs.oracle.com/en/graalvm/enterprise/21/docs/overview/known-issues/).
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.

## Supported Platforms

The following are the supported platforms for GraalVM Enterprise 21:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux on OCI 	| 6, 7, 8 	| x86 64-bit 	| [GraalVM Enterprise Installation Guide on OCI](/getting-started/oci/compute-instances/) 	|
| Oracle Linux 	| 6, 7, 8 	| x86 64-bit, ARM 64-bit (experimental)	| [GraalVM Enterprise Installation Guide for Linux](/getting-started/installation-linux/) 	|
| Red Hat Enterprise Linux(RHEL) 	| 6, 7, 8 	| x86 64-bit 	| [GraalVM Enterprise Installation Guide for Linux](/getting-started/installation-linux/) 	|
| macOS 	| 10.13 (High Sierra), 10.14 (Mojave), 10.15 (Catalina), 11.2 (Big Sur)	| x86 64-bit 	| [GraalVM Enterprise Installation Guide for macOS](/getting-started/installation-macos/) 	|
| Windows 	| 10                                                                                                  	| x86 64-bit 	| [GraalVM Enterprise Installation Guide for Windows](/getting-started/installation-windows/) 	|


## Distribution Components List

GraalVM Enterprise consists of core and additional functionalities.

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
* [GraalVM Updater](/reference-manual/graalvm-updater/) to install additional functionalities

### Additional Functionalities
GraalVM Enterprise core installation can be extended with more languages runtimes and utilities.

Tools/Utilities:

* [Native Image](/reference-manual/native-image/) -- a technology to compile an application ahead-of-time into a native platform executable.
* [LLVM toolchain](/reference-manual/llvm/) --  a set of tools and APIs for compiling native programs to bitcode that can be executed on GraalVM.
* [Java on Truffle](/reference-manual/java-on-truffle/) -- a JVM implementation built upon the [Truffle framework](/graalvm-as-a-platform/language-implementation-framework/) to run Java via a Java bytecode interpreter.

Runtimes:

* [Node.js](/reference-manual/js/) -- the Node.js 14.16.1 runtime for JavaScript
* [Python](/reference-manual/python/) -- Python 3.8.5 compatible
* [Ruby](/reference-manual/ruby/) -- Ruby 2.7.2 compatible
* [R](/reference-manual/r/) -- GNU R 4.0.3 compatible
* [GraalWasm](/reference-manual/wasm/) -- WebAssembly (Wasm)

## Licensing and Support

Oracle GraalVM Enterprise Edition is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.

For production use, GraalVM Enterprise is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/uk/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and the access to [My Oracle Support (MOS)](https://www.oracle.com/support/).

## Experimental and Early Adopter Features

Oracle GraalVM Enterprise Edition features are distributed as fully supported, early adopter, and experimental.

Experimental features are being considered for future versions of GraalVM Enterprise.
They are not meant to be used in production and are not supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

The following table lists supported and experimental features in GraalVM Enterprise Edition 21 by platform.

| Feature | Linux AMD64 | Linux ARM64 | macOS | Windows |
|--------------------|---------------|---------------|---------------|
| Native Image | early adopter | experimental | early adopter | experimental |
| LLVM runtime | supported | experimental | supported | not available |
| LLVM toolchain | supported | experimental | supported | not available |
| JavaScript | supported | experimental | supported | experimental |
| Node.js  | supported | experimental | supported | experimental |
| Java on Truffle | experimental | not available | experimental | experimental |
| Python | experimental | not available | experimental | not available |
| Ruby | experimental | not available | experimental | not available |
| R | experimental | not available | experimental | not available |
| WebAssembly | experimental | experimental | experimental | experimental |

## What to Read Next

Users who are new to GraalVM Enterprise or have little experience using it, continue to [Getting Started with GraalVM Enterprise](/getting-started//#install-graalvm-enterprise).
Download and install GraalVM Enterprise on your local machine, try running the examples provided in the guide, or test GraalVM Enterprise with your workload.
We suggest you then look at more complex [Examples Applications](/docs/examples/).

Developers, who have GraalVM Enterprise already installed or have experience using it in the past, can skip the getting started guide and proceed to the [Reference Manuals](/reference-manual/) for
in-depth coverage of GraalVM Enterprise technologies.
