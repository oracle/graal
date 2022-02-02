---
layout: docs-experimental
toc_group: espresso
link_title: FAQ
permalink: /reference-manual/java-on-truffle/faq/
---

# Frequently Asked Questions

### Does Java running on Truffle implement the Java language running as a Truffle interpreter?

Not quite: it implements the Java Virtual Machine running as a Truffle interpreter.
That means it can only run a Java program once it has been compiled to Java bytecode (classes, JARs, etc.) with your favorite Java compiler (e.g., `javac`) or a build tool (Maven, Gradle, etc.).
In the GraalVM family, this is similar to WebAssembly or the LLVM interpreter: while both can run C programs, they have to be complied by a C compiler first.

### Does Java running on Truffle run on HotSpot too?
Like other languages implemented with the [Truffle framework](../../../truffle/docs/README.md), it can run both as a native image or on top of HotSpot.
Running on top of HotSpot is currently only possible on Linux.
We plan to extend this capability to macOS and Windows platforms also.

### Does running Java on Truffle require HotSpot?
No, it doesn't, it works fine as a native image.
Java on Truffle does require a standard core Java library (the `rt.jar` library for Java 8 or the `lib/modules` file for Java 11 and Java 17 as well as the associated native libraries: `libjava`, `libnio`, etc.)

### Running Java on GraalVM already brings the highest level of optimization, what benefits will Java on Truffle give me?
- Java on Truffle will inherit the extensive tooling provided by the Truffle framework. This means that for the things like code coverage and profiling you would no longer need to rely on external tools.
- Another important aspect is that Java on Truffle comes with improved isolation of the host Java VM and the Java program running on Truffle.
- Moreover, Java on Truffle can run in the context of a native image while still allowing dynamically-loaded bytecodes!
- Finally, you can enjoy the benefits of enhanced HotSwap capabilities which will help boost your productivity.

### What is the license for Java on Truffle?
Java on Truffle is an implementation of the Java Virtual Machine. It is open source and is offered as free software under the [GNU General Public License version two (GPLv2)](https://github.com/oracle/graal/blob/master/tools/LICENSE).

### Can I run Java on Truffle in production?
Running in production is not recommended.
While Java on Truffle already passes the Java Compatibility Kit (JCK or TCK for Java SE) 8, 11 and 17 runtimes, it is still an early prototype and experimental feature in GraalVM.
It may undergo significant improvements before being considered production-ready.

### What performance can I expect from executing Java on Truffle?
Performance is currently 2-3x slower than HotSpot.
It does not match the speed offered by GraalVM yet for sure, but having created a fully working Java on Truffle runtime, the development team is now focusing on making it as performant as the GraalVM JIT.

### Can I embed Java running on Truffle in my application?
Yes, you can use [GraalVM's Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) to run Java bytecodes in a separate context from the host Java VM.
You can even embed a Java 8 context in a Java 11 or Java 17 application!

### Why do I see "Unrecognized option: -javaagent:.../idea_rt.jar..." when I try to run my app from the IDE?
Java on Truffle does not yet support attaching Java agents. For the time being add: `-XX:+IgnoreUnrecognizedVMOptions` to the VM options too.
