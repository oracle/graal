---
layout: ni-docs
toc_group: how-to-guides
link_title: Debug Native Executables with GDB
permalink: /reference-manual/native-image/guides/debug-native-image-process/
---

# Debug Native Executables with GDB

A generated native executable is heavily optimized code with minimal symbol information which makes debugging harder.
This can be solved by embedding debug information into the resulting binary at build time.
This information tells the debugger precisely how to interpret the machine code and point it back to the original Java method.

If your Java application deployed as a native executable behaves differently than expected, you can interactively debug a running process:

- using the standard Linux GNU Debugger (GDB);
- using the built-in Java debugging in VS Code enabled with the [GraalVM Tools for Java extension](https://marketplace.visualstudio.com/items?itemName=oracle-labs-graalvm.graalvm).

In this guide you will learn how to debug a native executable using GDB. 

> Note: Native Image debugging with GDB currently works on Linux with initial support for macOS. The feature is experimental.

### Run a Demo

To build a native executable with debug information, provide the `-g` command-line option to the `native-image` builder.
This will enable source-level debugging, and the debugger (GDB) then correlates machine instructions with specific source lines in Java files. 

### Prerequisites

- Linux AMD64
- GDB 10.1 or higher

Follow the steps to test debugging a native executable with GDB. The below workflow is known to work on Linux with GDB 10.1.

1. Save the following code to the file named _GDBDemo.java_.

    ```java
    public class GDBDemo {
        static long fieldUsed = 1000;

        public static void main(String[] args) {
            if (args.length > 0) {
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
            text.append("Hello World from GraalVM Native Image and GDB in Java.\n");
            System.out.println(text.toString());
        }

        static void neverCalledMethod() {
            System.out.println("This method is unreachable and will not be included in the native executable.");
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

2. Compile it and generate a native executable with debug information:

    ```shell 
    $JAVA_HOME/bin/javac GDBDemo.java
    ```
    ```shell
    native-image -g -O0 GDBDemo
    ```
    The `-g` option instructs `native-image` to generate debug information. The resulting native executable will contain debug records in a format GDB understands.

    Notice that you can also pass `-O0` which specifies that no compiler optimizations should be performed. Disabling all optimizations is not required, but in general it makes the debugging experience better.

3. Launch the debugger and run your native executable:

    ```shell
    gdb ./gdbdemo
    ```
    The `gdb` prompt will open.
 
4. Set a breakpoint: type `breakpoint <java method>` to set a breakpoint and `run <arg>` to run the native executable. You can put breakpoints configured by file and line, or by method name. The following command places a breakpoint on the main entry point for class `GDBDemo`:

    ```
    (gdb) info func ::main
    ```

    <!-- ```
    (gdb) args.length > 0
    ``` -->
5. Pass `<arg>` to the command line. Step over to the next function call. If the native executable segfaults, you can print the backtrace of the entire stack (`bt`).

The debugger points machine instructions back from the binary to specific source lines in Java files. Note that single stepping within a compiled method includes file and line number information for inlined code. GDB may switch files even though you are still in the same compiled method.

Most of the regular debugging actions are supported by GDB, namely:

  - single stepping including both into and over function calls
  - stack backtraces (not including frames detailing inlined code)
  - printing of primitive values
  - structured, field by field, printing of Java objects
  - casting and printing objects at different levels of generality
  - access through object networks via path expressions
  - reference by name to methods and static field data

The generation of debug information is implemented by modeling the Java program as an equivalent C++ program.  Since GDB was primarily designed for debugging C (and C++), there are certain considerations to be taken into account when debugging Java applications. 
Read more about Native Image debugging support from the [reference documentation](../DebugInfo.md#special-considerations-for-debugging-java-from-gdb).

### Related Documentation

- [Debug Info Feature](../DebugInfo.md)
- [Debug a running native image process from VS Code](../../../tools/vscode/graalvm/native-image-debugging.md)