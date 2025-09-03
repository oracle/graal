# Truffle Language Implementation Framework

## Introduction

The Truffle language implementation framework (Truffle) is an open-source library for building programming language implementations as interpreters for self-modifying Abstract Syntax Trees.
Together with the open-source [Graal compiler](https://github.com/oracle/graal/tree/master/compiler), Truffle represents a significant step forward in programming language implementation technology in the current era of dynamic languages.

A growing body of shared implementation code and services reduces language implementation effort significantly, but leads to runtime performance that matches or exceeds the competition.
The value of the platform is further increased by support for low-overhead language interoperability, as well as a general instrumentation framework that supports multilanguage debugging and other external developer tools.

Truffle is developed and maintained by Oracle and the Institute for System Software of the Johannes Kepler University Linz.

## Using Truffle

If you are looking for documentation on how to use Truffle, please consult the documentation [here](docs/README.md) or [on the website](https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/).

## Hacking Truffle

Truffle and the Graal compiler use the [MX build tool](https://github.com/graalvm/mx/), which needs to be installed before using this repository.
To install it, run these commands in a clean directory:
```bash
$ git clone https://github.com/graalvm/mx.git/
$ mx/mx
```

The `mx` command is a wrapper around the Python script that serves as a build tool.
Make sure you put it onto your `PATH` and then you can work with Truffle sources from a command line:
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

The necessary IDE metadata will be generated into _truffle/_ subdirectory
and its directories.

The `mx` tool supports Maven integration.
To register prebuilt binaries into your local Maven repository, run:
```bash
$ mx build
$ mx maven-install
```

Then it is possible to add Truffle artifacts as dependencies to the Maven configuration file, _pom.xml_:
```xml
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>24.2.0</version> <!-- or whether version got installed by mx maven-install -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>24.2.0</version>
    <scope>provided</scope>
</dependency>
```

## Contributing

To contribute a change, verify it using:
```bash
$ mx gate
```
Then create a [pull request](https://help.github.com/articles/using-pull-requests/).
Detailed information can be found in the [contributing document](CONTRIBUTING.md).

## Community

To reach out to the Truffle community (as well as the wider GraalVM community) consider the information available at the [GraalVM website](https://www.graalvm.org/community/).
There is a dedicated Truffle channel (`#truffle`) on the [GraalVM community slack](https://www.graalvm.org/slack-invitation/).

## License

The Truffle framework and the contained SimpleLanguage are licensed under the [Universal Permissive License](LICENSE.md).
