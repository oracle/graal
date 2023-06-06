---
layout: ohc
permalink: /getting-started/installation-macos/
---

# Installation on macOS Platforms

Oracle GraalVM is available for macOS on x64 and AArch64 architectures.

Note that in macOS, the JDK installation path is: _/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home_.

Follow these steps to install Oracle GraalVM on a macOS operating system:

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).

2. Select the preferred Oracle GraalVM version in the Release Version dropdown, **17** or **19** for the Java version, **macOS** for the operating system and **x64** or **aarch64** for the architecture.

3. Click on the **Oracle GraalVM JDK** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.

4. When the download button becomes active, click it to start downloading.

5. Unzip the archive:
   ```shell
   tar -xzf graalvm-ee-java<version>-darwin-<architecture>-<version>.tar.gz
   ```
   Alternatively, open the file in the Finder.
   > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine /path/to/graalvm
    ```

6. Move the downloaded package to its proper location, the _/Library/Java/JavaVirtualMachines_ directory. Since this is a system directory, `sudo` is required:
   ```shell
   sudo mv graalvm-ee-java<version>-<version> /Library/Java/JavaVirtualMachines
   ```
   
To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

7. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the Oracle GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```

8. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify Oracle GraalVM as the default JRE or JDK installation in your Java IDE.

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that Oracle GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK 17 installation(s) available, it is now possible that `/usr/libexec/java_home -v17` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v17 -V` to see the complete list of JDK17 JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.

## Supported Functionalities

The base distribution of Oracle GraalVM for macOS platforms includes the JDK, the Graal compiler, and Native Image.
The base installation can be additionally extended with language runtimes:

* [Java on Truffle](../../reference-manual/java-on-truffle/README.md)
* [JavaScript](../../reference-manual/js/README.md)
* [Node.js](../../reference-manual/js/NodeJS.md)
* [LLVM](../../reference-manual/llvm/README.md)
* [Python](../../reference-manual/python/README.md)
* [Ruby](../../reference-manual/ruby/README.md)
* [Wasm](../../reference-manual/wasm/README.md)

To assist you with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility to GraalVM.
