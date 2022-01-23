---
layout: ohc
permalink: /getting-started/installation-linux-aarch64/
---

# Installation on Linux ARM64 systems

As of version 21.0, Oracle provides GraalVM Enterprise distributions based on Oracle JDK11 and Oracle JDK17 for AArch64 architecture.
 This distribution can be installed on Oracle Linux and Red Hat Enterprise Linux(RHEL) systems for AArch64 CPU architecture.

You can install the GraalVM distribution for Linux ARM64 systems from an archive file (_.tar.gz_).
This allows you to install GraalVM for the current user into any location, without affecting other JDK installations.

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html?selected_tab=21).
2. Select the preferable GraalVM Enterprise version in the Release Version dropdown, **11** or **17** for the Java version, **Linux** for the operating system, and **aarch64** for the architecture.
3. Click on the **GraalVM Enterprise Core** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.
4. When the download button becomes active, press it to start downloading **graalvm-ee-java<version>-linux-aarch64-<version>.tar.gz**.
5. Change the directory to the location where you want to install GraalVM Enterprise, then move the _.tar.gz_ archive to it.
6. Unzip the archive:
```shell
tar -xzf graalvm-ee-java<version>-linux-aarch64-<version>.tar.gz
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
8. To check whether the installation was successful, enter the `java -version` command.

Optionally, you can specify GraalVM Enterprise as the default JRE or JDK installation in your Java IDE.

## Supported Functionalities

The 64-bit GraalVM Enterprise distribution for Linux platforms includes Oracle JDK with the GraalVM compiler enabled, the [GraalVM Updater, `gu`](../../reference-manual/graalvm-updater.md) tool, the JavaScript runtime, and some developer tools (e.g., Chrome inspector based debugger, Visual VM).
Support for [Native Image](../../reference-manual/native-image/README.md), Node.js, LLVM and WebAssembly runtimes can be installed with `gu`.
Runtimes for Python, FastR, and Ruby languages are not available in this distribution yet.
