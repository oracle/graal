---
layout: docs
toc_group: getting-started
link_title: Getting Started
permalink: /getting-started/
redirect_from: /docs/getting-started/
---

# Getting Started with Oracle GraalVM

Oracle GraalVM is an advanced JDK with ahead-of-time Native Image compilation.
Here you can find information about how to install Oracle GraalVM and run basic applications with it.

If you are new to Oracle GraalVM, we recommend starting with the [GraalVM Overview](../introduction.md), where you will find information about GraalVM's benefits, distributions, certified platforms, available features, and licensing.

If you have Oracle GraalVM already installed and have experience using it, you can skip this page and proceed to the in-depth [reference manuals](../reference-manual/reference-manuals.md).

## Installing

Installation steps for your specific platform:

* [Oracle Linux](https://docs.oracle.com/en/graalvm/jdk/23/docs/getting-started/oci/compute-instance/){:target="_blank"}
* [Linux](linux.md)
* [macOS](macos.md)
* [Windows](windows.md)

## Running an Application

Oracle GraalVM includes the Java Development Kit (JDK), the just-in-time compiler (the Graal compiler), Native Image, and other familiar Java tools.
You can use the GraalVM JDK just like any other JDK in your IDE, so having installed Oracle GraalVM, you can run any Java application unmodified.

The `java` launcher runs the JVM with Graal as the last-tier compiler.
Check the installed Java version:
```bash
java -version
```

Using [GraalVM Native Image](../reference-manual/native-image/README.md) you can compile Java bytecode into a platform-specific, self-contained, native executable to achieve faster startup and a smaller footprint for your application.

Compile this _HelloWorld.java_ application to bytecode and then build a native executable:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

```bash
javac HelloWorld.java
```
```bash
native-image HelloWorld
```

The last command generates an executable file named _helloworld_ in the current working directory.
Invoking it runs the natively-compiled code of the `HelloWorld` class as follows:
```bash
./helloworld
Hello, World!
```

> Note: For compilation, `native-image` depends on the local toolchain. Make sure your system meets the [prerequisites](../reference-manual/native-image/README.md#prerequisites).

## What to Read Next

### New Users

Continue to [Native Image basics](../reference-manual/native-image/NativeImageBasics.md) to educate yourself about the technology.
For users who are familiar with GraalVM Native Image but may have little experience using it, proceed to [User Guides](../reference-manual/native-image/guides/guides.md).

For more information on the compiler, see the [Graal Compiler](../reference-manual/java/compiler.md). 
Larger Java examples can be found in the [GraalVM Demos repository on GitHub](https://github.com/graalvm/graalvm-demos).

### Advanced Users

Developers who are more experienced with GraalVM or want to do more with GraalVM can proceed directly to [Reference Manuals](../reference-manual/reference-manuals.md) for in-depth documentation. 

You can find information on GraalVM's security model in the [Security Guide](../security/security-guide.md), and rich API documentation in the [Oracle GraalVM Java API Reference](https://docs.oracle.com/en/graalvm/jdk/23/sdk/index.html).

### Oracle Cloud Infrastructure Users

Oracle Cloud Infrastructure users who are considering Oracle GraalVM for their cloud workloads are invited to read [Oracle GraalVM on OCI](oci/installation-compute-instance-with-OL.md).
This page focuses on using Oracle GraalVM with an Oracle Cloud Infrastructure Compute instance.

We also recommend checking the [GraalVM Team Blog](https://medium.com/graalvm).