---
layout: docs
toc_group: getting-started
link_title:  Installation on macOS
permalink: /docs/getting-started/macos/
---

## Installation on macOS Platforms

GraalVM is available for macOS on x64 and AArch64 architectures.

Note that on macOS the JDK installation path is: _/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home_.

Follow these steps to install GraalVM: 

1. Navigate to the [GraalVM Downloads page](https://www.graalvm.org/downloads/). Select **21** for the Java version, **macOS** for the operating system, **x64** or **aarch64** for the architecture, and download.

2. Unzip the archive.
    ```shell
    tar -xzf graalvm-jdk-<version>_macos-<architecture>.tar.gz
    ```
    Alternatively, open the file in Finder.
    > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine /path/to/graalvm
    ```

3.  Move the downloaded package to its proper location, the `/Library/Java/JavaVirtualMachines` directory. Since this is a system directory, `sudo` is required:
    ```shell
    sudo mv graalvm-jdk-<version>_macos-<architecture> /Library/Java/JavaVirtualMachines
    ```
    To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```  

5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ directory.
This means that GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK installation(s) available, it is now possible that `/usr/libexec/java_home -v21` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v21 -V` to see the complete list of JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.