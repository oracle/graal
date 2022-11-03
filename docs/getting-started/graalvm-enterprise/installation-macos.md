---
layout: ohc
permalink: /getting-started/installation-macos/
---

# Installation on macOS Platforms

GraalVM Enterprise can be installed for a single user and administrator privileges are not required. However, if GraalVM Enterprise is meant to become a default JDK, administrator privileges are required.

GraalVM Enterprise does not provide the installation wizard, unlike Oracle JDK distributions for macOS that come with the _.dmg_ download.
Note that in macOS, the JDK installation path is: `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.

Follow these steps to install Oracle GraalVM Enterprise Edition on the macOS operating system:

1. Navigate to[ Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).
2. Select the preferable GraalVM Enterprise version in the Release Version dropdown, **11** or **17** for the Java version, and **macOS** for the operating system.
3. Click on the **GraalVM Enterprise JDK** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.
4. When the download button becomes active, press it to start downloading **graalvm-ee-java<version>-darwin-amd64-<version>.tar.gz**.
5. Unzip the archive:
  ```shell
  tar -xzf graalvm-ee-java<version>-darwin-amd64-<version>.tar.gz
  ```
  Alternatively, open the file in Finder.
  > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine /path/to/graalvm
    ```

6. Move the downloaded package to its proper location, the `/Library/Java/JavaVirtualMachines` directory. Since this is a system directory, `sudo` is required:
  ```shell
  sudo mv graalvm-ee-java<version>-<version> /Library/Java/JavaVirtualMachines
  ```
To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.
7. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM Enterprise `bin` directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM Enterprise installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
8. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM Enterprise as the default JRE or JDK installation in your Java IDE.

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that GraalVM Enterprise participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK 8 installation(s) available, it is now possible that `/usr/libexec/java_home -v1.8` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v1.8 -V` to see the complete list of 1.8 JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.

## Supported Functionalities

The base distribution of GraalVM Enterprise for macOS platforms includes Oracle JDK with the GraalVM compiler enabled.
The base installation can be additionally extended with:

Tools/Utilities:
* [Native Image](../../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native executable
* [LLVM toolchain](../../reference-manual/llvm/Compiling.md#llvm-toolchain-for-compiling-cc) --  a set of tools and APIs for compiling native programs to bitcode that can be executed with on the GraalVM runtime

Language runtimes:
* [Java on Truffle](../../reference-manual/java-on-truffle/README.md)
* [JavaScript](../../reference-manual/js/README.md)
* [Node.js](../../reference-manual/js/NodeJS.md)
* [LLVM](../../reference-manual/llvm/README.md)
* [Python](../../reference-manual/python/README.md)
* [Ruby](../../reference-manual/ruby/README.md)
* [R](/../../reference-manual/r/README.md)
* [Wasm](../../reference-manual/wasm/README.md)

To assist a user with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility from above to GraalVM.
