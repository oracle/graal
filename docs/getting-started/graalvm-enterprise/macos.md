---
layout: ohc
permalink: /getting-started/installation-macos/
---

# Installation on macOS Platforms

Oracle GraalVM is available for macOS on x64 and AArch64 architectures.

Note that on macOS the JDK installation path is: <em>/Library/Java/JavaVirtualMachines/&lt;graalvm&gt;/Contents/Home</em>.

Follow these steps to install Oracle GraalVM:

1. Navigate to [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/). Select the preferred Oracle GraalVM version, **22** for the Java version, **macOS** for the operating system, and the architecture. Start downloading.

2. Remove the quarantine attribute (required for macOS Catalina and later):
    ```shell
    sudo xattr -r -d com.apple.quarantine graalvm-jdk-<version>_macos-<architecture>.tar.gz
    ```

3. Unzip the archive:
   ```shell
   tar -xzf graalvm-jdk-<version>_macos-<architecture>.tar.gz
   ```
   Alternatively, open the file in the Finder.

4. Move the downloaded package to its proper location, the _/Library/Java/JavaVirtualMachines_ directory. Since this is a system directory, `sudo` is required:
   ```shell
   sudo mv graalvm-jdk-<version>_macos-<architecture> /Library/Java/JavaVirtualMachines
   ```
   To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

5. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
6. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify Oracle GraalVM as the default JRE or JDK installation in your Java IDE.

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that Oracle GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK 17 installation(s) available, it is now possible that `/usr/libexec/java_home -v22` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v22 -V` to see the complete list of JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.