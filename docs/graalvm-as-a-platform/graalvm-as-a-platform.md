---
layout: docs
link_title: GraalVM as a Platform
permalink: /graalvm-as-a-platform/
redirect_from: /docs/graalvm-as-a-platform/
toc_group: graalvm-as-a-platform
---

# GraalVM as a Platform

GraalVM is an open ecosystem and allows users to implement a custom language or tool on top of it with the [Truffle language implementation framework](../../truffle/docs/README.md) which offers APIs for writing interpreters for programming languages in the form of Java programs.

GraalVM loads and runs the Truffle framework, which itself is a Java program -- a collection of JAR files -- together with interpreters.
These get optimized at runtime into efficient machine code for executing loaded programs.

Learn more about this framework from its [reference documentation](../../truffle/docs/README.md).

## Implement Your Language

With the [Language API](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/package-summary.html) offered by the Truffle framework, you can implement a language interpreter on top of GraalVM.

To get started, proceed to [Implement Your Language](implement-language.md).

## Implement Your Tool

With the [Instrument API](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/package-summary.html) offered by the Truffle framework, you can create language-agnostic tools like debuggers, profilers, or other instruments on top of GraalVM.

To get started, proceed to [Implement Your Tool](implement-instrument.md).
