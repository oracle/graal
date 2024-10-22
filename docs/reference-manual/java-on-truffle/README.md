---
layout: docs
toc_group: espresso
link_title: Espresso
permalink: /reference-manual/espresso/
redirect_from: /reference-manual/java-on-truffle/
---

# Espresso

Using GraalVM, you can run Java applications normally [on HotSpot](../java/README.md), in [Native Image](../native-image/README.md), and on Truffle.

Espresso, also known as Java on Truffle, is an implementation of the Java Virtual Machine Specification, [Java SE 8](https://docs.oracle.com/javase/specs/jvms/se8/html/index.html), [Java SE 11](https://docs.oracle.com/javase/specs/jvms/se11/html/index.html), [Java SE 17](https://docs.oracle.com/javase/specs/jvms/se17/html/index.html), and [Java SE 21](https://docs.oracle.com/javase/specs/jvms/se21/html/index.html) built upon GraalVM as a Truffle interpreter.
It is a minified Java VM that includes all core components of a VM, implements the same API as the Java Runtime Environment library (`libjvm.so`), and reuses the existing standard library.
See the [Implementation Details](ImplementationDetails.md) for more information.

Espresso is open source with its codebase accessible [on GitHub](https://github.com/oracle/graal/tree/master/espresso).

Espresso runs Java via a Java bytecode interpreter, implemented with the [Truffle framework](../../../truffle/docs/README.md) – an open source library for writing interpreters for programming languages.
With Espresso, Java can be executed by the same principle as other languages in the GraalVM ecosystem (such as JavaScript, Python, Ruby), directly interoperate with those languages, and pass data back and forth in the same memory space.
Besides complete language interoperability, with Espresso you can:

- run Java bytecode in a separate context from the host Java VM.
- run either a Java 8, Java 11, Java 17, or Java 21 guest JVM, allowing to embed, for example, a Java 17 context in a Java 21 application, by using [GraalVM’s Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html).
- leverage the whole stack of tools provided by the Truffle framework, not previously available for Java.
- have an improved isolation of the host Java VM and the Java program running on Truffle, so you can run less trusted guest code.
- run in the context of a native executable while still allowing dynamically-loaded bytecode.

Espresso passes the Java Compatibility Kit (JCK or TCK for Java SE).

## Getting Started

Espresso is available as a standalone distribution that provides a Java 21 environment.
You can download a standalone based on Oracle GraalVM or GraalVM Community Edition. 

1. Download the Espresso 24.0 standalone for your operating system:

   * [Linux x64](https://gds.oracle.com/download/espresso/archive/espresso-java21-24.0.1-linux-amd64.tar.gz)
   * [Linux AArch64](https://gds.oracle.com/download/espresso/archive/espresso-java21-24.0.1-linux-aarch64.tar.gz)
   * [macOS x64](https://gds.oracle.com/download/espresso/archive/espresso-java21-24.0.1-macos-amd64.tar.gz)
   * [macOS AArch64](https://gds.oracle.com/download/espresso/archive/espresso-java21-24.0.1-macos-aarch64.tar.gz)
   * [Windows x64](https://gds.oracle.com/download/espresso/archive/espresso-java21-24.0.1-windows-amd64.zip)

2. Unzip the archive:
    ```shell
    tar -xzf <archive>.tar.gz
    ```
   
3. A standalone comes with a JVM in addition to its native launcher. Check the version to see the runtime is active:
    ```shell
    # Path to Espresso installation
    ./path/to/bin/java -truffle -version
    ```

## Run a Java Application on Espresso

You can run a Java application on Espresso, by passing the `-truffle` option to the standard `java` launcher.
This is similar to how you used to switch between the `-client` and `-server` JVMs.

To execute a class file:
```shell
java -truffle [options] class
```
To execute a JAR file:
```shell
java -truffle [options] -jar jarfile
```

You can also run a Java application from the main class in a module, or run a single source-file program:
```shell
java -truffle [options] -m module[/<mainclass>]
java -truffle [options] sourcefile
```

By default, Espresso runs within GraalVM by reusing all GraalVM's JAR files and native libraries, but it is possible to "cross-version" and specify a different Java installation directory (`java.home`).
It will automatically switch versions regardless of the host JVM.
```shell
java -truffle --java.JavaHome=/path/to/java/home -version
```

## Performance Considerations

The startup time will not match the speed offered by the regular GraalVM just-in-time (JIT) execution yet, but having created a fully working Espresso runtime, the development team is now focusing on performance.
You can still influence the performance by passing the following options to `java -truffle`:
* `--engine.MultiTier=true` to enable multi-tier compilation;
* `--engine.Inlining=false` in combination with `--java.InlineFieldAccessors=true` to make the compilation faster, in exchange for slower performance.

The `--vm.XX:` syntax ensures the option is passed to the underlying [Native Image VM](../native-image/BuildOptions.md).
When using the `-XX:` syntax, the VM first checks if there is such an option in the Espresso runtime.
If there is none, it will try to apply this option to the underlying Native Image VM.
This might be important for options such as `MaxDirectMemorySize` which can be set independently at both levels: `-XX:MaxDirectMemorySize=256M` controls how much native memory can be reserved by the Java program running on Espresso (the guest VM), while `--vm.XX:MaxDirectMemorySize=256M` controls how much native memory can be reserved by Native Image (the host VM).

## Start Running Applications

#### From Command Line

To ensure you have successfully installed Espresso, verify its version:
```shell
# Path to Espresso installation
./path/to/bin/java -truffle -version
```

Taking this `HelloWorld.java` example, compile it and run from the command line:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

```shell
$JAVA_HOME/bin/javac HelloWorld.java
$JAVA_HOME/bin/java -truffle HelloWorld
```

Taking some real-world applications, try running [Spring PetClinic](https://github.com/spring-projects/spring-petclinic) - a sample web application that demonstrates the use of Spring Boot with Spring MVC and Spring Data JPA.

1. Clone the project and navigate to the project’s directory:
   ```shell
   git clone https://github.com/spring-projects/spring-petclinic.git
   cd spring-petclinic
   ```

2. Build a JAR file (Spring PetClinic is built with Maven):
   ```shell
   ./mvnw package
   ```

3. Then run it from the command line by selecting the `-truffle` runtime:
   ```java
   java -truffle -jar target/spring-petclinic-<version>-SNAPSHOT.jar
   ```

4. When the application starts, access it on [localhost:8000](http://localhost:8080/).

#### From IDE

To run a Java project on Espresso from an IDE requires setting GraalVM as a project's default JDK and enabling the Espresso execution mode.
For example, to run the Spring PetClinic project using Intellij IDEA, you need to:

1. Navigate to **File**, then to **Project Structure**. Click **Project**, and then click **Project SDK**. Expand the drop down, press Add **JDK** and open the directory where you installed GraalVM. For macOS users, JDK home path will be `/Library/Java/JavaVirtualMachines/{graalvm}/Contents/Home`. Give it a name, and press Apply.

    ![Intellij IDEA: Add Project Name](images/add-project-default-sdk.png)

2. Generate sources and update folders for the project. In the Maven sidebar, click on the directory with the spinner icon:

    ![Intellij IDEA: Generate Project Sources](images/generate-project-sources.png)

3. Enable Espresso. From the main menu select **Run**, then **Run…**. Click **Edit Configurations** and choose **Environment**. Put the `-truffle -XX:+IgnoreUnrecognizedVMOptions` command in **VM options** and press Apply.

    ![Intellij IDEA: Enable Environment Configuration](images/pass-vmoption.png)

  It is necessary to specify `-XX:+IgnoreUnrecognizedVMOptions` because Intellij automatically adds a `-javaagent` argument which is not supported yet.

4. Press Run.

## Debugging

You do not have to configure anything special to debug Java applications running Espresso from your favorite IDE debugger.
For example, starting a debugger session from IntelliJ IDEA is based on the Run Configurations.
To ensure you attach the debugger to your Java application in the same environment, navigate in the main menu to Run -> Debug… -> Edit Configurations, expand Environment, check the JRE value and VM options values.
It should show GraalVM as project's JRE, and VM options should include `-truffle -XX:+IgnoreUnrecognizedVMOptions`, where `-truffle` enables Espresso, and `-XX:+IgnoreUnrecognizedVMOptions` is a temporary workaround since the Espresso runtime does not yet support attaching Java agents.

![Intellij IDEA: Debug Configuration](images/debug-configuration.png)

## What to Read Next

Espresso enables a seamless Java interoperability with other languages in the GraalVM ecosystem.
Check the [Interoperability with Truffle Languages guide](Interoperability.md) to learn how to load code written in other languages, export and import objects between languages, and so on.

To learn about the implementation approach, project's current status, and known limitations proceed to [Implementation Details](ImplementationDetails.md).

You can already run some large applications such as the Eclipse IDE, Scala or other languages REPLs in the Espresso execution mode.
We recommend having a look at the collection of [Demo Applications](Demos.md).

If you have a question, check the available [FAQs](FAQ.md), or reach us directly over the **#espresso** channel in [GraalVM Slack](https://www.graalvm.org/slack-invitation/).
