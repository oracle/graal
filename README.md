# GraalVM

[![https://graalvm.slack.com](https://img.shields.io/badge/slack-join%20channel-inactive)](https://join.slack.com/t/graalvm/shared_invite/enQtNzk0NTc5MzUyNzg5LTAwY2YyODQ4MzJjMGJjZGQzMWY2ZDA3NWI3YzEzNDRlNGQ1MTZkYzkzM2JkYjIxMTY2NGQzNjUxOGQzZGExZmU)

GraalVM is a universal virtual machine for running applications written in
JavaScript, Python, Ruby, R, JVM-based languages like Java, Scala, Clojure,
Kotlin, and LLVM-based languages such as C and C++. It offers the following
benefits:

* **Performance**: GraalVM leverages years of research into compiler technology to give you better
peak performance on average than any other JVM.
* **Ahead-of-time compilation**: Ahead-of-time (AOT) compiled native images improve application start-up time and
reduce memory footprint.
* **Interoperability**: Combining programming languages in the same runtime maximizes your resources and
increases code efficiency. Use whichever programming language is best fit for
purpose, in any combination. Match the correct code to the use case you need.
* **Embeddability**: GraalVM Polyglot SDK removes isolation between programming languages and gives
you a next-generation runtime environment where you no longer need to write
separate applications to use different languages.
* **Tooling**: GraalVM takes advantage of JVM-based tooling and provides a common set of tools,
such as debugging and profiling, that you can use for all your code.

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

* [VS Code](/vscode/README.md) provides extensions to Visual Studio Code that support development of polyglot applications using GraalVM.

## Get Support

* Open a [GitHub issue](https://github.com/oracle/graal/issues) for bug reports, questions, or requests for enhancements.
* Report a security vulnerability to [secalert_us@oracle.com](mailto:secalert_us@oracle.com). For additional information see [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).


## Related Repositories

GraalVM allows running of following languages which are being developed and tested in related repositories with GraalVM core to run on top of it using the Truffle framework and the GraalVM compiler. These are:
* [GraalJS](https://github.com/graalvm/graaljs) - JavaScript (ECMAScript 2019 compatible) and Node.js 10.16.3
* [FastR](https://github.com/oracle/fastr) - R Language 3.6.1
* [GraalPython](https://github.com/graalvm/graalpython) - Python 3.7
* [TruffleRuby](https://github.com/oracle/truffleruby/) - Ruby Programming Language 2.6.2
* [SimpleLanguage](https://github.com/graalvm/simplelanguage) - A simple demonstration language for the GraalVM.


## License

Each GraalVM component is licensed:
* [Truffle Framework](/truffle/) and its dependency [GraalVM SDK](/sdk/) are licensed under the [Universal Permissive License](truffle/LICENSE.md).
* [Tools](/tools/) project is licensed under the [GPL 2 with Classpath exception](tools/LICENSE).
* [TRegex](/regex/) project is licensed under the [GPL 2 with Classpath exception](regex/LICENSE.GPL.md).
* [GraalVM compiler](/compiler/) is licensed under the [GPL 2 with Classpath exception](compiler/LICENSE.md).
* [Substrate VM](/substratevm/) is licensed under the [GPL 2 with Classpath exception](substratevm/LICENSE).
* [Sulong](/sulong/) is licensed under [3-clause BSD](sulong/LICENSE).
* [VM](/vm/) is licensed under the [GPL 2 with Classpath exception](vm/LICENSE_GRAALVM_CE).
* [VS Code](/vscode/) extensions are distributed under the [UPL 1.0 license](/vscode/graalvm/LICENSE.txt).
