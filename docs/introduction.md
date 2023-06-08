---
layout: docs
toc_group: docs
title: Overview
permalink: /docs/introduction/
redirect_from: /$version/docs/introduction/
---

# GraalVM Overview

GraalVM compiles your Java applications ahead of time into standalone binaries. 
These binaries are smaller, start up to 100x faster, provide peak performance with no warmup, and use less memory and CPU than applications running on a Java Virtual Machine (JVM).

GraalVM reduces the attack surface of your application. 
It excludes unused classes, methods, and fields from the application binary.
It restricts reflection and other dynamic Java language features to build time only. 
It does not load any unknown code at run time.

Popular microservices frameworks such as Spring Boot, Micronaut, Helidon, and Quarkus, and cloud platforms such as Oracle Cloud Infrastructure, Amazon Web Services, Google Cloud Platform, and Microsoft Azure all support GraalVM.

With profile-guided optimization and the G1 (Garbage-First) garbage collector, you can get lower latency and on-par or better peak performance and throughput compared to applications running on a Java Virtual Machine (JVM).

You can use the GraalVM JDK just like any other Java Development Kit in your IDE.

* [Available Distributions](#available-distributions)
* [Features Support](#features-support)
* [Licensing and Support](#licensing-and-support)
* [What to Read Next](#what-to-read-next)

## Available Distributions

GraalVM is available as **Oracle GraalVM** and **GraalVM Community Edition**.
Oracle GraalVM is based on Oracle JDK while GraalVM Community Edition is based on OpenJDK.

GraalVM is available for Linux and macOS on x64 and AArch64 architectures, and for Windows on the x64 architecture. 
See the [installation guide](getting-started/graalvm-community/get-started-graalvm-community.md) for installation instructions.

## Features Support

GraalVM technologies are distributed as _supported_ or _experimental_.

_Experimental_ features are being considered for future versions of GraalVM and are **not** meant to be used in production.
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


For Oracle GraalVM, check the features support list [here](https://docs.oracle.com/en/graalvm/jdk/20/docs/support/#features-support).

## Licensing and Support

Oracle GraalVM is licensed under [Graal Free Terms and Conditions (GFTC) including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html).
Oracle GraalVM is free to use on Oracle Cloud Infrastructure. For more information about Oracle GraalVM licensing, see the [Oracle Java SE Licensing FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html).

GraalVM Community Edition is open-source software built from the sources available on [GitHub](https://github.com/oracle/graal) and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](https://github.com/oracle/graal/blob/master/LICENSE), which are the same terms as for Java.
Check the [licenses](https://github.com/oracle/graal#license) of individual GraalVM components which are generally derivative of the license of a particular language and may differ.
GraalVM Community Edition is free to use for any purpose and comes with no strings attached, but also no guarantees or support.

## What to Read Next

Start with installing GraalVM by following the [installation guide](getting-started/graalvm-community/get-started-graalvm-community.md).

Whether you are new to GraalVM Native Image or have little experience using it, continue to [Getting Started](reference-manual/native-image/README.md).

After that we suggest you to take look at [User Guides](reference-manual/native-image/guides/guides.md).

Developers who have experience using GraalVM and Native Image can proceed to the [Reference Manuals](reference-manual/reference-manuals.md) for in-depth coverage.

To start coding with GraalVM APIs, check the [GraalVM SDK Java API Reference](http://www.graalvm.org/sdk/javadoc).

If you cannot find the answer you need in the available documentation or have a troubleshooting query, you can ask for help in a [Slack channel](/slack-invitation/) or [submit a GitHub issue](https://github.com/oracle/graal/issues).
