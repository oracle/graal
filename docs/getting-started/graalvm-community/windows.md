---
layout: docs
toc_group: getting-started
link_title:  Installation on Windows
permalink: /docs/getting-started/windows/
---

# Installation on Windows Platforms

You can install GraalVM Community Edition on the Windows operating system from an archive file (_zip_).
Follow these steps:

1. Navigate to the [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Depending on the workload, select Java 11 based or Java 8 based distribution for Windows, and download.
2. Change the directory to the location where you want to install GraalVM, then move the _.zip_ archive file to it.
3. Unzip the archive to your file system.
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment. Setting environment variables via the command line will work the same way for Windows 7, 8 and 10.
  - Point the `PATH` environment variable to the GraalVM `bin` directory:
  ```shell
  setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
  ```
  - Set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory:
  ```shell
  setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
  ```
  Note that the `/M` flag, equivalent to `-m`, requires elevated user privileges.

5. Restart Command Prompt to reload the environment variables. Then use the
following command to check whether the variables were set correctly:
```shell
echo %PATH%
echo %JAVA_HOME%
```

For Oracle GraalVM Enterprise Edition users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/21/docs/getting-started/installation-windows/).

## Installation Note

To run GraalVM Community Edition based on OpenJDK 8u292 on a Windows platform, the **MSVCR100.dll** redistributable package needs to be installed (for more details, see the issue [#3187](https://github.com/oracle/graal/issues/3187#issuecomment-784234990)).

## Supported Functionalities

The GraalVM Community distribution for Windows platforms includes OpenJDK with the GraalVM compiler enabled, the [GraalVM Updater](/reference-manual/graalvm-updater/) tool to install additional functionalities, the JavaScript runtime, and the developer tools (e.g., Chrome inspector based debugger, Profiler, etc.).
Currently, the GraalVM environment on Windows can be extended with [Native Image](/reference-manual/native-image/), [Java on Trufle](/reference-manual/java-on-truffle/), WebAssembly, and Node.js support.

## Prerequisites for Using Native Image on Windows
To make use of Native Image on Windows, observe the following recommendations.
The required Microsoft Visual C++ (MSVC) version depends on the JDK version that GraalVM is based on.
For GraalVM based on JDK 8, you will need MSVC 2010 SP1 version. The recommended installation method is using Microsoft Windows SDK 7.1:
1. Download the SDK file `GRMSDKX_EN_DVD.iso` for from [Microsoft](https://www.microsoft.com/en-gb/download).
2. Mount the image by opening `F:\Setup\SDKSetup.exe` directly.

For GraalVM distribution based on JDK 11, you will need MSVC 2017 15.5.5 or later version.

The last prerequisite, common for both distributions, is the proper [Developer Command Prompt](https://docs.microsoft.com/en-us/cpp/build/building-on-the-command-line?view=vs-2019#developer_command_prompt_shortcuts) for your version of [Visual Studio](https://visualstudio.microsoft.com/vs/). On Windows the `native-image` tool only works when it is executed from the **x64 Native Tools Command Prompt**.
