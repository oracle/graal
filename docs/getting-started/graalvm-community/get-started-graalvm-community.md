---
layout: docs
toc_group: getting-started
link_title: Getting Started
permalink: /docs/getting-started/
---

# Getting Started with GraalVM

GraalVM compiles your Java applications ahead of time into standalone binaries that start instantly, provide peak performance with no warmup, and use fewer resources.

Here you will find information about installing GraalVM and running basic applications with it.

If you are new to GraalVM, we recommend starting with the [introduction to GraalVM](../../introduction.md), where you will find information about GraalVM's benefits, distributions available, supported platforms, features support, and licensing.

If you have GraalVM already installed and have experience using it, you can skip this page and proceed to the in-depth [reference manuals](../../reference-manual/reference-manuals.md).

Choose your operating system and proceed to the installation steps for your specific platform:

* [Linux](linux.md)
* [macOS](macos.md)
* [Windows](windows.md)
* [Docker Container](container-images/graalvm-ce-container-images.md)

## Start Running Applications

GraalVM includes the Java Development Kit (JDK), the just-in-time compiler (the Graal compiler), Native Image, and standard JDK tools.
You can use the GraalVM JDK just like any other JDK in your IDE, so having installed GraalVM, you can run any Java application unmodified.

The `java` launcher runs the JVM with Graal as the last-tier compiler.
Check the installed Java version:
```shell
$JAVA_HOME/bin/java -version
```

Using [GraalVM Native Image](../../reference-manual/native-image/README.md) you can compile Java bytecode into a platform-specific, self-contained native executable to achieve faster startup and a smaller footprint for your application.

Compile this simple _HelloWorld.java_ application to bytecode and then build a native executable:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

```shell
javac HelloWorld.java
```
```shell
native-image HelloWorld
```

The last command generates an executable file named _helloworld_ in the current working directory.
Invoking it runs the natively compiled code of the `HelloWorld` class as follows:
```shell
./helloworld
Hello, World!
```

> Note: For compilation `native-image` depends on the local toolchain. Make sure your system meets the [prerequisites](../../reference-manual/native-image/README.md#prerequisites).

## What to Read Next

### New Users

Continue to [Native Image basics](../../reference-manual/native-image/NativeImageBasics.md) for more information about the technology.
For users who are familiar with GraalVM Native Image but may have little experience using it, proceed to [User Guides](../../reference-manual/native-image/guides/guides.md).

For more information on the compiler, see [Graal Compiler](../../reference-manual/java/compiler.md). 
Larger Java examples can be found in the [GraalVM Demos repository on GitHub](https://github.com/graalvm/graalvm-demos).

### Advanced Users

Developers who are more experienced with GraalVM or want to do more with GraalVM can proceed directly to [Reference Manuals](../../reference-manual/reference-manuals.md) for in-depth documentation.

You can find information on GraalVM's security model in the [Security Guide](../../security/security-guide.md), and rich API documentation in the [GraalVM SDK Javadoc](https://www.graalvm.org/sdk/javadoc/) and [Truffle Javadoc](https://www.graalvm.org/truffle/javadoc/).

We also recommend checking our [GraalVM Team Blog](https://medium.com/graalvm).