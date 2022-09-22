---
layout: ohc
permalink: /getting-started/installation-linux/
---

# Installation on Linux Platforms

Oracle provides GraalVM Enterprise Edition distributions for Linux on AMD64 (Intel) and ARM 64-bit architectures. 
You can install GraalVM Enterprise on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

### Note on ARM 64-bit Distribution
This distribution can be installed on Oracle Linux and Red Hat Enterprise Linux(RHEL) systems for AArch64 CPU architecture.

Follow these steps to install Oracle GraalVM Enterprise Edition on the Linux operating system:

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).
2. Select the preferable GraalVM Enterprise version in the Release Version dropdown, **11** or **17** for the Java version, **Linux** for the operating system, and **amd64** or **aarch64** for the architecture.
3. Click on the **GraalVM Enterprise JDK** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.
4. When the download button becomes active, press it to start downloading **graalvm-ee-java<version>-linux-<architecture>-<version>.tar.gz**.
5. Change directory to the location where you want to install GraalVM Enterprise, then move the _.tar.gz_ file to that directory.
6. Unzip the archive:
 ```shell
 tar -xzf graalvm-ee-java<version>-linux-<architecture>-<version>.tar.gz
 ```
7. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM Enterprise `bin` directory:
  ```shell
  export PATH=/path/to/<graalvm>/bin:$PATH
  ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
  ```shell
  export JAVA_HOME=/path/to/<graalvm>
  ```
8. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM Enterprise as the default JRE or JDK installation in your Java IDE.

## Supported Functionalities

The 64-bit GraalVM Enterprise distribution for Linux platforms includes Oracle JDK with the GraalVM compiler enabled, the [GraalVM Updater, `gu`](../../reference-manual/graalvm-updater.md) utility and some developer tools (e.g., Chrome inspector based debugger, Profiler).
Support for [Native Image](../../reference-manual/native-image/README.md), JavaScript, Node.js, LLVM, and WebAssembly runtimes can be installed with `gu`.
Runtimes for Python, FastR, and Ruby languages are not available in this distribution yet.

The base distribution of GraalVM Enterprise for Linux includes Oracle JDK with the GraalVM compiler enabled.
The base installation can be additionally extended with:

Tools/Utilities:
* [Native Image](../../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native executable
* [LLVM toolchain](../../reference-manual/llvm/Compiling.md#llvm-toolchain-for-compiling-cc) --  a set of tools and APIs for compiling native programs to bitcode that can be executed with on the GraalVM runtime

Language runtimes:
* [Java on Truffle](../../reference-manual/java-on-truffle/README.md)
* [JavaScript](../../reference-manual/js/README.md)
* [Node.js](../../reference-manual/js/NodeJS.md)
* [LLVM](../../reference-manual/llvm/README.md)
* [Python](../../reference-manual/python/README.md)
* [Ruby](../../reference-manual/ruby/README.md)
* [R](/../../reference-manual/r/README.md)
* [Wasm](../../reference-manual/wasm/README.md)

To assist a user with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility from above to GraalVM.

> Note: Runtimes for Python, R, and Ruby languages are not available for the GraalVM Enterprise Linux ARM 64-bit distribution.