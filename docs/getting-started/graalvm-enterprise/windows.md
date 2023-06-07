---
layout: ohc
permalink: /getting-started/installation-windows/
---

# Installation on Windows Platforms

Oracle GraalVM is available for Windows on the x64 architecture.

Follow these steps to install Oracle GraalVM:

1. Navigate to [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/).  
Select the preferred Oracle GraalVM version, **17** or **20** for the Java version, and **Windows** for the operating system. Start downloading.

2. Change the directory to the location where you want to install GraalVM, then move the _.zip_ archive file to it.

3. Unzip the archive to your file system.

4. There can be multiple JDKs installed on the machine. The next step is to configure the runtime environment. Setting environment variables via the command line will work the same way for Windows 8, 10, and 11.
  - Set the value of the `PATH` environment variable to the GraalVM _bin_ directory:
    ```shell
    setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
    ```
  - Set the `JAVA_HOME` environment variable to resolve to the installation directory:
    ```shell
    setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
    ```
  Note that the `/M` flag, equivalent to `-m`, requires elevated user privileges.

5. Restart Command Prompt to reload the environment variables. Then use the following command to check whether the variables were set correctly:
    ```shell
    echo %PATH%
    echo %JAVA_HOME%
    ```

Alternatively, you can set up environment variables through a Windows GUI:

1. Go to Windows Start Menu - Settings - ... - Advanced.
2. Click **Environment Variables**. In the section labeled "System Variables" find the `JAVA_HOME` variable and select it.
3. Click **Edit**.
4. Click **New**.
5. Click **Browse** to find the directory to add. Confirm by clicking **OK**.
6. Restart Command Prompt to reload the environment variables.

Repeat the same for the `PATH` environment variable.

## Prerequisites for Using Native Image on Windows
On Windows, Native Image requires Visual Studio and Microsoft Visual C++(MSVC).
You can use Visual Studio 2022 version 17.1.0 or later.
There are two installation options:
- Install the Visual Studio Build Tools with the Windows 10 SDK
- Install Visual Studio with the Windows 10 SDK

Step-by-step instructions on installing Visual Studio Build Tools and Windows 10 SDK, and starting using Native Image can be found [here](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311).