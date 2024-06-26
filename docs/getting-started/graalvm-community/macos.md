---
layout: docs
toc_group: getting-started
link_title:  Installation on macOS
permalink: /docs/getting-started/macos/
---

## Installation on macOS Platforms

GraalVM is available for macOS on x64 and AArch64 architectures.
You can install GraalVM on macOS:
* [using SDKMAN!](#sdkman)
* [from an archive](#from-an-archive)
* [using script-friendly URLs](#script-friendly-urls)

Note that on macOS the JDK installation path is: <em>/Library/Java/JavaVirtualMachines/&lt;graalvm&gt;/Contents/Home</em>.

Select the installation option that you prefer.

## SDKMAN!

Install GraalVM with [SDKMAN!](https://sdkman.io/):
```
sdk install java 23-graalce
```

SDKMAN! helps you install and easily switch between JDKs.
Check which GraalVM releases are available for installation by running: 
```bash
sdk list java
```

## From an Archive

Install GraalVM from an archive (_.tar.gz_) for the current user into any location, without affecting other JDK installations.

1. Navigate to the [GraalVM Downloads page](https://www.graalvm.org/downloads/). Select **23** for the Java version, **macOS** for the operating system, **x64** or **aarch64** for the architecture, and download.
  
2. Remove the quarantine attribute (required for macOS Catalina and later):
    ```shell
    sudo xattr -r -d com.apple.quarantine graalvm-jdk-<version>_macos-<architecture>.tar.gz
    ```

3. Unzip the archive.
    ```shell
    tar -xzf graalvm-jdk-<version>_macos-<architecture>.tar.gz
    ```
    Alternatively, open the file in Finder.

4.  Move the downloaded package to its proper location, the `/Library/Java/JavaVirtualMachines` directory. Since this is a system directory, `sudo` is required:
    ```shell
    sudo mv graalvm-jdk-<version>_macos-<architecture> /Library/Java/JavaVirtualMachines
    ```
    To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

5. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```

To confirm that the installation was successful, run the `java -version` command.
Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

## Script-Friendly URLs

[Script-friendly URLs](https://www.oracle.com/java/technologies/jdk-script-friendly-urls/) enable you to download GraalVM from a command line, or automatically in your script and Dockerfile by using a download URL. 
Substitute `<version>` and `<architecture>` with the JDK version and `aarch64` or `x64` architecture.
```bash
# Download with wget
wget https://download.oracle.com/graalvm/23/latest/graalvm-jdk-23_macos-<architecture>_bin.tar.gz

# Download with curl
curl https://download.oracle.com/graalvm/23/latest/graalvm-jdk-23_macos-<architecture>_bin.tar.gz

# Download from archive
curl https://download.oracle.com/java/23/archive/jdk-23_macos-<architecture>_bin.tar.gz
```

For other installation options, visit the [GraalVM Downloads page](https://www.graalvm.org/downloads/).

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ directory.
This means that GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK installation(s) available, it is now possible that `/usr/libexec/java_home -v23` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v23 -V` to see the complete list of JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.