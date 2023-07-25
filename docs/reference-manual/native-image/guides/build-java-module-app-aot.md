---
layout: ni-docs
toc_group: how-to-guides
link_title: Build Java Modules into a Native Executable
permalink: /reference-manual/native-image/guides/build-java-modules-into-native-executable/
---

# Build Java Modules into a Native Executable

GraalVM Native Image supports the [Java Platform Module System](https://www.oracle.com/uk/corporate/features/understanding-java-9-modules.html), introduced in Java 9, which means you can convert a modularized Java application into a native executable. 

The `native-image` tool accepts the module-related arguments like `--module` (`-m`), `--module-path` (`-p`), `--add-opens`, `--add-exports` (same as for the `java` launcher). 
When such a module-related argument is used, the `native-image` tool itself is used as a module too.
 
In addition to supporting `--add-reads` and `--add-modules`, all module related options are considered prior to scanning the module path. 
This helps prevent class loading errors and allow for better module introspection at run time.

The command to build a native executable from a Java module is:
```shell
native-image [options] --module <module>[/<mainclass>] [options]
```

### Run a Demo

Follow the steps below to practice building a modular Java application into a native executable.
For the demo, you will use a simple HelloWorld Java module gathered with Maven:

```
├── hello
│   └── Main.java
│       > package hello;
│       > 
│       > public class Main {
│       >     public static void main(String[] args) {
│       >         System.out.println("Hello from Java Module: "
│       >             + Main.class.getModule().getName());
│       >     }
│       > }
│
└── module-info.java
    > module HelloModule {
    >     exports hello;
    > }
```    

1. Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

2. Download or clone the demos repository and navigate to the directory `native-hello-module`:
    ```bash
    git clone https://github.com/graalvm/graalvm-demos
    cd graalvm-demos/native-hello-module
    ```

3. Compile and package the project with Maven:
    ```bash
    mvn package
    ```

4. Test running it on GraalVM's JDK:
    ```bash
    $JAVA_HOME/bin/java --module-path target/HelloModule-1.0-SNAPSHOT.jar --module HelloModule
    ```

5. Now build this module into a native executable:
    ```bash
    $JAVA_HOME/bin/native-image --module-path target/HelloModule-1.0-SNAPSHOT.jar --module HelloModule
    ```

    It builds the modular Java application into a native executable called `hellomodule` in the project root directory that you can execute:
    ```bash
    ./hellomodule
    ```

### Related Documentation

- Learn more how you can [access resources for a Java module at run time](../Resources.md#resources-in-java-modules).