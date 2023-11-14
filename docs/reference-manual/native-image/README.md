---
layout: docs
toc_group: native-image
link_title: Getting Started with Native Image
permalink: /reference-manual/native-image/
---

# Native Image

Native Image is a technology to compile Java code ahead-of-time to a binary -- a **native executable**. 
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

* [Build a Native Executable](#build-a-native-executable)
* [Configuring Native Image with Third-Party Libraries](#configuring-native-image-with-third-party-libraries)
* [Further Reading](#further-reading)

### Prerequisites

The `native-image` tool, available in the `bin` directory of your GraalVM installation, depends on the local toolchain (header files for the C library, `glibc-devel`, `zlib`, `gcc`, and/or `libstdc++-static`). 
These dependencies can be installed (if not yet installed) using a package manager on your machine.
Choose your operating system to find instructions to meet the prerequisites.

{%
include snippet-tabs
tab1type="markdown" tab1id="Linux" tab1name="Linux" tab1path="native_image/linux.md"
tab2type="markdown" tab2id="macOS" tab2name="macOS" tab2path="native_image/macos.md"
tab3type="markdown" tab3id="Windows" tab3name="Windows" tab3path="native_image/windows.md"
%}

<!-- #### Linux

On Oracle Linux use the `yum` package manager:
```shell
sudo yum install gcc glibc-devel zlib-devel
```
Some Linux distributions may additionally require `libstdc++-static`.
You can install `libstdc++-static` if the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7, _ol8_codeready_builder_ on Oracle Linux 8, and _ol9_codeready_builder_ on Oracle Linux 9).

On Ubuntu Linux use the `apt-get` package manager:
```shell
sudo apt-get install build-essential libz-dev zlib1g-dev
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

To use Native Image on Windows, install [Visual Studio 2022](https://visualstudio.microsoft.com/vs/) version 17.1.0 or later, and Microsoft Visual C++ (MSVC). There are two installation options:
* Install the Visual Studio Build Tools with the Windows 10 or later SDK
* Install Visual Studio with the Windows 10 or later SDK

Native Image runs in both a PowerShell or Command Prompt and will automatically set up build environments on Windows, given that it can find a suitable Visual Studio installation.

For more information, see [Using GraalVM and Native Image on Windows 10](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311). -->

## Build a Native Executable

The `native-image` tool takes Java bytecode as its input. You can build a native executable from a class file, from a JAR file, or from a module (with Java 9 and higher).

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

### Getting Notified When the Build Process Is Done

Depending on the size of your application and the available resources of your build machine, it can take a few minutes to AOT-compile your Java application to a native executable.
If you are building your project in the background, consider using a command that notifies you when the build process is completed.
Below, example commands are listed per operating system:

#### Linux
```bash
# Ring the terminal bell
native-image -jar App.jar ... ; printf '\a'

# Use libnotify to create a desktop notification
native-image -jar App.jar ... ; notify-send "GraalVM Native Image build completed with exit code $?"

# Use Zenity to open an info dialog box with text
native-image -jar App.jar ... ; zenity --info --text="GraalVM Native Image build completed with exit code $?"
```

#### macOS
```bash
# Ring the terminal bell
native-image -jar App.jar ... ; printf '\a'

# Use Speech Synthesis
native-image -jar App.jar ... ; say "GraalVM Native Image build completed"
```

#### Windows
```bash
# Ring the terminal bell (press Ctrl+G to enter ^G)
native-image.exe -jar App.jar & echo ^G

# Open an info dialog box with text
native-image.exe -jar App.jar & msg "%username%" GraalVM Native Image build completed
```

## Build Configuration

There many options you can pass to the `native-image` builder to configure the build process. Run `native-image --help` to see the full list.
The options passed to `native-image` are evaluated left-to-right.

For different build tweaks and to learn more about build time configuration, see [Native Image Build Configuration](BuildConfiguration.md).

Native Image will output the progress and various statistics during the build. To learn more about the output and the different build phases, see [Build Output](BuildOutput.md).

## Native Image and Third-Party Libraries

For more complex applications that use external libraries, you must provide the `native-image` builder with metadata.

Building a standalone binary with the `native-image` tool takes place under a "closed world assumption". 
The `native-image` tool performs an analysis to see which classes, methods, and fields within your application are reachable and must be included in the native image. 
The analysis is static: it does not run your application.
This means that all the bytecode in your application that can be called at run time must be known (observed and analyzed) at build time.

The analysis can determine some cases of dynamic class loading, but it cannot always exhaustively predict all usages of the Java Native Interface (JNI), Java Reflection, Dynamic Proxy objects, or class path resources. 
To deal with these dynamic features of Java, you inform the analysis with details of the classes that use Reflection, Proxy, and so on, or what classes to be dynamically loaded.
To achieve this, you either provide the `native-image` tool with JSON-formatted configuration files or pre-compute metadata in the code.

To learn more about metadata, ways to provide it, and supported metadata types, see [Reachability Metadata](ReachabilityMetadata.md).
To automatically collect metadata for your application, see [Automatic Collection of Metadata](AutomaticMetadataCollection.md).

There are also Maven and Gradle plugins for Native Image to automate building, testing and configuring native executables. Learn more [here](https://graalvm.github.io/native-build-tools/latest/index.html).

Some applications may need additional configuration to be compiled with GraalVM Native Image.
For more details, see [Native Image Compatibility Guide](Compatibility.md).

Native Image can also interop with native languages through a custom API.
Using this API, you can specify custom native entry points into your Java application and build it into a nativw shared library.
To learn more, see [Interoperability with Native Code](InteropWithNativeCode.md).

### Further Reading

This getting started guide is intended for new users or those with little experience of using GraalVM Native Image. 
We strongly recommend these users to check the [Basics of Native Image](NativeImageBasics.md) page to better understand some key aspects before going deeper.

Check [user guides](guides/guides.md) to become more experienced with GraalVM Native Image, find demo examples, and learn about potential usage scenarios.

For a gradual learning process, check the Native Image [Build Overview](BuildOverview.md) and [Build Configuration](BuildConfiguration.md) documentation.

Consider running interactive workshops to get some practical experience: go to [Luna Labs](https://luna.oracle.com/) and search for "Native Image".

If you have stumbled across a potential bug, please [submit an issue in GitHub](https://github.com/oracle/graal/issues/new/choose).

If you would like to contribute to Native Image, follow our standard [contributing workflow](contribute/Contributing.md).
