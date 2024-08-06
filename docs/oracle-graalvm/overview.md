---
layout: ohc
permalink: /overview/
---

# Overview

Oracle GraalVM is an advanced JDK with ahead-of-time Native Image compilation.

Oracle GraalVM accelerates application performance while consuming fewer resources&mdash;improving application efficiency and reducing IT costs.
It achieves this by compiling your Java application ahead of time into a native binary.
The binary is smaller, starts up to 100x faster, provides peak performance with no warmup, and uses less memory and CPU than an application running on a Java Virtual Machine (JVM).
With profile-guided optimization and the G1 (Garbage-First) garbage collector, you can get lower latency and on-par or better peak performance and throughput compared to an application running on a JVM.

## Key Benefits

Oracle GraalVM's key benefits are:

* **Low Resource Usage**: A Java application compiled ahead of time by Oracle GraalVM requires less memory and CPU to run. No memory and CPU cycles are spent on just-in-time compilation. As a result, your application needs fewer resources to run and is cheaper to operate at scale.
* **Fast Startup**: With Oracle GraalVM, you can start your Java application faster by initializing parts of it at build time instead of runtime, and instantly achieve predictable peak performance with no warmup.
* **Compact Packaging**: A Java application compiled ahead of time by Oracle GraalVM is small and can be easily packaged into a lightweight container image for fast and efficient deployment.
* **Improved Security**: Oracle GraalVM reduces the attack surface of your Java application by excluding the following: unreachable code (unused classes, methods, and fields), the just-in-time compilation infrastructure, and build-time initialized code. Oracle GraalVM's closed world assumption prevents your application from loading unknown code by disabling dynamic features such as reflection, serialization, and so on at runtime, and requires an explicit include list of such classes, methods, and fields at build time. Oracle GraalVM can embed a software bill of materials (SBOM) in your binary, making it easier for you to use common security scanners to check your Java application for published Common Vulnerabilities and Exposures (CVEs).
* **Easily Build Cloud Native Microservices**: Popular microservices frameworks such as Micronaut, Spring Boot, Helidon, and Quarkus, and cloud platforms such as Oracle Cloud Infrastructure (OCI), Amazon Web Services (AWS), Google Cloud Platform (GCP), and Microsoft Azure all support Oracle GraalVM. This makes it easy for you to build cloud native Java microservices, compiled as binaries, packaged in small containers, and run on the most popular cloud platforms.
* **Extend your Java Application with Python and Other Languages**: With Oracle GraalVM you can embed languages such as Python, JavaScript, and others to extend your Java application.
* **Use Existing Development and Monitoring Tools**: Your existing Java application development and monitoring tools work with application binaries. Oracle GraalVM provides build plugins for Maven and Gradle, and GitHub Actions for CI/CD. Oracle GraalVM supports Java Flight Recorder (JFR), Java Management Extensions (JMX), heap dumps, VisualVM, and other monitoring tools. Oracle GraalVM works with existing Java editors/IDEs, and unit test frameworks such as JUnit.

### What to Read Next

* Start with installing Oracle GraalVM by following the [installation guide](../getting-started/get-started.md).
* Whether you are new to Oracle GraalVM and Native Image or have little experience using it, continue to [Getting Started with Native Image](../reference-manual/native-image/README.md).
* Developers who already have Oracle GraalVM installed, or have experience using it, should proceed to the [Reference Manuals](../reference-manual/reference-manuals.md) for in-depth coverage.