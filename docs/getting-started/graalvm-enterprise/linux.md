---
layout: ohc
permalink: /getting-started/installation-linux/
---

# Installation on Linux Platforms

Oracle GraalVM is available for Linux on x64 and AArch64 architectures. 
You can install Oracle GraalVM on Linux from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

### Note on AArch64 Distribution
This distribution can be installed on Oracle Linux and Red Hat Enterprise Linux (RHEL) systems for the AArch64 architecture.

Follow these steps to install Oracle GraalVM:

1. Navigate to [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/). 
Select the preferred Oracle GraalVM version, **22** for the Java version, **Linux** for the operating system, and the architecture. Start downloading.

2. Change directory to the location where you want to install Oracle GraalVM, then move the _.tar.gz_ file to that directory.

3. Unzip the archive:
    ```shell
    tar -xzf graalvm-jdk-<version>_linux-<architecture>.tar.gz
    ```
4. There can be multiple JDKs installed on your machine. The next step is to configure your runtime environment:
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/path/to/<graalvm>
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```
5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can use Oracle GraalVM as the default JRE or JDK installation in your Java IDE.