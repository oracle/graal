---
layout: ohc
permalink: /getting-started/installation-macos/
---

# Installation on macOS Platforms

Oracle GraalVM is available for macOS on x64 and AArch64 architectures.

Note that in macOS, the JDK installation path is: _/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home_.

Follow these steps to install Oracle GraalVM:

1. Navigate to [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/). 
Select the preferred Oracle GraalVM version, **17** or **20** for the Java version, **macOS** for the operating system, and the architecture. Start downloading.

2. Unzip the archive:
   ```shell
   tar -xzf graalvm-jdk-<version>_macos-<architecture>.tar.gz
   ```
   Alternatively, open the file in the Finder.
   > Note: If you are using macOS Catalina and later you may need to remove the quarantine attribute:
    ```shell
    sudo xattr -r -d com.apple.quarantine /path/to/graalvm
    ```

3. Move the downloaded package to its proper location, the _/Library/Java/JavaVirtualMachines_ directory. Since this is a system directory, `sudo` is required:
   ```shell
   sudo mv graalvm-jdk-<version>_macos-<architecture> /Library/Java/JavaVirtualMachines
   ```
   To verify if the move is successful and to get a list of all installed JDKs, run `/usr/libexec/java_home -V`.

4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment:
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    export PATH=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home/bin:$PATH
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home
    ```

5. To check whether the installation was successful, run the `java -version` command.

Optionally, you can specify Oracle GraalVM as the default JRE or JDK installation in your Java IDE.

## Installation Notes

### On JAVA_HOME Command
The information property file, _Info.plist_, is in the top level _Contents_ folder.
This means that Oracle GraalVM participates in the macOS-specific `/usr/libexec/java_home` mechanism. Depending on other JDK 17 installation(s) available, it is now possible that `/usr/libexec/java_home -v17` returns `/Library/Java/JavaVirtualMachines/<graalvm>/Contents/Home`.
You can run `/usr/libexec/java_home -v17 -V` to see the complete list of JDK17 JVMs available to the `java_home` command. This command sorts the JVMs in decreasing version order and chooses the top one as the default for the specified version.
Within a specific version, the sort order appears to be stable but is unspecified.