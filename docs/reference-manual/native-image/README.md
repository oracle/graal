---
layout: docs
toc_group: native-image
link_title: Getting Started with Native Image
permalink: /reference-manual/native-image/
---

# Native Image

Native Image is a technology to compile Java code ahead-of-time to a binary&mdash;a **native executable**. 
A native executable includes only the code required at run time, that is the application classes, standard-library classes, the language runtime, and statically-linked native code from the JDK. 

An executable file produced by Native Image has several important advantages, in that it

- Uses a fraction of the resources required by the Java Virtual Machine, so is cheaper to run
- Starts in milliseconds
- Delivers peak performance immediately, with no warmup
- Can be packaged into a lightweight container image for fast and efficient deployment
- Presents a reduced attack surface

A native executable is created by the **Native Image builder** or `native-image` that processes your application classes and [other metadata](ReachabilityMetadata.md) to create a binary for a specific operating system and architecture.
First, the `native-image` tool performs static analysis of your code to determine the classes and methods that are **reachable** when your application runs.
Second, it compiles classes, methods, and resources into a binary.
This entire process is called **build time** to clearly distinguish it from the compilation of Java source code to bytecode. 

The `native-image` tool can be used to build a **native executable**, which is the default, or a **native shared library**. This quick start guide focuses on building a native executable; to learn more about native shared libraries, go [here](InteropWithNativeCode.md).

To get used to Native Image terminology and get better understanding of the technology, we recommend you to read the [Basics of Native Image](NativeImageBasics.md). 

### Table of Contents

