# Graal Standard Development Kit

The GraalVM SDK is a collection of APIs for the components of GraalVM.

* The [`org.graalvm.polyglot`](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html) module contains APIs to embed Graal languages in Java host applications.
* The [`org.graalvm.collections`](http://www.graalvm.org/sdk/javadoc/org/graalvm/collections/package-summary.html) module contains memory efficient common collection data structures used across Graal projects.
* The [`org.graalvm.options`](http://www.graalvm.org/sdk/javadoc/org/graalvm/options/package-summary.html) module contains reusable classes for options.

## Getting Started

1. Download GraalVM from [Oracle Technology Network](https://www.oracle.com/downloads/graalvm-downloads.html).
2. Use any of the Java executables in `./bin` or import GraalVM as JDK in your favorite IDE.
3. Use GraalVM SDK, the jar is put on the class path for you automatically.

The GraalVM SDK bits are also uploaded to Maven central.
You can use it from your `pom.xml` file as:

```xml
  <dependencies>
    <dependency>
      <groupId>org.graalvm</groupId>
      <artifactId>graal-sdk</artifactId>
      <version>0.30</version>
    </dependency>
  </dependencies>
```

Please note that GraalVM SDK requires GraalVM to run.

## Tutorials

* [Tutorial](https://www.graalvm.org/docs/reference-manual/embed/) on using the polyglot API to embed Graal languages in Java host applications.

## Changes

Important API changes and additions are tracked in the [SDK changelog](./CHANGELOG.md).  


## License

[GraalVM SDK](../sdk) is licensed under the [Universal Permissive License](./LICENSE.md).
