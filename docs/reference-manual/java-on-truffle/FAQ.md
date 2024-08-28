---
layout: docs
toc_group: espresso
link_title: FAQ
permalink: /reference-manual/espresso/faq/
redirect_from: /reference-manual/java-on-truffle/faq/
---

# Frequently Asked Questions

### Does Java running on Truffle implement the Java language running as a Truffle interpreter?

Not quite: it implements the Java Virtual Machine running as a Truffle interpreter.
That means it can only run a Java program once it has been compiled to Java bytecode (classes, JAR files, and so on) with your favorite Java compiler (for example, `javac`) or a build tool (Maven, Gradle, and so on).
In the GraalVM family, this is similar to WebAssembly or the LLVM interpreter: while both can run C programs, they have to be compiled by a C compiler first.

### Does Java running on Truffle run on the HotSpot JVM too?

Like other languages implemented with the [Truffle framework](../../../truffle/docs/README.md), it can run both as a native executable or on the HotSpot JVM.
Running on the HotSpot JVM is currently only possible on Linux x64 and macOS x64.
We plan to extend this capability to other platforms.

### Does running Espresso require the HotSpot JVM?

No, it doesn't, it works fine as a native executable.
Espresso does require a standard core Java library (the _rt.jar_ library for Java 8 or the `lib/modules` file for Java 11+, as well as the associated native libraries: `libjava`, `libnio`, and so on).

### Running Java on GraalVM already brings the highest level of optimization, what benefits will Espresso give me?

- Espresso inherits the extensive tooling provided by the Truffle framework. This means that for code coverage and profiling you would no longer need to rely on external tools.
- Another important aspect is that Espresso comes with improved isolation of the host Java VM and the Java program running on Truffle.
- Moreover, Espresso can run in the context of a native executable while still allowing dynamically-loaded bytecode!
- Finally, you can enjoy the benefits of enhanced HotSwap capabilities which will help boost your productivity.

### What is the license for Espresso?

Espresso is an implementation of the Java Virtual Machine. It is open source and is offered as free software under the [GNU General Public License version two (GPLv2)](https://github.com/oracle/graal/blob/master/tools/LICENSE).

### Can I run Espresso in production?

Yes, you can on Linux x64. Support for other platforms is still experimental.

### What performance can I expect from executing Espresso?

Performance is currently 2-3x slower than the HotSpot JVM.
It does not match the speed offered by GraalVM yet for sure, but having created a fully-working Espresso runtime, the development team is now focusing on making it as performant as the GraalVM JIT.

### Can I embed Java running on Truffle in my application?

Yes, you can use [GraalVM's Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) to run Java bytecode in a separate context from the host Java VM.
You can even embed a Java 8 context in a Java 11, 17, or 21 application (using the option `--java.JavaHome=/path/to/jdk8`).

### Why do I see "Unrecognized option: -javaagent:.../idea_rt.jar..." when I try to run my app from the IDE?

It is not possible to attach an agent to Espresso. For the time being, add `-XX:+IgnoreUnrecognizedVMOptions` to the VM options.