---
layout: ohc
permalink: /getting-started/installation-linux/
---

# Installation on Linux Platforms

Follow these steps to install Oracle GraalVM Enterprise Edition on the Linux operating system:

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html?selected_tab=21).
2. Select the preferable GraalVM Enterprise version in the Release Version dropdown, **11** or **17** for the Java version, **Linux** for the operating system, and **amd64** for the architecture.
3. Click on the **GraalVM Enterprise Core** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.
4. When the download button becomes active, press it to start downloading **graalvm-ee-java<version>-linux-amd64-<version>.tar.gz**.
5. Change the directory to the location where you want to install GraalVM Enterprise, then move the _.tar.gz_ archive to it.
6. Unzip the archive:
 ```shell
 tar -xzf graalvm-ee-java<version>-linux-amd64-<version>.tar.gz
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

The base distribution of GraalVM Enterprise for Linux (AMD64) platforms includes Oracle JDK with the GraalVM compiler enabled, LLVM and JavaScript runtimes.
The base installation can be extended with:

Tools/Utilities:
* [Native Image](../../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native executable
* [LLVM toolchain](../../reference-manual/llvm/Compiling.md#llvm-toolchain-for-compiling-cc) --  a set of tools and APIs for compiling native programs to bitcode that can be executed with on the GraalVM runtime

Runtimes:
* [Java on Truffle](../../reference-manual/java-on-truffle/README.md) -- a Java Virtual Machine implementation based on a Truffle interpreter for GraalVM
* [Node.js](../../reference-manual/js/README.md) -- Node.js v14.18.1 compatible
* [Python](../../reference-manual/python/README.md) -- Python 3.8.5 compatible
* [Ruby](../../reference-manual/ruby/README.md) -- Ruby 3.0.2 compatible
* [R](/../../reference-manual/r/README.md) -- GNU R 4.0.3 compatible
* [Wasm](../../reference-manual/wasm/README.md) -- WebAssembly (Wasm)
​
These runtimes are not part of the GraalVM Enterprise base distribution and must be installed separately.

To assist a user with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility from above to GraalVM.
