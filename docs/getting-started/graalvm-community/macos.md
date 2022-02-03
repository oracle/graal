---
layout: docs
toc_group: getting-started
link_title:  Installation on macOS
permalink: /docs/getting-started/macos/
---

## Installation on macOS Platforms

GraalVM Community Edition can be installed for a single user and administrator privileges are not required.
However, if GraalVM is meant to become a default JDK, administrator privileges are required.

GraalVM Community Edition does not provide the installation wizard, unlike OpenJDK distributions for macOS that come with the _.dmg_ download.
It can be installed from an archive file (_.tar.gz_).
Note that in macOS, the JDK installation path is: `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.

Follow these steps to install GraalVM Community on the macOS operating system:

1. Navigate to [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select Java 11 based or Java 17 based distribution for macOS, and download.
2. Unzip the archive.
  ```shell
   tar -xzf graalvm-ce-java<version>-darvin-amd64-<version>.tar.gz
  ```
  Alternatively, open the file in Finder.
  > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute. See [Installation Notes](#installation-notes) below.

3.  Move the downloaded package to its proper location, the `/Library/Java/JavaVirtualMachines` directory. Since this is a system directory, `sudo` is required:
  ```shell
  sudo mv graalvm-ce-java<version>-<version> /Library/Java/JavaVirtualMachines
  ```
To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Point the `PATH` environment variable to the GraalVM `bin` directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify GraalVM as the default JRE or JDK installation in your Java IDE.

For Oracle GraalVM Enterprise Edition users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/21/docs/getting-started/installation-macos/).

## Installation Notes

#### On Software Notarization
If you are using macOS Catalina and later you may need to remove the quarantine attribute from the bits before you can use them.
To do this, run the following:
```shell
sudo xattr -r -d com.apple.quarantine /path/to/GRAALVM_HOME
```

#### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that GraalVM Enterprise participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK installation(s) available, it is now possible that `/usr/libexec/java_home -v1.8` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v1.8 -V` to see the complete list of 1.8 JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.

## Supported Functionalities

The base distribution of GraalVM Community Edition for macOS includes OpenJDK with the GraalVM compiler enabled, LLVM and JavaScript runtimes.
The base installation can be extended with:

Tools/Utilities:
* [Native Image](../../reference-manual/native-image/README.md) -- a technology to compile an application ahead-of-time into a native executable
* [LLVM toolchain](../../reference-manual/llvm/Compiling.md#llvm-toolchain-for-compiling-cc) --  a set of tools and APIs for compiling native programs to bitcode that can be executed with on the GraalVM runtime

Runtimes:
* [Java on Truffle](../../reference-manual/java-on-truffle/README.md) -- a Java Virtual Machine implementation based on a Truffle interpreter for GraalVM
* [Node.js](../../reference-manual/js/README.md) -- Node.js v14.18.1 compatible
* [Python](../../reference-manual/python/README.md) -- Python 3.8.5 compatible
* [Ruby](../../reference-manual/ruby/README.md) -- Ruby 3.0.2 compatible
* [R](/../../reference-manual/r/README.md) -- GNU R 4.0.3 compatible
* [Wasm](../../reference-manual/wasm/README.md) -- WebAssembly (Wasm)
​
These runtimes are not part of the GraalVM Community base distribution and must be installed separately.

To assist a user with installation, GraalVM includes **GraalVM Updater**, a command line utility to install and manage additional functionalities.
Proceed to the [installation steps](../../reference-manual/graalvm-updater.md#component-installation) to add any necessary language runtime or utility from above to GraalVM.
