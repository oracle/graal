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

In this guide you will learn how to debug a native executable using the standard Linux GNU Debugger (GDB).

> Note: Native Image debugging with GDB currently works on Linux with initial support for macOS. The feature is experimental.

### Run a Demo

To build a native executable with debug information, provide the `-g` command-line option for `javac` when compiling the application, and then to the `native-image` builder.
This enables source-level debugging, and the debugger (GDB) then correlates machine instructions with specific source lines in Java files. 

### Prerequisites

- Linux AMD64
- GDB 10.1 or higher

Follow the steps to test debugging a native executable with GDB. The below workflow is known to work on Linux with GDB 10.1.

1. Download and install the latest GraalVM JDK with Native Image using the [GraalVM JDK Downloader](https://github.com/graalvm/graalvm-jdk-downloader):
    ```bash
    bash <(curl -sL https://get.graalvm.org/jdk)
    ``` 

2. Save the following code to the file named _GDBDemo.java_.

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

3. Compile it and generate a native executable with debug information:

    ```shell 
    $JAVA_HOME/bin/javac -g GDBDemo.java
    ```
    ```shell
    native-image -g -O0 GDBDemo
    ```
    The `-g` option instructs `native-image` to generate debug information. The resulting native executable will contain debug records in a format GDB understands.

    Notice that you can also pass `-O0` which specifies that no compiler optimizations should be performed. Disabling all optimizations is not required, but in general it makes the debugging experience better.

4. Launch the debugger and run your native executable:

    ```shell
    gdb ./gdbdemo
    ```
    The `gdb` prompt will open.
 
5. Set a breakpoint: type `breakpoint <java method>` to set a breakpoint and `run <arg>` to run the native executable. You can put breakpoints configured by file and line, or by method name. See below the example of a debugging session.

    ```shell
    $ gdb ./gdbdemo
    GNU gdb (GDB) 10.2
    Copyright (C) 2021 Free Software Foundation, Inc.
    ...
    Reading symbols from ./gdbdemo...
    Reading symbols from /dev/gdbdemo.debug...
    (gdb) info func ::main
    All functions matching regular expression "::main":

    File GDBDemo.java:
    5:	void GDBDemo::main(java.lang.String[]*);
    (gdb) b ::factorial
    Breakpoint 1 at 0x2d000: file GDBDemo.java, line 32.
    (gdb) run 42
    Starting program: /dev/gdbdemo 42
    Thread 1 "gdbdemo" hit Breakpoint 1, GDBDemo::factorial (n=42) at GDBDemo.java:32
    32	        if (n == 0) {
    (gdb) info args
    n = 42
    (gdb) step
    35	        if (n >= fieldUsed) {
    (gdb) next
    38	        double f = 1;
    (gdb) next
    39	        while (n > 1) {
    (gdb) info locals
    f = 1
    (gdb) ...
    ```

     
In case your native executable segfaults, you can print the backtrace of the entire stack (`bt`).

The debugger points machine instructions back from the binary to specific source lines in Java files. Note that single stepping within a compiled method includes file and line number information for inlined code. GDB may switch files even though you are still in the same compiled method.

Most of the regular debugging actions are supported by GDB, namely:

  - single stepping including both into and over function calls
  - stack backtraces (not including frames detailing inlined code)
  - printing of primitive values
  - structured, field by field, printing of Java objects
  - casting and printing objects at different levels of generality
  - access through object networks via path expressions
  - reference by name to methods and static field data

The generation of debug information is implemented by modeling the Java program as an equivalent C++ program. Since GDB was primarily designed for debugging C (and C++), there are certain considerations to be taken into account when debugging Java applications. 
Read more about Native Image debugging support in the [reference documentation](../DebugInfo.md#special-considerations-for-debugging-java-from-gdb).

### Related Documentation

- [Debug Info Feature](../DebugInfo.md)