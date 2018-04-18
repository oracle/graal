# GraalVM

[![Join the chat at https://gitter.im/graalvm/home](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/graalvm/home?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

GraalVM is a universal virtual machine for running applications written in JavaScript, Python, Ruby, R, JVM-based languages like Java, Scala, Kotlin, and LLVM-based languages such as C and C++.

The project website at [https://www.graalvm.org](https://www.graalvm.org) describes how to [get started](https://www.graalvm.org/docs/getting-started/), how to [stay connected](https://www.graalvm.org/community/), and how to [contribute](https://www.graalvm.org/community/contributors/).

## Repository Structure

The GraalVM main source repository includes the following components:

* [Graal SDK](sdk/README.md) contains long term supported APIs of GraalVM.

* [Graal compiler](compiler/README.md) written in Java that supports both dynamic and static compilation and can integrate with
the Java HotSpot VM or run standalone.

* [Truffle](truffle/README.md) language implementation framework for creating languages and instrumentations for GraalVM.

* [Tools](tools/README.md) contains a set of tools for GraalVM languages
implemented with the instrumentation framework.

* [Substrate VM](substratevm/README.md) framework that allows ahead-of-time (AOT)
compilation of Java applications under closed-world assumption into executable
images or shared objects.

* [TRegex](/regex/) is an implementation of regular expressions which leverages GraalVM for efficient compilation of automata.


## License
Each GraalVM component is licensed:
[Truffle](/truffle/) and its dependency [Graal SDK](/sdk/) are licensed under
the [GPL 2 with Classpath exception](truffle/LICENSE.GPL.md)
[Tools](/tools/) project is licensed under the [GPL 2 with Classpath exception](tools/LICENSE.GPL.md)
[TRegex](/regex/) project is licensed under the [GPL 2 with Classpath exception](regex/LICENSE.GPL.md).
The [Graal compiler](/compiler/) is licensed under the [GPL 2](compiler/LICENSE.md)
[Substrate VM](/substratevm/) is licensed under the [GPL 2](substratevm/LICENSE.md)
