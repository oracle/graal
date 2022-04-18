---
layout: docs
toc_group: native-image
link_title: Native Image
permalink: /reference-manual/native-image/
---
# Native Image

Native Image is a technology to ahead-of-time compile Java code to a standalone executable with [fast startup and low-memory footprint](TODO link results).
This executable (called a **native image**) includes the application classes, standard-library classes, the language runtime, and statically-linked native code from the JDK.
The executable does not require the Java VM as it includes the language runtime called Substrate VM. Substrate VM takes care of the Java VM semantics, memory management, thread scheduling, monitoring, and diagnostics.

The **Native Image builder** or `native-image` is a tool that processes application classes and [reachability metadata]() to produce a standalone native image for a specific operating system and architecture.
First, `native-image` determines the classes and methods that are **reachable** during the application execution by performing static analysis of the code. To determine if elements accessed through dynamic language features (e.g., reflective calls and resources) should be included, `native-image` requires the [reachability metadata]().
Second, `native-image` compiles classes, methods, and resources into a standalone binary.
This entire process is called **building an image** (or the **image build time**) to clearly distinguish it from the compilation of Java source code to bytecode.

Native Image supports JVM-based languages, e.g., Java, Scala, Clojure, Kotlin.
The resulting image can, optionally, execute dynamic languages like JavaScript, Ruby, R or Python. (TODO: link, mention truffle?)

* [Install Native Image](#install-native-image)
* [Build a Native Image](#build-a-native-image)
* [Further Reading](#further-reading)
* [License](#license)

## Install Native Image

Native Image can be added to GraalVM with the [GraalVM Updater](../graalvm-updater.md) tool.

Run this command to install Native Image:
```shell
gu install native-image
```
After this additional step, the `native-image` executable will become available in
the `$JAVA_HOME/bin` directory.

The above command will install Native Image from the GitHub catalog for GraalVM Community users.
For GraalVM Enterprise users, the [manual installation](../graalvm-updater.md#manual-installation) is required.

### Prerequisites

For compilation `native-image` depends on the local toolchain. Install `glibc-devel`, `zlib-devel` (header files for the C library and `zlib`) and `gcc`, using a package manager available on your OS. Some Linux distributions may additionally require `libstdc++-static`.

#### Linux

On Oracle Linux use `yum` package manager:
```shell
sudo yum install gcc glibc-devel zlib-devel
```
You can still install `libstdc++-static` as long as the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7 and _ol8_codeready_builder_ on Oracle Linux 8).

On  Ubuntu Linux use `apt-get` package manager:
```shell
sudo apt-get install build-essential libz-dev zlib1g-dev
```
On other Linux distributions use `dnf` package manager:
```shell
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```
#### MacOS

On macOS use `xcode`:
```shell
xcode-select --install
```

#### Windows

To start using Native Image on Windows, install [Visual Studio](https://visualstudio.microsoft.com/vs/) and Microsoft Visual C++ (MSVC).
There are two installation options:

* Install the Visual Studio Build Tools with the Windows 10 SDK
* Install Visual Studio with the Windows 10 SDK

You can use Visual Studio 2017 version 15.9 or later.

Lastly, on Windows, the `native-image` builder will only work when it is executed from the **x64 Native Tools Command Prompt**.
The command for initiating an x64 Native Tools command prompt is different if you only have the Visual Studio Build Tools installed, versus if you have the full VS 2019 installed.

## Build a Native Image

To build a native image of a Java class file in the current working directory, use
```shell
native-image [options] class [imagename] [options]
```

for example:

```shell
cat > HelloWorld.java << EOF
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, Native World!");
    }
}
EOF
javac HelloWorld.java
native-image HelloWorld
/usr/bin/time -f 'Elapsed Time: %e s Max RSS: %M KB' ./helloworld
# Hello, Native World!
# Elapsed Time: 0.00 s Max RSS: 7620 KB
```

For non-trivial applications, reachability metadata must be provided for the image to work properly.
To learn more about the reachability metadata and how to automatically collect it, see [ReachabilityMetadata](ReachabilityMetadata.md).

Not all applications are AOT-compilation friendly. To see if your application qualifies for building into a native image see [Native Image Limitations](Limitations.md).

For more complex examples, visit the [native-image build overview](BuildOverview.md).

## Further Reading

* [Native Image Build Overview](BuildOverview.md)
* [Optimizations and Performance](OptimizationsAndPerformance.md)
* [Debugging And Diagnostics](DebuggingAndDiagnostics.md)
* [Building a Shared Library](SharedLibrary.md)
* [How to Guides]()

## License

The Native Image technology is distributed as a separate installable to GraalVM.
Native Image for GraalVM Community Edition is licensed under the [GPL 2 with Classpath Exception](https://github.com/oracle/graal/blob/master/substratevm/LICENSE).

Native Image for GraalVM Enterprise Edition is available as an Early Adopter feature.
Early Adopter features are subject to ongoing development, testing, and modification.
For more information, check the [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html).
