---
layout: docs
toc_group: getting-started
link_title:  Installation on Linux
permalink: /docs/getting-started/linux/
---

## Installation on Linux Platforms

GraalVM Community Edition is available for Linux on x64 and AArch64 architectures.

You can install GraalVM Community on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

Follow these steps to install GraalVM Community Edition on the Linux operating system.

1. Navigate to the [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select **17** or **19** for the Java version, **Linux** for the operating system, **x64** or **aarch64** for the architecture, and download.

2. Change directory to the location where you want to install GraalVM, then move the _.tar.gz_ file to that directory.

3. Unzip the archive:
    ```shell
    tar -xzf graalvm-ce-java<version>-linux-<architecture>-<version>.tar.gz
    ```
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/path/to/<graalvm>
    ```
5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

## Supported Functionalities

The base distribution of GraalVM Community Edition for Linux platforms includes the JDK, the Graal compiler, and Native Image.
The base installation can be additionally extended with language runtimes:

Language runtimes:
* [Java on Truffle](../../reference-manual/java-on-truffle/README.md)
* [JavaScript](../../reference-manual/js/README.md)
* [Node.js](../../reference-manual/js/NodeJS.md)
* [LLVM](../../reference-manual/llvm/README.md)
* [Python](../../reference-manual/python/README.md)
* [Ruby](../../reference-manual/ruby/README.md)
* [R](/../../reference-manual/r/README.md)
* [Wasm](../../reference-manual/wasm/README.md)

To assist you with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility to GraalVM.
