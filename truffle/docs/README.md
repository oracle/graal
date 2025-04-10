---
layout: docs
toc_group: truffle
link_title: Truffle Language Implementation Framework
permalink: /graalvm-as-a-platform/language-implementation-framework/
---
# Truffle Language Implementation Framework

The Truffle language implementation framework (Truffle) is an open source library for building tools and programming languages implementations as interpreters for self-modifying Abstract Syntax Trees.
Together with the open source [Graal compiler](https://github.com/oracle/graal/tree/master/compiler), Truffle represents a significant step forward in programming language implementation technology in the current era of dynamic languages.

The Truffle artifacts are uploaded to [Maven Central - Sonatype](https://central.sonatype.com/artifact/org.graalvm.truffle/truffle-api){:target="_blank"}.
You can use them from your `pom.xml` file as:

```xml
<properties>
    <graalvm.version>24.2.0</graalvm.version> <!-- or any later version -->
</properties>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>${graalvm.version}</version> <!-- or any later version -->
</dependency>
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.graalvm.truffle</groupId>
                        <artifactId>truffle-dsl-processor</artifactId>
                        <version>${graalvm.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Implement Your Language

The Truffle framework allows you to run programming languages efficiently on GraalVM.
It simplifies language implementation by automatically deriving high-performance code from interpreters.

### Getting Started

We provide extensive [Truffle API documentation](http://graalvm.org/truffle/javadoc/).
Start by looking at the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class, which you should subclass to start developing a language.
Truffle comes with the Graal Compiler and several language implementations as part of GraalVM.

A good way to start implementing your language with Truffle is to fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking.
SimpleLanguage is a relatively small language implementation, well-documented, and designed to demonstrate most of the Truffle features.
You could also try by looking at code in one of the existing open source languages [implementations and experiments](./Languages.md).

### Advanced Topics

Implementing a language using Truffle offers a way to interoperate with other "Truffle" languages.
To estimate if your language is a valid polyglot citizen, read about using the [Polyglot API-based Test Compatibility Kit](./TCK.md).
Somewhat related topics worth exploring are [Truffle Libraries](./TruffleLibraries.md), as well as how to use them to implement a [language interoperability](./InteropMigration.md).
Languages implemented with Truffle can also be embedded in Java host applications using the [Polyglot API](../../docs/reference-manual/embedding/embed-languages.md).

To better understand how to improve the performance of your language, see the documentation on [Profiling Truffle Interpreters](./Profiling.md) and [Optimizing Truffle Interpreters](./Optimizing.md).
Also, to better understand how to use Truffle's automated monomorphization feature (for example, splitting), look at the [related documentation](./splitting/Monomorphization.md).

## Implement Your Tool

With the Truffle framework, you can develop language-agnostic tools such as debuggers, profilers, and other instrumentations.
Start with looking at the [TruffleInstrument](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html) class, which -- similar to `TruffleLanguage` -- one should subclass to start developing a tool.

If you want to implement your own "Truffle" tool, a good way to start is to fork the [SimpleTool](https://github.com/graalvm/simpletool) project -- like the SimpleLanguage project described above -- and start hacking.
SimpleTool is a well-documented, minimalistic code-coverage tool designed to be a starting point for understanding the tool development process using Truffle.

Since tools, developed with Truffle, instrument the language using the same AST-node-based approach, most of the techniques available to language developers, in terms of improving performance, are available to the tool developers as well.
This is why it is recommended that you understand how Truffle works from a language developer's perspective to get the maximum out of your tool.

## Compatibility

The Truffle API is evolved in a backwards-compatible manner from one version to the next.
When an API is deprecated, then it will stay deprecated for at least [two GraalVM releases](https://www.graalvm.org/release-calendar/) before it will be removed.

As a best practice it is recommended to upgrade Truffle only one version at a time.
This way you can increment the version and fix deprecation warnings before continuing to the next version.
The deprecated Javadoc tags on the deprecated APIs are designed to be a guide on how to upgrade.

The latest additions and changes can be seen in the [changelog](https://github.com/oracle/graal/blob/master/truffle/CHANGELOG.md).

## Modifying Truffle

To understand how to modify Truffle, check [this file](https://github.com/oracle/graal/blob/master/truffle/README.md).
If you would like to contribute to Truffle, consult the [contribution documentation](https://github.com/oracle/graal/blob/master/truffle/CONTRIBUTING.md).
