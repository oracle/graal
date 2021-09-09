---
layout: ohc
permalink: /getting-started/installation-windows/
---

# Installation on Windows Platforms

You can install Oracle GraalVM Enterprise Edition on the Windows operating system from an archive file (_zip_).
Follow these steps:

1. Navigate to [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).
2. Select the preferable GraalVM Enterprise version in the Release Version dropdown, **8**, **11**, or **16** for the Java version, and **Windows** for the operating system.
3. Click on the **GraalVM Enterprise Core** download link. Before you download a file, you must accept the [Oracle License Agreement](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) in the popup window.
4. When the download button becomes active, press it to start downloading **graalvm-ee-java<version>-windows-amd64-<version>.zip**.
5. Change the directory to the location where you want to install GraalVM Enterprise, then move the _.zip_ archive to it.
6. Unzip the archive to your file system.
7. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment. Setting environment variables via the command line will work the same way for Windows 7, 8 and 10.
  - Point the `PATH` environment variable to the GraalVM Enterprise `bin` directory:
  ```shell
  setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
  ```
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM Enterprise installation directory:
  ```shell
  setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
  ```
  Note that the `/M` flag, equivalent to `-m`, requires elevated user privileges.

8. Restart Command Prompt to reload the environment variables. Then use the
following command to check whether the variables were set correctly:
```shell
echo %PATH%
echo %JAVA_HOME%
```

Optionally, you can specify GraalVM Enterprise as the JRE or JDK installation in your Java IDE.

## Supported Functionalities

The GraalVM Enterprise distribution for Windows platforms includes Oracle JDK with the GraalVM compiler enabled, the [GraalVM Updater](/reference-manual/graalvm-updater/) tool, the JavaScript runtime, and the developer tools (e.g., Chrome inspector based debugger, Profiler, etc.).
Currently, the GraalVM Enterprise environment on Windows can be extended with [Native Image](/reference-manual/native-image/), [Java on Trufle](/reference-manual/java-on-truffle/), WebAssembly, and Node.js support.

## Prerequisites for Using Native Image on Windows
To make use of Native Image on Windows, observe the following recommendations. The
required Microsoft Visual C++ (MSVC) version depends on the JDK version that
GraalVM is based on. For GraalVM Enterprise distribution based on JDK 8, you will need MSVC
2010 SP1 version. The recommended installation method is using Microsoft Windows
SDK 7.1:
1. Download the SDK file `GRMSDKX_EN_DVD.iso` for from [Microsoft](https://www.microsoft.com/en-gb/download).
2. Mount the image by opening `F:\Setup\SDKSetup.exe` directly.

For GraalVM Enterprise distribution based on JDK 11, you will need MSVC 2017 15.5.5 or later version.

The last prerequisite, common for both distributions, is the proper [Developer Command Prompt](https://docs.microsoft.com/en-us/cpp/build/building-on-the-command-line?view=vs-2019#developer_command_prompt_shortcuts) for your version of [Visual Studio](https://visualstudio.microsoft.com/vs/). On Windows the `native-image` tool only works when it is executed from the **x64 Native Tools Command Prompt**.
