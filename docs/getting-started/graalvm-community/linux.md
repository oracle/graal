---
layout: docs
toc_group: getting-started
link_title:  Installation on Linux
permalink: /docs/getting-started/linux/
---

## Installation on Linux Platforms

We provide GraalVM Community Edition distributions for Linux on AMD64 (Intel) and AArch64 architectures. 
You can install GraalVM Community on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

Follow these steps to install GraalVM Community Edition on the Linux operating system.

1. Navigate to the [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select **11**, **17** or **19** for the Java version, **Linux** for the operating system, **amd64** or **aarch64** for the architecture, and download.

2. Change directory to the location where you want to install GraalVM, then move the _.tar.gz_ file to that directory.

3. Unzip the archive:
    ```shell
    tar -xzf graalvm-ce-java<version>-linux-<architecture>-<version>.tar.gz
    ```
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM `bin` directory:
    ```shell
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/path/to/<graalvm>
    ```
5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

For Oracle GraalVM Enterprise Edition users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/22/docs/getting-started/installation-linux/).

## Supported Functionalities

The base distribution of GraalVM Community Edition for Linux platforms includes OpenJDK with the GraalVM compiler enabled.
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

> Note: Runtimes for Python, R, and Ruby languages are not available for the GraalVM Enterprise Linux AArch64 distribution.