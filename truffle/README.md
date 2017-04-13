# The Truffle Language Implementation Framework


## Introduction

Truffle is a framework for implementing languages as simple interpreters.
Together with the [Graal compiler](https://github.com/graalvm/graal-core/),
Truffle interpreters are automatically just-in-time compiled and programs
running on top of them can reach performance of normal Java.

The Truffle framework provides the basic foundation for building
abstract-syntax-tree (AST) interpreters that perform
[self-optimizations](http://dx.doi.org/10.1145/2384577.2384587) at runtime. The
included TruffleDSL provides a convenient way to express such optimizations.

Truffle is developed and maintained by Oracle Labs and the Institute for System
Software of the Johannes Kepler University Linz.

## Using Truffle

Truffle official documentation is part of [Truffle javadoc](http://graalvm.github.io/truffle/javadoc/).
It includes description of common use-cases, references to various tutorials,
code snippets and more. In case you want to embedded Truffle into your
application or write your own high speed language interpreter, start by
downloading [GraalVM](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/)
(which contains all the necessary pre-built components) and then follow to the
[javadoc overview](http://graalvm.github.io/truffle/javadoc/).

Truffle bits are uploaded to Maven central. You can use them from your
`pom.xml` file as:

```xml
<dependency>
    <groupId>com.oracle.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>0.23</version> <!-- or any later version -->
</dependency>
<dependency>
    <groupId>com.oracle.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>0.23</version>
    <scope>provided</scope>
</dependency>
```

If you want to implement your own Truffle guest language, a good way to start is to fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking.
SimpleLanguage is well documented and designed to demonstrate most of the Truffle features.

Important links to resources that are available:
  - [Truffle javadoc overview](http://graalvm.github.io/truffle/javadoc/)
  - [SimpleLanguage example](https://github.com/graalvm/simplelanguage)
  - [Tutorials and publications on Truffle](https://github.com/graalvm/truffle/blob/master/docs/Publications.md)
  - [Tutorials and publications on Graal](https://github.com/graalvm/graal-core/blob/master/docs/Publications.md)
  - [Graal VM download](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index-2301583.html) on the Oracle Technology Network


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
    <groupId>com.oracle.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>0.18-SNAPSHOT</version> <!-- or whether version got installed by mx maven-install -->
</dependency>
<dependency>
    <groupId>com.oracle.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>0.18-SNAPSHOT</version>
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

The Truffle framework is licensed under the [GPL 2 with Classpath exception](https://github.com/graalvm/truffle/blob/master/LICENSE.GPL.md).
The SimpleLanguage is licensed under the [Universal Permissive License (UPL)](http://opensource.org/licenses/UPL).
