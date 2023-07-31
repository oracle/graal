---
layout: docs
toc_group: docs
title: Overview
permalink: /docs/introduction/
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

## Licensing and Support

Oracle GraalVM is licensed under [GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html).
Subject to the conditions in the license, including the License for Early Adopter Versions, the GFTC is intended to permit use by any user including commercial and production use. Redistribution is permitted as long as it is not for a fee.
Oracle GraalVM is also free to use on Oracle Cloud Infrastructure.
For more information about Oracle GraalVM licensing, see the [Oracle Java SE Licensing FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html#GraalVM-licensing).

GraalVM Community Edition is open-source software built from the sources available on [GitHub](https://github.com/oracle/graal) and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](https://github.com/oracle/graal/blob/master/LICENSE), which are the same terms as for Java.
Check the [licenses](https://github.com/oracle/graal#license) of individual GraalVM components which are generally derivative of the license of a particular language and may differ.

## What to Read Next

Start with installing GraalVM by following the [installation guide](getting-started/graalvm-community/get-started-graalvm-community.md).

Whether you are new to GraalVM Native Image or have little experience using it, continue to [Getting Started](reference-manual/native-image/README.md).

After that we suggest you to take look at [User Guides](reference-manual/native-image/guides/guides.md).

Developers who have experience using GraalVM and Native Image can proceed to the [Reference Manuals](reference-manual/reference-manuals.md) for in-depth coverage.

To start coding with GraalVM APIs, check the [GraalVM SDK Java API Reference](http://www.graalvm.org/sdk/javadoc).

If you cannot find the answer you need in the available documentation or have a troubleshooting query, you can ask for help in a [Slack channel](/slack-invitation/) or [submit a GitHub issue](https://github.com/oracle/graal/issues).
