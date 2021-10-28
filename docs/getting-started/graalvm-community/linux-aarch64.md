---
layout: docs
toc_group: getting-started
link_title:  Installation on Linux ARM64
permalink: /docs/getting-started/linux-aarch64/
---

# Installation on Linux ARM64 systems

As of version 21.0, we provide GraalVM Community Edition for Linux on ARM 64-bit system, based on OpenJDK 11 and OpenJDK 17 for AArch64 architecture.
This distribution can be installed on Linux systems for AArch64 CPU architecture.

You can install the GraalVM distribution for Linux ARM64 systems from an archive file (_.tar.gz_).
This allows you to install GraalVM for the current user into any location, without affecting other JDK installations.

1. Navigate to the [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select Java 11 or 17 based distribution for the Linux AArch64 architecture, and download.
2. Change the directory to the location where you want to install GraalVM, then move the _.tar.gz_ archive to it.
3. Unzip the archive:
```shell
tar -xzf graalvm-ce-java<version>-linux-aarch64-<version>.tar.gz
```
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM Enterprise `bin` directory:
  ```shell
  export PATH=/path/to/<graalvm>/bin:$PATH
  ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
  ```shell
  export JAVA_HOME=/path/to/<graalvm>
  ```
5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

For Oracle GraalVM Enterprise Edition users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/21/docs/getting-started/installation-linux-aarch64/).

## Supported Functionalities

The 64-bit GraalVM Community distribution for Linux platforms includes OpenJDK with the GraalVM compiler enabled, the [GraalVM Updater, `gu`](../../reference-manual/graalvm-updater.md), tool, the JavaScript runtime, and some developer tools (e.g., Chrome inspector based debugger, Visual VM).
Support for [Native Image](../../reference-manual/native-image/README.md), Node.js, LLVM and WebAssembly runtimes can be installed with `gu`.
Runtimes for Python, FastR, and Ruby languages are not available in this distribution yet.
