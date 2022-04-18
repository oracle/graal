---
layout: docs
toc_group: truffle
link_title: Truffle Language Implementation Framework
permalink: /graalvm-as-a-platform/language-implementation-framework/
---
# Truffle Language Implementation Framework

The Truffle language implementation framework (Truffle) is an open source library for building tools and programming languages implementations as interpreters for self-modifying Abstract Syntax Trees.
Together with the open source [Graal compiler](https://github.com/oracle/graal/tree/master/compiler), Truffle represents a significant step forward in programming language implementation technology in the current era of dynamic languages.

The Truffle bits are uploaded to [Maven central](https://mvnrepository.com/artifact/org.graalvm.truffle). 
You can use them from your `pom.xml` file as:

```xml
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>22.1.0</version> <!-- or any later version -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>22.1.0<</version>
    <scope>provided</scope>
</dependency>
```

## Implement Your Language

The Truffle framework allows you to run programming languages efficiently on GraalVM.
It simplifies language implementation by automatically deriving high-performance code from interpreters.

### Getting Started

Information on how to get starting building your language can be found in the [Language Implementation Tutorial](./LanguageTutorial.md).
The reference API documentation is available as part of the [Truffle Javadoc](http://graalvm.org/truffle/javadoc/).
Start with looking at the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class, which one should subclass to start developing a language.
Truffle comes prebuilt with the Graal Compiler and several language implementations as part of GraalVM.

A good way to start implementing your language with Truffle is to fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking.
SimpleLanguage is a relatively small language implementation, well-documented, and designed to demonstrate most of the Truffle features.
You could also try by looking at code in one of the existing open source languages [implementations and experiments](./Languages.md).

Consider reading [these publications](https://github.com/oracle/graal/blob/master/docs/Publications.md) for a very detailed view into how many of the aspects of Truffle work. However, as with any other software project, the source code is the ground truth.

### Advanced Topics

Implementing a language using Truffle offers a way to interoperate with other "Truffle" languages.
To learn more about verifying that your language is a valid polyglot citizen, read more about using the [Polyglot TCK](./TCK.md).
Somewhat related topics worth exploring are [Truffle Libraries](./TruffleLibraries.md), as well as how to use them to implement a language [interoperability](./InteropMigration.md).
Languages implemented with Truffle can also be embedded in Java host applications using the [Polyglot API](../../docs/reference-manual/embedding/embed-languages.md).

To better understand how to improve the performance of your language please consult the documentation on [profiling](./Profiling.md) and [optimizing](./Optimizing.md) your language.
Also, to better understand how to use Truffle's automated monomorphization feature (i.e., splitting), look at the [related documentation](./splitting/Monomorphization.md).

## Implement Your Tool

GraalVM provides a framework for creating language-agnostic tools like debuggers, profilers, and other instrumentations.
In general, GraalVM provides a standardized way to express and run program code enabling cross-language research and the development of tools that can be developed once and then applied to any language.

The reference API documentation is available as part of the [Truffle Javadoc](http://graalvm.org/truffle/javadoc/).
Start with looking at the [TruffleInstrument](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html) class, which -- similar to `TruffleLanguage` -- one should subclass to start developing a tool.

If you want to implement your own "Truffle" tool, a good way to start is to fork the [SimpleTool](https://github.com/graalvm/simpletool) project -- like the SimpleLanguage project described above -- and start hacking.
SimpleTool is a well-documented, minimalistic code-coverage tool designed to be a starting point for understanding the tool development process using Truffle.

Since tools, developed with Truffle, instrument the language using the same AST-node-based approach, most of the techniques available to language developers in terms of improving performance are available to the tool developers as well.
This is why it is recommended that you understand how Truffle works from a language developer's perspective, in order to get the maximum out of your tool.

## Compatibility

The Truffle API is evolved in a backwards-compatible manner from one version to the next.
When an API is deprecated, then it will stay deprecated for at least [two GraalVM releases](https://www.graalvm.org/release-notes/version-roadmap/), and a minimum of one month, before it will be removed.

As a best practice it is recommended to upgrade Truffle only one version at a time.
This way you can increment the version and fix deprecation warnings before continuing to the next version.
The deprecated Javadoc tags on the deprecated APIs are designed to be a guide on how to upgrade.

The latest additions and changes can be seen in the [changelog](https://github.com/oracle/graal/blob/master/truffle/CHANGELOG.md).

## Modifying Truffle

To understand how to modify Truffle, consult [this file](https://github.com/oracle/graal/blob/master/truffle/README.md), and if you would like to contribute to Truffle, consult the [contribution documentation](https://github.com/oracle/graal/blob/master/truffle/CONTRIBUTING.md).
