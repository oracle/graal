---
layout: docs
toc_group: docs
title: Introduction
permalink: /introduction/
redirect_from: /docs/introduction/
---

# Introduction to GraalVM

GraalVM is an advanced JDK with ahead-of-time Native Image compilation.

GraalVM accelerates application performance while consuming fewer resources&mdash;improving application efficiency and reducing IT costs.
It achieves this by compiling your Java application ahead of time into a native binary.
The binary is smaller, starts up to 100x faster, provides peak performance with no warmup, and uses less memory and CPU than an application running on a Java Virtual Machine (JVM).
With profile-guided optimization and the G1 (Garbage-First) garbage collector, you can get lower latency and on-par or better peak performance and throughput compared to an application running on a JVM.

## Key Benefits

GraalVM's key benefits are:

* **Low Resource Usage**: A Java application compiled ahead-of-time by GraalVM requires less memory and CPU to run. No memory and CPU cycles are spent on just-in-time compilation. As a result, your application needs fewer resources to run and is cheaper to operate at scale.
* **Fast Startup**: With GraalVM, you can start your Java application faster by initializing parts of it at build time instead of runtime, and instantly achieve predictable peak performance with no warmup.
* **Compact Packaging**: A Java application compiled ahead-of-time by GraalVM is small and can be easily packaged into a lightweight container image for fast and efficient deployment.
* **Improved Security**: GraalVM reduces the attack surface of your Java application by excluding the following: unreachable code (unused classes, methods, and fields), the just-in-time compilation infrastructure, and build-time initialized code. GraalVM's closed world assumption prevents your application from loading unknown code by disabling dynamic features such as reflection, serialization, and so on at runtime, and requires an explicit include list of such classes, methods, and fields at build time. GraalVM can embed a software bill of materials (SBOM) in your binary, making it easier for you to use common security scanners to check your Java application for published Common Vulnerabilities and Exposures (CVEs).
* **Easily Build Cloud Native Microservices**: Popular microservices frameworks such as Micronaut, Spring Boot, Helidon, and Quarkus, and cloud platforms such as Oracle Cloud Infrastructure (OCI), Amazon Web Services (AWS), Google Cloud Platform (GCP), and Microsoft Azure all support GraalVM. This makes it easy for you to build cloud native Java microservices, compiled as binaries, packaged in small containers, and run on the most popular cloud platforms.
* **Extend your Java Application with Python and Other Languages**: With GraalVM you can embed languages such as Python, JavaScript, and others to extend your Java application.
* **Use Existing Development and Monitoring Tools**: Your existing Java application development and monitoring tools work with GraalVM application binaries. GraalVM provides build plugins for Maven and Gradle, and GitHub Actions for CI/CD. GraalVM supports Java Flight Recorder (JFR), Java Management Extensions (JMX), heap dumps, VisualVM, and other monitoring tools. GraalVM works with existing Java editors/IDEs, and unit test frameworks such as JUnit.

## Licensing and Support

Oracle GraalVM is licensed under [GraalVM Free Terms and Conditions (GFTC) including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graal-free-license.html).
Subject to the conditions in the license, including the License for Early Adopter Versions, the GFTC is intended to permit use by any user including commercial and production use. Redistribution is permitted as long as it is not for a fee.
Oracle GraalVM is also free to use on Oracle Cloud Infrastructure.
For more information about Oracle GraalVM licensing, see the [Oracle Java SE Licensing FAQ](https://www.oracle.com/java/technologies/javase/jdk-faqs.html#GraalVM-licensing).

GraalVM Community Edition is open-source project built from the sources available on [GitHub](https://github.com/oracle/graal) and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](https://github.com/oracle/graal/blob/master/LICENSE), which are the same terms as for Java.
Check the [licenses](https://github.com/oracle/graal#license) of individual GraalVM components which are generally derivative of the license of a particular language and may differ.

### What to Read Next

* Start with the [installation guide](getting-started/get-started.md).
* GraalVM is based on the Java HotSpot Virtual Machine. Read more about [GraalVM as a Java Virtual Machine](reference-manual/java/README.md) and its optimizing just-in-time compiler, [Graal Compiler](reference-manual/java/compiler.md).
* Whether you are new to GraalVM Native Image, or have little experience using it, continue to [Getting Started with Native Image](reference-manual/native-image/README.md). 
We suggest you to take look at [User Guides](reference-manual/native-image/guides/guides.md).
* Developers interested in embedding other languages into Java, proceed directly to the [Embedding Languages documentation](reference-manual/embedding/embed-languages.md).
* Developers interested in building interpreters for programming languages which then run on GraalVM, continue to the [Truffle language implementation framework documentation](../truffle/docs/README.md).
* To learn more about security considerations in GraalVM, check the [Security Guide](security/security-guide.md).
* If you cannot find the answer you need in the available documentation or have a troubleshooting query, ask for help in a [Slack channel](/slack-invitation/) or [submit a GitHub issue](https://github.com/oracle/graal/issues).