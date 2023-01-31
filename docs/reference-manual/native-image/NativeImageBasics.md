---
layout: ni-docs
toc_group: native-image
link_title: Native Image Basics
permalink: /reference-manual/native-image/basics/
---

# Native Image Basics

Native Image is written in Java and takes Java bytecode as input to produce a standalone binary (an **executable**, or a **shared library**).
During the process of producing a binary, Native Image can run user code.
Finally, Native Image links compiled user code, parts of the Java runtime (for example, the garbage collector, threading support), and the results of code execution into the binary.

We refer to this binary as a **native executable**, or a **native image**.
We refer to the utility that produces the binary as the **`native-image` builder**, or the **`native-image` generator**.

To clearly distinguish between code executed during the native image build, and code executed during the native image execution, we refer to the difference between the two as [**build time** and **run time**](#build-time-vs-run-time).

To produce a minimal image, Native Image employs a process called [**static analysis**](#static-analysis).

### Table of Contents

* [Build Time vs Run Time](#build-time-vs-run-time)
* [Native Image Heap](#native-image-heap)
* [Static Analysis](#static-analysis)

## Build Time vs Run Time

During the image build, Native Image may execute user code.
This code can have side effects, such as writing a value to a static field of a class.
We say that this code is executed at *build time*.
Values written to static fields by this code are saved in the [**image heap**](#native-image-heap).
*Run time* refers to code and state in the binary when it is executed.

The easiest way to see the difference between these two concepts is through [configurable class initialization](ClassInitialization.md).
In Java, a class is initialized when it is first used.
Every Java class used at build time is said to be **build-time initialized**.
Note that merely loading a class does not necessarily initialize it.
The static class initializer of build-time initialized classes executes **on the JVM running the image build**.
If a class is initialized at build time, its static fields are saved in the produced binary.
At run time, using such a class for the first time does not trigger class initialization.

Users can trigger class initialization at build time in different ways:
 - By passing `--initialize-at-build-time=<class>` to the `native-image` builder.
 - By using a class in the static initializer of a build-time initialized class.

Native Image will initialize frequently used JDK classes at image build time, for example, `java.lang.String`, `java.util.**`, etc.
Note that build-time class initialization is an expert feature.
Not all classes are suitable for build-time initialization.

The following example demonstrates the difference between build-time and run-time executed code:
```java
public class HelloWorld {
    static class Greeter {
        static {
            System.out.println("Greeter is getting ready!");
        }
        
        public static void greet() {
          System.out.println("Hello, World!");
        }
    }

  public static void main(String[] args) {
    Greeter.greet();
  }
}
```

Having saved the code in a file named _HelloWorld.java_, we compile and run the application on the JVM:

```bash
javac HelloWorld.java
java HelloWorld 
Greeter is getting ready!
Hello, World!
```

Now we build a native image of it, and then execute:
```bash
native-image HelloWorld
========================================================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
========================================================================================================================
...
Finished generating 'helloworld' in 14.9s.
```
```bash
./helloworld 
Greeter is getting ready!
Hello, World!
```
`HelloWorld` started up and invoked `Greeter.greet`. 
This caused `Greeter` to initialize, printing the message `Greeter is getting ready!`.
Here we say the class initializer of `Greeter` is executed at *image run time*.

What would happen if we tell `native-image` to initialize `Greeter` at build time?

```bash
native-image HelloWorld --initialize-at-build-time=HelloWorld\$Greeter
========================================================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
========================================================================================================================
Greeter is getting ready!
[1/7] Initializing...                                                                                    (3.1s @ 0.15GB)
 Version info: 'GraalVM dev Java 11 EE'
 Java version info: '11.0.15+4-jvmci-22.1-b02'
 C compiler: gcc (linux, x86_64, 9.4.0)
 Garbage collector: Serial GC
...
Finished generating 'helloworld' in 13.6s.
./helloworld 
Hello, World!
```

We saw `Greeter is getting ready!` printed during the image build.
We say the class initializer of `Greeter` executed at *image build time*.
At run time, when `HelloWorld` invoked `Greeter.greet`, `Greeter` was already initialized.
The static fields of classes initialized during the image build are stored in the [image heap](#native-image-heap).

## Native Image Heap

The **Native Image heap**, also called the **image heap**, contains:
 - Objects created during the image build that are reachable from application code.
 - `java.lang.Class` objects of classes used in the native image.
 - Object constants [embedded in method code](ReachabilityMetadata.md#computing-metadata-in-code).

When native image starts up, it copies the initial image heap from the binary.

One way to include objects in the image heap is to initialize classes at build time:
```java
class Example {
    private static final String message;
    
    static {
        message = System.getProperty("message");
    }

    public static void main(String[] args) {
        System.out.println("Hello, World! My message is: " + message);
    }
}
```

Now we compile and run the application on the JVM:
```bash
javac Example.java
java -Dmessage=hi Example
Hello, World! My message is: hi
```
```bash
java -Dmessage=hello Example 
Hello, World! My message is: hello
```
```bash
java Example
Hello, World! My message is: null
```

Now examine what happens when we build a native image in which the `Example` class is initialized at build time:
```bash
native-image Example --initialize-at-build-time=Example -Dmessage=native
================================================================================
GraalVM Native Image: Generating 'example' (executable)...
================================================================================
...
Finished generating 'example' in 19.0s.
```
```bash
./example 
Hello, World! My message is: native
```
```bash
./example -Dmessage=aNewMessage
Hello, World! My message is: native
```

The class initializer of the `Example` class was executed at image build time.
This created a `String` object for the `message` field and stored it inside the image heap.

## Static Analysis

Static analysis is a process that determines which program elements (classes, methods and fields) are used by an application.
These elements are also referred to as **reachable code**.
The analysis itself has two parts:
 - Scanning the bytecodes of a method to determine what other elements are reachable from it.
 - Scanning the root objects in the native image heap (i.e., static fields) to determine which classes are reachable from them.
It starts from the entry points of the application (i.e., the `main` method).
The newly discovered elements are iteratively scanned until further scanning yields no additional changes in element's reachability.

Only **reachable** elements are included in the final image.
Once a native image is built, no new elements can be added at run time, for example, through class loading.
We refer to this constraint as the **closed-world assumption**.

### Further Reading

* [Native Image Build Overview](BuildOverview.md)
* [Class Initialization in Native Image](ClassInitialization.md)