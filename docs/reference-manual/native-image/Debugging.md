---
layout: docs
toc_group: native-image
link_title: Native Image Debugging
permalink: /reference-manual/native-image/debugging/
---

# Native Image Debugging

It is now possible to debug native images the same way you would debug regular Java applications.
You can set breakpoints, create watches and inspect the state of your application running as a native image.

There are two ways to debug a native image:
  * using `gdb` from the command line
  * debugging a running native image process directly from within [Visual Studio Code](../../tools/vscode/graalvm/README.md)

In this guide you will learn how to debug a Java application, compiled into a native image.

## Debugging Native Image with `gdb`

Debugging a native image from the command line is provided with the GNU Debugger (GDB).
The requirement is that the image should contain debug records in a format `gdb` debugger understands.

> Note: Debug symbols are only available in GraalVM Enterprise, so it is required to produce full debug information for a native image.

> Note: Native Image debugging requires `gdb` debugger (GDB 7.11 or GDB 10.1+), it currently works only on Linux. The feature is experimental.

To build a native image with debug information, provide one of the following switches to the `native-image` builder:
- `-g -O0`
- `-H:Debug=2 -H:Optimize=0`

For example,
```shell
javac Hello.java
native-image -g -O0 Hello
```
The resulting images will contain debug records in a format `gdb` understands.
For more details on debugging Java from GDB, see the [Debug Info Feature guide](DebugInfo.md).

## Debugging Native Image in VS Code

You can debug a native image the same way as debugging a regular Java application.
It is currently possible in the [Visual Studio Code](https://code.visualstudio.com/) editor through the [GraalVM Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm-pack).
This extension serves many purposes including Java like debugging of native executables produced by Native Image.

> Note: Debugging native images from within Visual Studio Code requires installing GraalVM Enterprise.

You can attach the debugger to a Native Image process and step over the application source code.
Attaching of debugger to a Native Image process is done by adding a separate configuration, **Native Image: launch**, into the _launch.json_ file.
You can experience regular Java debugging features like setting breakpoints, creating watches, inspecting the state of your application running as a native image.

## Demo Part: Debugging Native Image in VS Code

You can test debugging a native image feature in VS Code using the below Java application.

### Prerequisities
1. Linux OS with GDB 10.1
2. Visual Studio Code
3. [GraalVM Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm-pack): Open Visual Studio Code and navigate to Extensions in the left-hand side Activity Bar (or use the Ctrl+Shift+X keys combination). Search for “GraalVM” in the search field, find "GraalVM Extension Pack for Java" and press Install. Reload when required.
4. [GraalVM Enterprise](https://www.graalvm.org/downloads) runtime environment: Navigate to **Gr** activity panel in VS Code and install some of the latest **GraalVM Enterprise** editions available from the list.
5. [Native Image](https://www.graalvm.org/reference-manual/native-image/): Upon GraalVM's installation completion, the “Install Optional GraalVM Components” window pops up in the right bottom corner. Install Native Image.

### Create and Build the Demo

The demo is a simple Java application calculating the factorial of a number.
The project is Maven-based, and uses the [Native Build Tools for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/index.html).

The Maven _pom.xml_ file is extended with a native profile, which makes building a native image very easy (read more about Maven Profiles [here](https://maven.apache.org/guides/introduction/introduction-to-profiles.html)).
To add debug information to your generated native image, the <buildArg> tags are used in the native profile to pass parameters to the `native-image` build process:

```xml
 <buildArg>-g</buildArg>
 <buildArg>-O0</buildArg>
```
Where `-g` instructs the `native-image` builder to generate debug information, and `-O0` specifies to not perform any optimizations.

1. To get the demo, clone the [demos repository from GitHub](https://github.com/graalvm/graalvm-demos) and open the `javagdbnative` folder in VS Code:

```
git clone git clone https://github.com/graalvm/graalvm-demos.git
```

2.In VS Code, open Terminal > New Terminal window and run following command:

  ```shell
  mvn -Pnative -DskipTests package
  ```
  This command packages a Java application into a runnable JAR file, and then builds a native image of it.

3. Once you have your executable with debug symbols generated, the last required setup step is to add a Native Image debugger configuration to the VSCode _launch.json_.

4. Select “Run | Add Configuration…” and then select “Native Image: Launch” from the list of available configurations. It will add the following code to launch.json.

  ```json
  {
   “type”: “nativeimage”,
   “request”: “launch”,
   “name”: “Launch Native Image”,
   “nativeImagePath”: “${workspaceFolder}/build/native-image/application”
   }
   ```
   The value of the nativeImagePath property has to match the executable name and the location specified in the Maven pom.xml, so change the last line of the configuration to `nativeImagePath”: “${workspaceFolder}/target/javagdb`.
5. Add an argument to specify the number that we want to calculate the factorial of : “args”: “100”. Your configuration should look like this:
  ```json
  {
   “type”: “nativeimage”,
   “request”: “launch”,
   “name”: “Launch Native Image”,
   “nativeImagePath”: “${workspaceFolder}/target/javagdb”,
   “args”: “100”
   }
   ```

### Run and Debug the Demo

To run the application in the debugging mode, go to the **Run and Debug** activity in the top navigation panel, select **Launch Native Image** and click the green arrow to run this configuration.
VS Code will execute your native image, attach to the application process, open the Java source file, letting you debug it.
You can set breakpoints, step over the code, explore local variables, specify expressions to be evaluated, etc., everything as you would do debugging a Java application from within your IDE.

![Native Image debugging source code](images/debugging_ni_vscode.png)

Notice that some code is greyed out in the source file. For example, the method `neverCalledMethod()`:

![Uncalled method greyed out](images/uncalled_method.png)

We can see how it is displayed in VS Code when using our VS Code extensions:
The native-image builder removes the code that is not used by the application at run time.
These could be some unreachable methods or some uncalled library.
The VS Code extension recognises these elimitations and will grey out both the body of eliminated method and the method call.
The shaded code is not a part of the native image.
