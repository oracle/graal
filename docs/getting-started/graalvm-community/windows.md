---
layout: docs
toc_group: getting-started
link_title:  Installation on Windows
permalink: /docs/getting-started/windows/
---

# Installation on Windows Platforms

You can install GraalVM Community Edition on the Windows operating system from an archive file (_zip_).
Follow these steps:

1. Navigate to the [GraalVM Releases repository on GitHub](https://github.com/graalvm/graalvm-ce-builds/releases). Select Java 11 based or Java 17 based distribution for Windows, and download.
2. Change the directory to the location where you want to install GraalVM, then move the _.zip_ archive file to it.
3. Unzip the archive to your file system.
4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment. Setting environment variables via the command line will work the same way for Windows 7, 8, and 10.
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

Alternatively, you can set up environment variables through a Windows GUI:

1. Go to Windows Start Menu - Settings - ... - Advanced.
2. Click Environment Variables. In the section System Variables find the `JAVA_HOME` variable and select it.
3. Click Edit.
4. Click New.
5. Click Browse and reach the folder to add. Confirm by clicking OK.
6. Restart Command Prompt to reload the environment variables.

Repeat the same for the `PATH` environment variable.

For Oracle GraalVM Enterprise Edition users, find the installation instructions [here](https://docs.oracle.com/en/graalvm/enterprise/22/docs/getting-started/installation-windows/).

## Installation Note

To run GraalVM Community Edition based on OpenJDK 8u292 on a Windows platform, the **MSVCR100.dll** redistributable package needs to be installed (for more details, see issue [#3187](https://github.com/oracle/graal/issues/3187#issuecomment-784234990)).

## Supported Functionalities

The GraalVM Community distribution for Windows platforms includes OpenJDK with the GraalVM compiler enabled, the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool to install additional functionalities and the developer tools (e.g., Chrome inspector based debugger, Profiler, etc.).
Currently, the GraalVM environment on Windows can be extended with [Native Image](../../reference-manual/native-image/README.md), [Java on Truffle](../../reference-manual/java-on-truffle/README.md), [LLVM runtime](../../reference-manual/llvm/README.md), WebAssembly, JavaScript and Node.js support.

## Prerequisites for Using Native Image on Windows
On Windows, Native Image requires Visual Studio and Microsoft Visual C++(MSVC).
You can use Visual Studio 2017 version 15.9 or later.
There are two installation options:
- Install the Visual Studio Build Tools with the Windows 10 SDK
- Install Visual Studio with the Windows 10 SDK

The last prerequisite is the proper [Developer Command Prompt](https://docs.microsoft.com/en-us/cpp/build/building-on-the-command-line?view=vs-2019#developer_command_prompt_shortcuts) for your version of [Visual Studio](https://visualstudio.microsoft.com/vs/).
On Windows, the `native-image` tool only works when it is executed from the **x64 Native Tools Command Prompt**.

Step-by-step instructions on installing Visual Studio Build Tools and Windows 10 SDK, and starting using Native Image can be found [here](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311).
