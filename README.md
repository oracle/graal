# GraalVM

[![Join the chat at https://gitter.im/graalvm/home](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/graalvm/home?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

GraalVM is a universal virtual machine for running applications written in JavaScript, Python, Ruby, R, JVM-based languages like Java, Scala, Clojure, Kotlin, and LLVM-based languages such as C and C++.

The project website at [https://www.graalvm.org](https://www.graalvm.org) describes how to [get started](https://www.graalvm.org/docs/getting-started/), how to [stay connected](https://www.graalvm.org/community/), and how to [contribute](https://www.graalvm.org/community/contributors/).


## Repository Structure

The GraalVM main source repository includes the following components:

* [GraalVM SDK](sdk/README.md) contains long term supported APIs of GraalVM.

* [GraalVM compiler](compiler/README.md) written in Java that supports both dynamic and static compilation and can integrate with
the Java HotSpot VM or run standalone.

* [Truffle](truffle/README.md) language implementation framework for creating languages and instrumentations for GraalVM.

* [Tools](tools/README.md) contains a set of tools for GraalVM languages
implemented with the instrumentation framework.

* [Substrate VM](substratevm/README.md) framework that allows ahead-of-time (AOT)
compilation of Java applications under closed-world assumption into executable
images or shared objects.

* [Sulong](sulong/README.md) is an engine for running LLVM bitcode on GraalVM.

* [TRegex](regex/README.md) is an implementation of regular expressions which leverages GraalVM for efficient compilation of automata.

* [VM](vm/README.md) includes the components to build a modular GraalVM image.


## Reporting Vulnerabilities

Please report security vulnerabilities not via GitHub issues or the public mailing lists, but via the process outlined at [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).


## Related Repositories

GraalVM allows running of following languages which are being developed and tested in related repositories with GraalVM core to run on top of it using Truffle and the GraalVM compiler. These are:
* [GraalJS](https://github.com/graalvm/graaljs) - JavaScript (ECMAScript 2019 compatible) and Node.js 10.15.2
* [FastR](https://github.com/oracle/fastr) - R Language 3.5.1
* [GraalPython](https://github.com/graalvm/graalpython) - Python 3.7
* [TruffleRuby](https://github.com/oracle/truffleruby/) - Ruby Programming Language 2.6.2
* [SimpleLanguage](https://github.com/graalvm/simplelanguage) - A simple demonstration language for the GraalVM.


## License

Each GraalVM component is licensed:
* [Truffle Framework](/truffle/) and its dependency [GraalVM SDK](/sdk/) are licensed under the [Universal Permissive License](truffle/LICENSE.md).
* [Tools](/tools/) project is licensed under the [GPL 2 with Classpath exception](tools/LICENSE.GPL.md).
* [TRegex](/regex/) project is licensed under the [GPL 2 with Classpath exception](regex/LICENSE.GPL.md).
* [GraalVM compiler](/compiler/) is licensed under the [GPL 2 with Classpath exception](compiler/LICENSE.md).
* [Substrate VM](/substratevm/) is licensed under the [GPL 2 with Classpath exception](substratevm/LICENSE.md).
* [Sulong](/sulong/) is licensed under [3-clause BSD](sulong/LICENSE).
* [VM](/vm/) is licensed under the [GPL 2 with Classpath exception](vm/LICENSE_GRAALVM_CE).
