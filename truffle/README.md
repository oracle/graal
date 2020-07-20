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

If you are looking for documentation on how to use truffle, please consult the [user documentation](docs/README.md).

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

The created `./mxbuild/dists` directory contains all necessary jars and source bundles.

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
    <version>20.0.0</version> <!-- or whether version got installed by mx maven-install -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>20.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Contributing

To contribute a change, verify it using

```bash
$ mx gate
```
and start a [pull request](https://help.github.com/articles/using-pull-requests/).
Detailed info can be found in the [contributing document](CONTRIBUTING.md).

## Community

To reach out to the Truffle community (as well as the wider GraalVM community) consider the information available at https://www.graalvm.org/community/.
There is a dedicated Truffle channel (`#truffle`) on the GraalVM community slack (https://www.graalvm.org/slack-invitation/).

## License

The Truffle framework and the contained SimpleLanguage are licensed under the [Universal Permissive License](LICENSE.md).
