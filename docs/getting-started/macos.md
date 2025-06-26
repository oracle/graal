---
layout: docs
toc_group: getting-started
link_title: Installation on macOS
permalink: /getting-started/macos/
redirect_from: /docs/getting-started/macos/
---

## Installation on macOS Platforms

GraalVM is available for macOS on x64 and AArch64 architectures.
You can install GraalVM on macOS:
* [using SDKMAN!](#sdkman)
* [from an archive](#from-an-archive)
* [using script-friendly URLs](#script-friendly-urls)

Note that on macOS the JDK installation path is: _/Library/Java/JavaVirtualMachines/&lt;graalvm&gt;/Contents/Home/_.

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

1. Navigate to the [GraalVM Downloads page](https://www.graalvm.org/downloads/){:target="_blank"}. Select the Java version, **macOS** for the operating system, **x64** or **aarch64** for the architecture, and download.
  
2. Unzip the archive.
    ```bash
    tar -xzf graalvm-jdk-<version>_macos-<architecture>.tar.gz
    ```
    Alternatively, open the file in Finder.

3. Move the downloaded package to its proper location, the _/Library/Java/JavaVirtualMachines/_ directory. Since this is a system directory, `sudo` is required:
    ```bash
    sudo mv graalvm-jdk-<version> /Library/Java/JavaVirtualMachines
    ```
    To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
    ```bash
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin/_ directory:
    ```bash
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```

To confirm that the installation was successful, run the `java -version` command.
Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

## Script-Friendly URLs

[Script-friendly URLs](https://www.oracle.com/java/technologies/jdk-script-friendly-urls/){:target="_blank"} enable you to download GraalVM from a command line, or automatically in your script and Dockerfile by using a download URL. 
Substitute `<version>` and `<architecture>` with the JDK version and `aarch64` or `x64` architecture.
```bash
# Download with wget
wget https://download.oracle.com/graalvm/<version>/latest/graalvm-jdk-<version>_macos-<architecture>_bin.tar.gz

# Download with curl
curl https://download.oracle.com/graalvm/<version>/latest/graalvm-jdk-<version>_macos-<architecture>_bin.tar.gz

# Download from archive
curl https://download.oracle.com/java/<version>/archive/jdk-<version>_macos-<architecture>_bin.tar.gz
```

For other installation options, visit the [GraalVM Downloads page](https://www.graalvm.org/downloads/){:target="_blank"}.

## Prerequisites for Native Image on macOS

Native Image requires the Xcode command line tools.
To install them, run:
```shell
xcode-select --install
```

## Installation Notes

### On JAVA_HOME Command

The information property file, _Info.plist_, is located in the top-level _Contents/_ directory.
This allows GraalVM to integrate with the macOS-specific `/usr/libexec/java_home` mechanism.
Depending on other installed JDKs, running `/usr/libexec/java_home -v<version>` may return `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
To view all JVMs recognized by `java_home`, run `/usr/libexec/java_home -V`. This command lists JVMs in descending version order.