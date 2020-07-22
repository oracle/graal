# Using Truffle

GraalVM is an open ecosystem and we invite third party systems to participate via connecting their own programming languages, tools, or platforms.
For that purpose we have developed Truffle, a languages and tools development framework.

The Truffle bits are uploaded to Maven central. You can use them from your
`pom.xml` file as:

```xml
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-api</artifactId>
    <version>20.1.0</version> <!-- or any later version -->
</dependency>
<dependency>
    <groupId>org.graalvm.truffle</groupId>
    <artifactId>truffle-dsl-processor</artifactId>
    <version>20.1.0</version>
    <scope>provided</scope>
</dependency>
```

## Implement your own language

The Truffle framework allows you to run programming languages efficiently on GraalVM.
It simplifies language implementation by automatically deriving high-performance code from interpreters.

### Getting started

Information on how to get starting building your language can be found in the Truffle language implementation [tutorial](./LanguageTutorial.md).
The reference API documentation is available as part of the [Truffle javadoc](http://graalvm.org/truffle/javadoc/).
Start with looking at the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) class, which one should subclass to start developing a language.
Truffle comes prebuilt with Graal and several language implementations as as part of [GraalVM](https://www.oracle.com/downloads/graalvm-downloads.html).

A good way to start implementing your own Truffle language is to fork the [SimpleLanguage](https://github.com/graalvm/simplelanguage) project and start hacking.
SimpleLanguage is a relatively small language implementation, well documented and designed to demonstrate most of the Truffle features.
You could also try to get inspired by looking at code of one of existing open source Truffle language [implementations and experiments](./Languages.md).

Reading The Graal/Truffle [publications](../../docs/Publications.md) gives a very detailed view into how many of the aspects of Truffle work.
However, as with any other software project, the source code is the ground truth.

### Advanced topics

Implementing a language using Truffle offers a way to interoperate with other Truffle Languages.
To learn more about verifying that your language is a valid polyglot citizen, read more about using the [Polyglot TCK](./TCK.md).
Somewhat related topics worth exploring are [Truffle Libraries](./TruffleLibraries.md), as well as using them to implementing language [interoperability](./InteropMigration.md).
Truffle languages can also be embedded in Java host applications using the [Polyglot API](https://www.graalvm.org/docs/reference-manual/embed/).

To better understand how to improve the performance of your language please consult our documentation on [profiling](./Profiling.md) and [optimizing](./Optimizing.md) your language.
Also, to better understand how to use Truffle's automated monomorphization feature (a.k.a. splitting), look at the [related documentation](./splitting/README.md).

## Implement your own tool

GraalVM provides a framework for creating language-agnostic tools like debuggers, profilers, or other instrumentations.
In general, GraalVM provides a standardized way to express and run program code enabling cross-language research and the development of tools that are developed once and then can be applied to any language.

The reference API documentation is available as part of the [Truffle javadoc](http://graalvm.org/truffle/javadoc/).
Start with looking at the [TruffleInstrument](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html) class, which one should subclass to start developing a tool.

If you want to implement your own Truffle tool, a good way to start is to fork the [SimpleTool](https://github.com/graalvm/simpletool) project and start hacking.
SimpleTool is a minimalistic code-coverage tool which is well documented and designed to be a starting point for understanding the tool development process using Truffle.

Since Truffle Tools instrument the language using the same AST-node-based approach, most of the techniques available to Truffle language developers in terms of improving performance are available to the tool developers as well.
This is why we encourage you to understand how truffle works from a language developers perspective, in order to get the maximum out of your tool.

## Compatibility

The Truffle API is evolved in a backwards compatible manner from one version to the next.
When API is deprecated, then it will stay deprecated for at least [two GraalVM releases](https://www.graalvm.org/docs/release-notes/version-roadmap/) and a minimum of one month before it will be removed.

As a best practice it is recommended to upgrade Truffle only one version at a time.
This way you can increment the version, fix deprecation warnings to continue with the next version.
The deprecated Javadoc tags on the deprecated APIs are designed to be a guide on how to upgrade.

The latest additions and changes can be seen in the [changelog](../CHANGELOG.md).

## Modifying Truffle

To understand how to modify Truffle, please consult the [Truffle README file](../README.md) and if you would like to contribute to Truffle, consult the [contribution documentation](../CONTRIBUTING.md).

