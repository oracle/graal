---
layout: docs
toc_group: getting-started
link_title:  Installation on Linux
permalink: /docs/getting-started/linux/
---

## Installation on Linux Platforms

GraalVM is available for Linux on x64 and AArch64 architectures.

You can install GraalVM on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

Follow these steps to install GraalVM: 

1. Navigate to the [GraalVM Downloads page](https://www.graalvm.org/downloads/). Select **22** for the Java version, **Linux** for the operating system, **x64** or **aarch64** for the architecture, and download.

2. Change to directory where you want to install GraalVM, then move the _.tar.gz_ file to that directory.

3. Unzip the archive:
    ```shell
    tar -xzf graalvm-jdk-<version>_linux-<architecture>.tar.gz
    ```
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the value of the `JAVA_HOME` environment variable to the installation directory:
    ```shell
    export JAVA_HOME=/path/to/<graalvm>
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```  
5. To confirm that the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.