# The Truffle Language Implementation Framework


## Introduction

Truffle is a framework for implementing languages as simple interpreters.
Together with the [Graal compiler](http://github.com/OracleLabs/GraalVM),
Truffle interpreters are automatically just-in-time compiled and programs
running on top of them can reach performance of normal Java.

The Truffle framework provides the basic foundation for building
abstract-syntax-tree (AST) interpreters that perform
[self-optimizations](http://dx.doi.org/10.1145/2384577.2384587) at runtime. The
included TruffleDSL provides a convenient way to express such optimizations.

Truffle is developed and maintained by Oracle Labs and the Institute for System
Software of the Johannes Kepler University Linz.


## Building and Using Truffle

Truffle and Graal use the [MX build tool](https://bitbucket.org/allr/mxtool2),
which is part of this repository. To build Truffle execute:

```bash
./mx.sh build
```

The created `./build` directory contains all necessary jars and source bundles.

  - `truffle-api.jar` contains the framework
  - `truffle-dsl-processor.jar` contains the TruffleDSL annotation processor

### Maven

For Maven based projects, prebuilt binaries can be included into a project by
adding the following dependencies to a `pom.xml`:

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

## Resources and Documentation

This repository contains the SimpleLanguage, which comes with JavaDoc
documentation to demonstrate how Truffle is used. A good entry point for
exploring SimpleLanguage is the [SLLanguage class](https://github.com/OracleLabs/Truffle/blob/master/truffle/com.oracle.truffle.sl/src/com/oracle/truffle/sl/SLLanguage.java).

  - [Truffle Tutorials and Presentations](https://wiki.openjdk.java.net/display/Graal/Publications+and+Presentations)
  - [Truffle FAQ and Guidelines](https://wiki.openjdk.java.net/display/Graal/Truffle+FAQ+and+Guidelines)
  - [Graal VM and Truffle/JS](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index-2301583.html) on the Oracle Technology Network
  - [Papers on Truffle](http://ssw.jku.at/Research/Projects/JVM/Truffle.html)
  - [Papers on Graal](http://ssw.jku.at/Research/Projects/JVM/Graal.html)

## Contributing

TODO


## License

The Truffle framework is licensed under the [GPL 2 with Classpath exception](http://openjdk.java.net/legal/gplv2+ce.html).
The SimpleLanguage is licensed under the [Universal Permissive License (UPL)](http://opensource.org/licenses/UPL).
