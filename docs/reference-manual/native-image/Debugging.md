---
layout: docs
toc_group: native-image
link_title: Debugging Native Image
permalink: /reference-manual/native-image/DebugInfo/
---

# Debugging Native Image

It is now possible to debug native images the same way you would debug regular Java applications.
You can set breakpoints, create watches and inspect the state of your application running as a native image.

There are two ways to debug native images:
  * using `gdb` from the command line
  * debugging applications directly from within [Visual Studio Code](../../tools/vscode/graalvm/README.md)

In this guide you will learn how to debug a Java application, transformed into a native executable.

## Debugging Native Image with `gdb`

Debugging native images from the command line is provided with the GNU Debugger (GDB).
The requirement is native images should contain debug records in a format `gdb` debugger understands.

> Note: Debug symbols are only available in GraalVM Enterprise, so it is required to produce full debug information for a native image.

> Note: Native Image debugging requires `gdb` debugger (GDB 7.11 or GDB 10.1+), it currently works only on Linux. The feature is experimental.

To build native images with debug information, provide one of the following switches to the `native-image` builder:
- `-g -O0`
- `-H:Debug=2 -H:Optimize=0`

For example,
```shell
javac Hello.java
native-image -H:Debug=2 -H:Optimize=0 Hello
```
The resulting images will contain debug records in a format `gdb` debugger understands.
For more details on debugging Java from GDB, see the [Debug Info Feature guide](DebugInfo.md).

## Debugging Native Image in VS Code

You can debug native images the same way as debugging regular Java applications.
It is possible in the [Visual Studio Code](https://code.visualstudio.com/) editor through the [GraalVM Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm-pack).
This extension serves many purposes including Java like debugging of native executables produced by Native Image.

> Note: Debugging native images from within Visual Studio Code requires installing GraalVM Enterprise.

You can attach the debugger to a Native Image process and step over the application source code.
Attaching of debugger to a Native Image process is done by adding another configuration, **Native Image: launch**, into the _launch.json_ file.
You can experience regular Java debugging features like setting breakpoints, creating watches, inspecting the state of your application running as a native image.
Stepping over the image source code (Java code) is mostly about UI differentiation of code which is compiled in the native image and which is not used.
The shaded code is not a part of the native image.

## Demo Part: Debugging Native Image in VS Code

You can test debugging a native image feature in VS Code using the below Java application.

### Prerequisities
1. Linux OS with GDB 10.1
2. Visual Studio Code
3. [GraalVM Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm-pack): Open Visual Studio Code and navigate to Extensions in the left-hand side Activity Bar (or use the Ctrl+Shift+X keys combination). Search for “GraalVM” in the search field, find "GraalVM Extension Pack for Java" and press Install. Reload when required.
4. [GraalVM Enterprise](https://www.graalvm.org/downloads) runtime environment: Navigate to **Gr** activity panel in VS Code and install some of the latest **GraalVM Enterprise** editions available from the list.
5. [Native Image](https://www.graalvm.org/reference-manual/native-image/): Upon GraalVM's installation completion, the “Install Optional GraalVM Components” window pops up in the right bottom corner. Install Native Image.

### Create and Build the Demo

1. Save the following Java code in the _App.java_ file:

```java
public class App
{
    static long fieldUsed = 1000;

    public static void main( String[] args )
    {
        if (args.length > 0){
            int n = -1;
            try {
                n = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.out.println(args[0] + " is not a number!");
            }
            if (n < 0) {
                System.out.println(args[0] + " is negative.");
            }
            double f = factorial(n);
            System.out.println(n + "! = " + f);
        }

        if (false)
            neverCalledMethod();

        StringBuilder text = new StringBuilder();
        text.append("Hello World from GraalVM native image and GDB in Java.\n");
        text.append("Native image debugging made easy!");
        System.out.println(text.toString());
    }

    static void neverCalledMethod(){
        System.out.println("This method will be never called and taken of by native-image.");
    }

    static double factorial(int n) {
        if (n == 0) {
            return 1;
        }
        if (n >= fieldUsed) {
            return Double.POSITIVE_INFINITY;
        }
        double f = 1;
        while (n > 1) {
            f *= n--;
        }
        return f;
    }
}
```

2. Open the file in VS Code.
3. In VS Code, open Terminal > New Terminal window and run following command:

  ```shell
  mvn -Pnative -DskipTests package
  ```

The `mvn -Pnative -DskipTests package` command packages a Java application into a runnable JAR file, and then builds a native image of it.

### Run and Debug the Demo

1. Select **Run and Debug** activity panel from the VS Code top navigation.
2. To attach a debugger to a running Native Image process, add a new launch configuration named **Native Image: launch** into the _launch.json_ which should look like this:
  ```json
  {
      "type": "nativeimage",
      "request": "launch",
      "name": "Launch Native Image",
      "nativeImagePath": "${workspaceFolder}/target/javagdb",
      "args": "100"
  }
  ```
3. Then run the debugger using `Launch Native Image` from the **RUN ...** menu. It will open the Java source file and start debugging a native image binary in VS Code.

   ![Native Image debugging source code](images/NativeImageExecutableLocations.png)
