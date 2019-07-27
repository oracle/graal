# The Truffle Language Implementation Framework

## Introduction

Truffle is an Open Source library for building programming language implementations as interpreters for self-modifying Abstract Syntax Trees.
Together with the Open Source [GraalVM compiler](../compiler), Truffle represents a significant step
forward in programming language implementation technology in the current era of dynamic languages.

A growing  body of shared implementation code and services
reduces language implementation effort significantly, but leads to competitive runtime
performance that matches or exceeds the competition.  The value of the platform is further
increased by support for low-overhead language interoperation, as well as a general instrumentation
framework that supports multi-language debugging and other external developer tools.

Truffle is developed and maintained by Oracle Labs and the Institute for System
Software of the Johannes Kepler University Linz.

## Using Truffle

Information on how to get starting building your language can be found in the Truffle language implementation [tutorial](./docs/LanguageTutorial.md).
The reference API documentation is available as part of the [Truffle javadoc](http://graalvm.org/truffle/javadoc/).
Truffle comes prebuilt with Graal and several language implementations as as part of [GraalVM](https://www.oracle.com/technetwork/graalvm/downloads/index.html).

The Truffle bits are uploaded to Maven central. You can use them from your
`pom.xml` file as:

```xml
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>1.0.0-rc8</version> <!-- or any later version -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>1.0.0-rc8</version>
    <scope>provided</scope>
</dependency>
```

If you want to implement your own Truffle guest language, a good way to start is to fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking.
SimpleLanguage is well documented and designed to demonstrate most of the Truffle features.

*To learn more:*

* Start with a new subclass of [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) for your own language implementation.
* Start with a new subclass of [TruffleInstrument](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html) for your own instrumentation/tool.
* Fork [SimpleLanguage](https://github.com/graalvm/simplelanguage), a toy language that demonstrates how to use most Truffle features.
* Get inspired by looking at code of one of existing open source Truffle language implementations and experiments [here](./docs/Languages.md).
* Embed Truffle languages in Java host applications using the [Polyglot API](http://www.graalvm.org/docs/graalvm-as-a-platform/embed/).
* Read The Graal/Truffle [publications](../docs/Publications.md)
* Verify that your language is a valid polyglot citizen using the [Polyglot TCK](./docs/TCK.md).

## Compatibility

The Truffle API is evolved in a backwards compatible manner from one version to the next.
When API is deprecated, then it will stay deprecated for at least two Truffle releases and a minimum of one month before it will be removed.

As a best practice it is recommended to upgrade Truffle only one version at a time.
This way you can increment the version, fix deprecation warnings to continue with the next version.
The deprecated Javadoc tags on the deprecated APIs are designed to be a guide on how to upgrade.

The latest additions and changes can be seen in the [changelog](./CHANGELOG.md).

## Hacking Truffle

Truffle and Graal use the [MX build tool](https://github.com/graalvm/mx/),
which needs to be installed before using this repository. To do so execute
in a clean directory:

```bash
$ git clone https://github.com/graalvm/mx.git/
$ mx/mx
```

the mx/*mx* command is a wrapper around Python script that serves as our build tool.
Make sure you put it onto your ''PATH'' and then you can work with Truffle
sources from a command line:

```bash
$ mx clean
$ mx build
$ mx unittest
```

The created `./build` directory contains all necessary jars and source bundles.

  - `truffle-api.jar` contains the framework
  - `truffle-dsl-processor.jar` contains the TruffleDSL annotation processor

You can open Truffle sources in your favorite Java IDE by invoking:

```bash
$ mx ideinit
```

the necessary IDE metadata will then be generated into *truffle* subdirectory
and its folders.

*mx* supports Maven integration. To register prebuilt binaries into local Maven
repository you can invoke:

```bash
$ mx build
$ mx maven-install
```

and then it is possible to include the artifacts as dependencies to a `pom.xml`:

```xml
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>1.0.0-rc8</version> <!-- or whether version got installed by mx maven-install -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>1.0.0-rc8</version>
    <scope>provided</scope>
</dependency>
```

## Contributing

You can contact the Truffle developers at graal-dev@openjdk.java.net mailing
list. To contribute a change, verify it using
```bash
$ mx gate
```
and start a [pull request](https://help.github.com/articles/using-pull-requests/).
Detailed info can be found in the [contributing document](CONTRIBUTING.md).

## License

The Truffle framework and the contained SimpleLanguage are licensed under the [Universal Permissive License](LICENSE.md).
