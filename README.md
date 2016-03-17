# The Truffle Language Implementation Framework


## Introduction

Truffle is a framework for implementing languages as simple interpreters.
Together with the [Graal compiler](https://wiki.openjdk.java.net/display/Graal/Instructions),
Truffle interpreters are automatically just-in-time compiled and programs
running on top of them can reach performance of normal Java.

The Truffle framework provides the basic foundation for building
abstract-syntax-tree (AST) interpreters that perform
[self-optimizations](http://dx.doi.org/10.1145/2384577.2384587) at runtime. The
included TruffleDSL provides a convenient way to express such optimizations.

Truffle is developed and maintained by Oracle Labs and the Institute for System
Software of the Johannes Kepler University Linz.

## Using Truffle

Truffle official documentation is part of [Truffle javadoc](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/).
It includes description of common use-cases, references to various tutorials,
code snippets and more. In case you want to embedded Truffle into your
application or write your own high speed language interpreter, start
[here](http://lafo.ssw.uni-linz.ac.at/javadoc/truffle/latest/).

Our typicial sample language is called the SimpleLanguage. A good entry point for
exploring SimpleLanguage is the [SLLanguage class](https://github.com/graalvm/Truffle/blob/master/truffle/com.oracle.truffle.sl/src/com/oracle/truffle/sl/SLLanguage.java).
In addition to that here are links to presentations, FAQs and papers about
Graal and Truffle:

  - [Truffle Tutorials and Presentations](https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations)
  - [Truffle FAQ and Guidelines](https://wiki.openjdk.java.net/display/Graal/Truffle+FAQ+and+Guidelines)
  - [Graal VM and Truffle/JS](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index-2301583.html) on the Oracle Technology Network
  - [Papers on Truffle](http://ssw.jku.at/Research/Projects/JVM/Truffle.html)
  - [Papers on Graal](http://ssw.jku.at/Research/Projects/JVM/Graal.html)

## Hacking Truffle

Truffle and Graal use the [MX build tool](https://bitbucket.org/allr/mx),
which needs to be installed before using this repository. To do so execute
in a clean directory:

```bash
$ hg clone https://bitbucket.org/allr/mx
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
  <groupId>com.oracle</groupId>
  <artifactId>truffle</artifactId>
  <version>0.8</version>
</dependency>
<dependency>
  <groupId>com.oracle</groupId>
  <artifactId>truffle-dsl-processor</artifactId>
  <version>0.8</version>
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

The Truffle framework is licensed under the [GPL 2 with Classpath exception](http://openjdk.java.net/legal/gplv2+ce.html).
The SimpleLanguage is licensed under the [Universal Permissive License (UPL)](http://opensource.org/licenses/UPL).
