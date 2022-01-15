---
layout: ohc
permalink: /overview/architecture/
---

# Oracle GraalVM Enterprise Edition Architecture Overview

Oracle GraalVM Enterprise Edition (GraalVM Enterprise) is a highly productive JDK distribution.
It is designed to accelerate the execution of applications written in Java and other JVM languages while also providing a high-performance runtime for JavaScript, Ruby, Python, and a number of other popular languages.
GraalVM Enterprise's polyglot capabilities make it possible to mix multiple programming languages in a single application while eliminating any foreign language call costs.

This page provides developers, solution architects, and infrastructure architects with an architectural overview of GraalVM Enterprise, as well as information about runtime modes, supported platforms, available distributions, core and additional functionalities, and support levels for various features.
The conceptual overview and advantages of GraalVM Enterprise are described on the [Solutions Overview](solutions-overview.md) page.

* [GraalVM Enterprise Architecture](#graalvm-enterprise-architecture)
* [Runtime Modes](#runtime-modes)
* [Available Distributions](#available-distributions)
* [Certified Platforms](#certified-platforms)
* [Distribution Components List](#distribution-components-list)
* [Licensing and Support](#licensing-and-support)
* [Experimental and Early Adopter Features](#experimental-and-early-adopter-features)
* [What to Read Next](#what-to-read-next)

## GraalVM Enterprise Architecture

![](/img/graalvm_architecture.png)

*Figure 1. GraalVM Enterprise Runtime*

The preceding diagram illustrates a complete high-level architecture of GraalVM Enterprise.

GraalVM adds an [advanced just-in-time (JIT) optimizing compiler](../reference-manual/java/compiler.md), which is written in Java, to the HotSpot Java Virtual Machine.

In addition to running Java and JVM-based languages, [GraalVM's language implementation framework (Truffle)](../../truffle/docs/README.md), makes it possible to run JavaScript, Ruby, Python, and a number of other popular languages on the JVM.
With Truffle, Java and other supported languages can directly interoperate with each other and pass data back and forth in the same memory space.

## Runtime Modes

GraalVM Enterprise is unique as a runtime environment offering several modes of operation: JVM runtime mode, Native Image, Java on Truffle (the same Java applications can be run on either).

#### JVM Runtime Mode
When running programs on the HotSpot JVM, GraalVM defaults to the [Graal compiler](../reference-manual/java/compiler.md) as the top-tier JIT compiler.
At runtime, an application is loaded and executed normally on the JVM.
The JVM passes bytecodes for Java or any other JVM-native language to the compiler, which compiles that to the machine code and returns it to the JVM.
Interpreters for supported languages, written on top of the [Truffle framework](../../truffle/docs/README.md), are themselves Java programs that run on the JVM.

#### Native Image
[Native Image](../reference-manual/native-image/README.md) is an innovative technology that compiles Java code into a standalone binary executable or a native shared library.
The Java bytecode that is processed during the native image build includes all application classes, dependencies, third party dependent libraries, and any JDK classes that are required.
A generated self-contained native executable is specific to each individual operating systems and machine architecture that does not require a JVM.

#### Java on Truffle
[Java on Truffle](../reference-manual/java-on-truffle/README.md) is an implementation of the Java Virtual Machine Specification, built with the [Truffle framework](../../truffle/docs/README.md).
It is a complete Java VM that includes all core components, implements the same API as the Java Runtime Environment library, and reuses all JARs and native libraries from GraalVM.

## Available Distributions

GraalVM Enterprise distributions are based on Oracle JDK 11 and 17.
GraalVM Enterprise releases include all Oracle Java critical patch updates (CPUs), which are released on a regular schedule to remedy defects and known vulnerabilities.

GraalVM Enterprise is available for Linux, macOS, and Windows platforms on x86 64-bit systems, and for Linux on ARM 64-bit system.
Depending on the platform, the distributions are shipped as *.tar.gz* or *.zip* archives.

## Certified Platforms

The following are the certified platforms for GraalVM Enterprise 22:

| Operating System 	| Version 	| Architecture 	| Installation Guide 	|
|------------------------------------	|--------------	|--------------	|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	|
| Oracle Linux 	| 7, 8 	| x86 64-bit, ARM 64-bit	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/oci/compute-instances.md) 	|
| Red Hat Enterprise Linux(RHEL) 	| 7, 8 	| x86 64-bit 	| [Installation Guide for Linux](../getting-started/graalvm-enterprise/installation-linux.md) 	|
| macOS 	| 10.14 (Mojave), 10.15 (Catalina)	| x86 64-bit 	| [Installation Guide for macOS](../getting-started/graalvm-enterprise/installation-macos.md) 	|
| Microsoft Windows 	| Server 2016, 2019	| x86 64-bit 	| [Installation Guide for Windows](../getting-started/graalvm-enterprise/installation-windows.md) 	|

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
* [GraalVM Updater](../reference-manual/graalvm-updater.md) to install additional functionalities

### Additional Functionalities
GraalVM Enterprise core installation can be extended with more languages runtimes and utilities.

Tools/Utilities:

* [Native Image](../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native platform executable.
* [LLVM toolchain](../reference-manual/llvm/README.md) --  a set of tools and APIs for compiling native programs to bitcode that can be executed on GraalVM.

Runtimes:

* [Java on Truffle](../reference-manual/java-on-truffle/README.md) -- a JVM implementation built upon the [Truffle framework](../../truffle/docs/README.md) to run Java via a Java bytecode interpreter.
* [Node.js](../reference-manual/js/README.md) -- the Node.js 14.18.1 runtime for JavaScript
* [Python](../reference-manual/python/README.md) -- Python 3.8.5 compatible
* [Ruby](../reference-manual/ruby/README.md) -- Ruby 3.0.2 compatible
* [R](../reference-manual/r/README.md) -- GNU R 4.0.3 compatible
* [GraalWasm](../reference-manual/wasm/README.md) -- WebAssembly (Wasm)

## Licensing and Support

Oracle GraalVM Enterprise Edition is licensed under the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.

For production use, GraalVM Enterprise is available as part of the [Oracle Java SE Subscription](https://www.oracle.com/uk/java/java-se-subscription/) which includes 24x7x365 [Oracle premier support](https://www.oracle.com/support/premier/) and the access to [My Oracle Support (MOS)](https://www.oracle.com/support/).

GraalVM Enterprise focuses on support for Java LTS releases for production deployments.
See [Versions Roadmap of Oracle GraalVM Enterprise Edition](../../release-notes/enterprise/graalvm-enterprise-version-roadmap.md) for more information.

Please note, that while Oracle JDK 17 is available under the new [Oracle No-Fee Terms and Conditions (NFTC) license](https://www.oracle.com/downloads/licenses/no-fee-license.html) which allows commercial and production use for 2 years, GraalVM Enterprise Edition license remains unchanged.

## Experimental and Early Adopter Features

Oracle GraalVM Enterprise Edition features are distributed as fully supported, early adopter, and experimental.

Experimental features are being considered for future versions of GraalVM Enterprise.
They are not meant to be used in production and are not supported by Oracle.
The development team welcomes feedback on experimental features, but users should be aware that experimental features might never be included in a final version, or might change significantly before being considered production-ready.

For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).

The following table lists supported and experimental features in GraalVM Enterprise Edition 22 by platform.

| Feature | Linux AMD64 | Linux ARM64 | macOS | Windows |
|--------------------|---------------|---------------|---------------|
| Native Image | early adopter | early adopter | early adopter | early adopter |
| LLVM runtime | supported | supported | supported | not available |
| LLVM toolchain | supported | supported | supported | not available |
| JavaScript | supported | supported | supported | supported |
| Node.js  | supported | supported | supported | supported |
| Java on Truffle | supported | experimental | experimental | experimental |
| Python | experimental | not available | experimental | not available |
| Ruby | experimental | experimental | experimental | not available |
| R | experimental | not available | experimental | not available |
| WebAssembly | experimental | experimental | experimental | experimental |

## What to Read Next

Users who are new to GraalVM Enterprise or have little experience using it, continue to [Getting Started with GraalVM Enterprise](../getting-started/graalvm-enterprise/get-started-graalvm-enterprise.md).
Download and install GraalVM Enterprise on your local machine, try running the examples provided in the guide, or test GraalVM Enterprise with your workload.
We suggest you then look at more complex [Examples Applications](../examples/examples.md).

Developers, who have GraalVM Enterprise already installed or have experience using it in the past, can skip the getting started guide and proceed to the [Reference Manuals](../reference-manual/reference-manuals.md) for
in-depth coverage of GraalVM Enterprise technologies.
