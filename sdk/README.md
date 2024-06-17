# GraalVM Standard Development Kit

The GraalVM SDK is a collection of APIs for the components of GraalVM.

* [`org.graalvm.nativeimage`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/package-summary.html): The Native Image public API for advanced use cases.
* [`org.graalvm.polyglot`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html): A library that allows embedding of polyglot language implementations in Java. 
* [`org.graalvm.word`](https://www.graalvm.org/sdk/javadoc/org/graalvm/word/package-summary.html): A low-level library for machine-word-sized values in Java.
* [`org.graalvm.collections`](https://www.graalvm.org/sdk/javadoc/org/graalvm/collections/package-summary.htmlyes): A collections library for GraalVM components.

## Getting Started

To get started, download and install GraalVM for your operating system as described in the [installation guide](https://www.graalvm.org/latest/docs/getting-started/).
The `org.graalvm.nativeimage`, `org.graalvm.word`, and  `org.graalvm.collection` modules from the GraalVM SDK are included in a GraalVM JDK and can be used like any other module.

The GraalVM SDK bits are also available on Maven central.
Add these module dependencies to your Maven project configuration file:

```xml
<dependency>
  <groupId>org.graalvm.sdk</groupId>
  <artifactId>nativeimage</artifactId>
  <version>${graalvm.version}</version>
</dependency>
<dependency>
  <groupId>org.graalvm.sdk</groupId>
  <artifactId>word</artifactId>
  <version>${graalvm.version}</version>
</dependency>
<dependency>
  <groupId>org.graalvm.sdk</groupId>
  <artifactId>collections</artifactId>
  <version>${graalvm.version}</version>
</dependency>
```

The `org.graalvm.polyglot` module is not contained by default in the GraalVM JDK. 
To enable a language embedding in Java, specify the language as a dependency. 
If you need the Truffle tools for your polyglot application, enable them through a dependency too. 
Below is an example of the Maven configuration:

```xml
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>polyglot</artifactId>
  <version>${graalvm.polyglot.version}</version>
</dependency>
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>js|python|ruby|wasm|llvm|java</artifactId> 
  <version>${graalvm.polyglot.version}</version>
  <type>pom</type>
</dependency>
<dependency>
  <groupId>org.graalvm.polyglot</groupId>
  <artifactId>tools</artifactId>
  <version>${graalvm.polyglot.version}</version>
  <type>pom</type>
</dependency>
```

## Learn More

* [Embedding Languages documentation](https://www.graalvm.org/latest/reference-manual/embed-languages/) to learn how to use the Polyglot API to embed GraalVM languages in Java host applications.
* [Polyglot Embedding Demonstration on GitHub](https://github.com/graalvm/polyglot-embedding-demo) to find a complete runnable Maven and Gradle examples.

## Changes

Important API changes and additions are tracked in the [SDK changelog](./CHANGELOG.md).  


## License

[GraalVM SDK](../sdk) is licensed under the [Universal Permissive License](./LICENSE.md).
