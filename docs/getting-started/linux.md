---
layout: docs
toc_group: getting-started
link_title: Installation on Linux
permalink: /getting-started/linux/
redirect_from: /docs/getting-started/linux/
---

## Installation on Linux Platforms

GraalVM is available for Linux on x64 and AArch64 architectures.
You can install GraalVM on Linux:
* [using SDKMAN!](#sdkman)
* [from an archive](#from-an-archive)
* [using script-friendly URLs](#script-friendly-urls)

Select the installation option that you prefer.

## SDKMAN!

Install Oracle GraalVM with [SDKMAN!](https://sdkman.io/){:target="_blank"}:
```bash
sdk install java <version>-graal
```
To install GraalVM Community Edition, change the distribution from `graal` to `graalce` in the command.

SDKMAN! helps you install and easily switch between JDKs.
Check which GraalVM releases are available for installation by running: 
```bash
sdk list java
```

## From an Archive

Install GraalVM from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

1. Navigate to the [GraalVM Downloads page](https://www.graalvm.org/downloads/){:target="_blank"}. Select the Java version, **Linux** for the operating system, **x64** or **aarch64** for the architecture, and download.

2. Change to the directory where you want to install GraalVM, then move the _.tar.gz_ file to that directory.

3. Unzip the archive:
    ```bash
    tar -xzf graalvm-jdk-<version>_linux-<architecture>.tar.gz
    ```

4. There can be multiple JDKs installed on the machine. Configure the runtime environment:
  - Set the value of the `JAVA_HOME` environment variable to the installation directory:
    ```bash
    export JAVA_HOME=/path/to/<graalvm>
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin/_ directory:
    ```bash
    export PATH=/path/to/<graalvm>/bin:$PATH
    ```

To confirm that the installation was successful, run the `java -version` command.
Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

## Script-Friendly URLs

[Script-friendly URLs](https://www.oracle.com/java/technologies/jdk-script-friendly-urls/){:target="_blank"} enable you to download GraalVM from a command line, or automatically in your script and Dockerfile by using a download URL. 
Substitute `<version>` and `<architecture>` with the JDK version and `aarch64` or `x64` architecture.
```bash
# Download with wget
wget https://download.oracle.com/graalvm/<version>/latest/graalvm-jdk-<version>_linux-<architecture>_bin.tar.gz

# Download with curl
curl https://download.oracle.com/graalvm/<version>/latest/graalvm-jdk-<version>_linux-<architecture>_bin.tar.gz

# Download from archive
curl https://download.oracle.com/java/<version>/archive/jdk-<version>_linux-<architecture>_bin.tar.gz
```

For other installation options, visit the [GraalVM Downloads page](https://www.graalvm.org/downloads/){:target="_blank"}.

## Prerequisites for Native Image on Linux

Native Image depends on the local toolchain (header files for the C library, `glibc-devel`, `zlib`, `gcc`, and/or `libstdc++-static`). 
These dependencies can be installed (if not yet installed) using a package manager on your Linux machine.

On **Oracle Linux** use the `yum` package manager:
```shell
sudo yum install gcc glibc-devel zlib-devel
```
Some Linux distributions may additionally require `libstdc++-static`.
You can install `libstdc++-static` if the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7, _ol8_codeready_builder_ on Oracle Linux 8, and _ol9_codeready_builder_ on Oracle Linux 9).

On **Ubuntu Linux** use the `apt-get` package manager:
```shell
sudo apt-get install build-essential zlib1g-dev
```
On **other Linux distributions** use the `dnf` package manager:
```shell
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```