* [Build a Native Executable Using Maven or Gradle](#build-a-native-executable-using-maven-or-gradle)
* [Build a Native Executable Using the `native-image` Tool](#build-a-native-executable-using-the-native-image-tool)
* [Build Configuration](#build-configuration)
* [Configuring Native Image with Third-Party Libraries](#configuring-native-image-with-third-party-libraries)
* [Further Reading](#further-reading)

### Prerequisites

The `native-image` tool, available in the `bin` directory of your GraalVM installation, depends on the local toolchain (header files for the C library, `glibc-devel`, `zlib`, `gcc`, and/or `libstdc++-static`). 
These dependencies can be installed (if not yet installed) using a package manager on your machine.
Choose your operating system to find instructions to meet the prerequisites.

#### Linux

On Oracle Linux use the `yum` package manager:
```shell
sudo yum install gcc glibc-devel zlib-devel
```
Some Linux distributions may additionally require `libstdc++-static`.
You can install `libstdc++-static` if the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7, _ol8_codeready_builder_ on Oracle Linux 8, and _ol9_codeready_builder_ on Oracle Linux 9).

On Ubuntu Linux use the `apt-get` package manager:
```shell
sudo apt-get install build-essential zlib1g-dev
```
On other Linux distributions use the `dnf` package manager:
```shell
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```

#### MacOS

On macOS use `xcode`:
```shell
xcode-select --install
```

#### Windows

To use Native Image on Windows, install [Visual Studio 2022](https://visualstudio.microsoft.com/vs/) version 17.6.0 or later, and Microsoft Visual C++ (MSVC). There are two installation options:
* Install the Visual Studio Build Tools with the Windows 11 SDK (or later version)
* Install Visual Studio with the Windows 11 SDK (or later version)

Native Image runs in both a PowerShell or Command Prompt and will automatically set up build environments on Windows, given that it can find a suitable Visual Studio installation.

For more information, see [Using GraalVM and Native Image on Windows](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311).

## Build a Native Executable Using Maven or Gradle

We provide Maven and Gradle plugins for Native Image to automate building, testing, and configuring native executables. 

### Maven 

The [Maven plugin for Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) adds support for compiling a Java application into a native executable using [Apache Maven](https://maven.apache.org/).

1. Create a new Maven Java project named “helloworld” in your favorite IDE or from your terminal with the following structure:
    ```
    ├── pom.xml
    └── src
        ├── main
        │   └── java
        │       └── com
        │           └── example
        │               └── App.java
    ```
    For example, you can run this command to create a new Maven project using the quickstart archetype:
    ```bash
    mvn archetype:generate -DgroupId=com.example -DartifactId=helloworld -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
    ```

2. Add the regular Maven plugins for compiling and assembling the project into an executable JAR file to your _pom.xml_ file:
    ```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.12.1</version>
                <configuration>
                    <fork>true</fork>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.example.App</mainClass>
                            <addClasspath>true</addClasspath>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
    ```

3. Enable the Maven plugin for Native Image by adding the following profile to _pom.xml_:
    ```xml
    <profiles>
      <profile>
        <id>native</id>
        <build>
          <plugins>
            <plugin>
              <groupId>org.graalvm.buildtools</groupId>
              <artifactId>native-maven-plugin</artifactId>
              <version>${native.maven.plugin.version}</version>
              <extensions>true</extensions>
              <executions>
                <execution>
                <id>build-native</id>
                  <goals>
                    <goal>compile-no-fork</goal>
                  </goals>
                  <phase>package</phase>
                </execution>
                <execution>
                <id>test-native</id>
                  <goals>
                    <goal>test</goal>
                  </goals>
                  <phase>test</phase>
                </execution>
              </executions>
            </plugin>
          </plugins>
        </build>
      </profile>
    </profiles>
    ```
    Set the `version` property to the latest plugin version (for example, by specifying the version via `<native.maven.plugin.version>` in the `<properties>` element).

4. Compile the project and build a native executable at one step:
    ```bash
    mvn -Pnative package
    ``` 
    The native executable, named `helloworld`, is created in the _target/_ directory of the project.

5. Run the executable:
    ```bash
    ./target/helloworld 
    ```
    That is it, you successfully created the native executable for your Java application using Maven.

The Maven plugin for Native Image building offers many other features that may be required for an application with more complexity, such as resources autodetection, generating the required configuration, running JUnit Platform tests on a native executable, and so on, described in the [plugin reference documentation](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).

### Gradle 

The [Gradle plugin for Native Image](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html) adds support for compiling a Java application into a native executable using the [Gradle build tool](https://gradle.org/).

1. Create a new Gradle Java project named “helloworld” in your favorite IDE or from your terminal with the following structure:
    ```
    ├── app
    │   ├── build.gradle
    │   └── src
    │       ├── main
    │       │   ├── java
    │       │   │   └── org
    │       │   │       └── example
    │       │   │           └── App.java
    │       │   └── resources
    ```

    For example, initialize a new Gradle project with the `java` plugin:
    - Create a new directory and enter it:
        ```bash
        mkdir helloworld && cd helloworld
        ```
    - Generate a project:
        ```bash
        gradle init --project-name helloworld --type java-application --test-framework junit-jupiter --dsl groovy
        ```
        Follow the prompts. 
        This command sets up a new Java application with the necessary directory structure and build files.

2. Enable the Gradle plugin for Native Image by adding the following to `plugins` section of your project’s _build.gradle_ file:
    ```
    plugins {
    // ...
    id 'org.graalvm.buildtools.native' version 'x.x.x'
    }
    ```
    Specify the latest plugin version for the `'x.x.x'` version value.

3. Build a native executable by running `./gradlew nativeCompile`:
    ```bash
    ./gradlew nativeCompile
    ```
    The native executable, named `app`, is created in the _app/build/native/nativeCompile/_ directory of the project.

4. Run the native executable:
    ```bash
    ./app/build/native/nativeCompile/app 
    ```
    That is it, you successfully created the native executable for your Java application using Gradle.

The Gradle plugin for Native Image building has many other features that may be required for an application with more complexity, such as resources autodetection, generating the required configuration, running JUnit Platform tests on a native executable, and so on, described in the [plugin reference documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html).

## Build a Native Executable Using the `native-image` Tool

The `native-image` tool takes Java bytecode as its input. 
You can build a native executable from a class file, from a JAR file, or from a module (with Java 9 and higher).

### From a Class

To build a native executable from a Java class file in the current working directory, use the following command:
```shell
native-image [options] class [imagename] [options]
```

For example, build a native executable for a HelloWorld application.

1. Save this code into file named _HelloWorld.java_:
    ```java 
    public class HelloWorld {
        public static void main(String[] args) {
            System.out.println("Hello, Native World!");
        }
    }
    ```

2. Compile it and build a native executable from the Java class:
    ```shell
    javac HelloWorld.java
    native-image HelloWorld
    ```
    It will create a native executable, `helloworld`, in the current working directory. 
    
3. Run the application:

    ```shell
    ./helloworld
    ```
    You can time it to see the resources used:
    
    ```shell
    time -f 'Elapsed Time: %e s Max RSS: %M KB' ./helloworld
    # Hello, Native World!
    # Elapsed Time: 0.00 s Max RSS: 7620 KB
    ```

### From a JAR file

To build a native executable from a JAR file in the current working directory, use the following command:
```shell
native-image [options] -jar jarfile [imagename]
```

The default behavior of `native-image` is aligned with the `java` command which means you can pass the `-jar`, `-cp`, `-m`  options to build with Native Image as you would normally do with `java`. For example, `java -jar App.jar someArgument` becomes `native-image -jar App.jar` and `./App someArgument`.

[Follow this guide](guides/build-native-executable-from-jar.md) to build a native executable from a JAR file.

### From a Module

You can also convert a modularized Java application into a native executable. 

The command to build a native executable from a Java module is:
```shell
native-image [options] --module <module>[/<mainclass>] [options]
```

For more information about how to produce a native executable from a modular Java application, see [Building a HelloWorld Java Module into a Native Executable](guides/build-java-module-app-aot.md).

## Build Configuration

There many options you can pass to the `native-image` tool to configure the build process. 
Run `native-image --help` to see the full list.
The options passed to `native-image` are evaluated left-to-right.

For different build tweaks and to learn more about build time configuration, see [Native Image Build Configuration](BuildConfiguration.md).

Native Image will output the progress and various statistics during the build. 
To learn more about the output and the different build phases, see [Build Output](BuildOutput.md).

## Native Image and Third-Party Libraries

For more complex applications that use external libraries, you must provide the `native-image` tool with metadata.

Building a standalone binary with `native-image` takes place under a "closed world assumption". 
The `native-image` tool performs an analysis to see which classes, methods, and fields within your application are reachable and must be included in the native executable. 
The analysis is static: it does not run your application.
This means that all the bytecode in your application that can be called at runtime must be known (observed and analyzed) at build time.

The analysis can determine some cases of dynamic class loading, but it cannot always exhaustively predict all usages of the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path resources. 
To deal with these dynamic features of Java, you inform the analysis with details of the classes that use Reflection, Proxy, and so on, or what classes to be dynamically loaded.
To achieve this, you either provide the `native-image` tool with JSON-formatted configuration files or pre-compute metadata in the code.

To learn more about metadata, ways to provide it, and supported metadata types, see [Reachability Metadata](ReachabilityMetadata.md).
To automatically collect metadata for your application, see [Automatic Collection of Metadata](AutomaticMetadataCollection.md).

Some applications may need additional configuration to be compiled with Native Image.
For more details, see [Native Image Compatibility Guide](Compatibility.md).

Native Image can also interop with native languages through a custom API.
Using this API, you can specify custom native entry points into your Java application and build it into a native shared library.
To learn more, see [Interoperability with Native Code](InteropWithNativeCode.md).

### Further Reading

This getting started guide is intended for new users or those with little experience of using Native Image. 
We strongly recommend these users to check the [Basics of Native Image](NativeImageBasics.md) page to better understand some key aspects before going deeper.

Check [user guides](guides/guides.md) to become more experienced with Native Image, find demo examples, and learn about potential usage scenarios.

For a gradual learning process, check the Native Image [Build Overview](BuildOverview.md) and [Build Configuration](BuildConfiguration.md) documentation.

Consider running interactive workshops to get some practical experience: go to [Luna Labs](https://luna.oracle.com/) and search for "Native Image".

If you have stumbled across a potential bug, please [submit an issue in GitHub](https://github.com/oracle/graal/issues/new/choose).

If you would like to contribute to Native Image, follow our standard [contributing workflow](contribute/Contributing.md).