---
layout: ohc
permalink: /getting-started/installation-linux/
---

# Installation on Linux Platforms

Oracle GraalVM is available for Linux on x64 and AArch64 architectures. 
You can install Oracle GraalVM on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

### Note on AArch64 Distribution
This distribution can be installed on Oracle Linux and Red Hat Enterprise Linux(RHEL) systems for the AArch64 architecture.

Follow these steps to install Oracle GraalVM on a Linux operating system:

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).

2. Select the preferred Oracle GraalVM version in the Release Version dropdown, **17** or **19** for the Java version, **Linux** for the operating system, and **x64** or **aarch64** for the architecture.

3. Click on the **Oracle GraalVM JDK** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.

4. When the download button becomes active, click it to start downloading.

5. Change directory to the location where you want to install Oracle GraalVM, then move the _.tar.gz_ file to that directory.

6. Unzip the archive:
    ```shell
    tar -xzf graalvm-ee-java<version>-linux-<architecture>-<version>.tar.gz
    ```
7. There can be multiple JDKs installed on your machine. The next step is to configure your runtime environment:
  - Point the `PATH` environment variable to the Oracle GraalVM _bin_ directory:
    ```shell
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/path/to/<graalvm>
    ```
8. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify Oracle GraalVM as the default JRE or JDK installation in your Java IDE.

## Supported Functionalities

The base distribution of GraalVM Community Edition for Linux platforms includes the JDK, the Graal compiler, and Native Image.
The base installation can be additionally extended with language runtimes:

* [Java on Truffle](../../reference-manual/java-on-truffle/README.md)
* [JavaScript](../../reference-manual/js/README.md)
* [Node.js](../../reference-manual/js/NodeJS.md)
* [LLVM](../../reference-manual/llvm/README.md)
* [Python](../../reference-manual/python/README.md)
* [Ruby](../../reference-manual/ruby/README.md)
* [Wasm](../../reference-manual/wasm/README.md)

To assist you with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility to GraalVM.

> Note: Runtimes for Python, R, and Ruby languages are not available for the Oracle GraalVM Linux AArch64 distribution.