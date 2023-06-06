---
layout: docs
toc_group: getting-started
link_title:  Installation on macOS
permalink: /docs/getting-started/macos/
---

## Installation on macOS Platforms

GraalVM Community Edition is available for macOS on x64 and AArch64 architectures.

Note that in macOS, the JDK installation path is: `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.

Follow these steps to install GraalVM Community on the macOS operating system:

1. Navigate to [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select **17** or **19** for the Java version, **macOS** for the operating system **x64** or **aarch64** for the architecture, and download.

2. Unzip the archive.
    ```shell
    tar -xzf graalvm-ce-java<version>-darwin-<architecture>-<version>.tar.gz
    ```
    Alternatively, open the file in Finder.
    > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine /path/to/graalvm
    ```

3.  Move the downloaded package to its proper location, the `/Library/Java/JavaVirtualMachines` directory. Since this is a system directory, `sudo` is required:
    ```shell
    sudo mv graalvm-ce-java<version>-<version> /Library/Java/JavaVirtualMachines
    ```

  To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```

5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

For Oracle GraalVM users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/22/docs/getting-started/installation-macos/).

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that Oracle GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK installation(s) available, it is now possible that `/usr/libexec/java_home -v17` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v17 -V` to see the complete list of JDK 17 JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.

## Supported Functionalities

The base distribution of GraalVM Community Edition for macOS platforms includes the JDK, the Graal compiler, and Native Image.
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
