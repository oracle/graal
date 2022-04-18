---
layout: docs
toc_group: native-image
link_title: The Native Image Programming Model
permalink: /reference-manual/native-image/ProgrammingModel/
---

# The Native Image Programming Model

## Native Image and the Native Image Build
Native Image is written in Java and takes Java bytecode as input to produce a standalone native binary (an *executable*, or a *shared library*).
During the process of producing a binary, Native Image can execute user code.
Finally, Native Image links compiled user code, parts of the Java runtime (e.g., the garbage collector and threading support), as well as the results of code executed during the above process into the binary. 

We refer to this binary as a *native image*, or simply *image*,
We refer to the process of creating an image the *native-image build* or *image build*, and Native Image itself as the *native-image builder*, or the *image builder*.

To clearly distinguish between code executed during an image build, and code executed during image execution, we refer to the difference between the two as [Image-Build Time and Image-Run Time](#image-build-time-vs-image-run-time)
To produce a minimal image, Native Image employs a process called [static analysis](#static-analysis-reachability-and-the-closed-world-assumption).

## Image-Build Time vs Image-Run Time

During the image build, Native Image may execute user code.
This code can have side effects, such as writing a value to a static field of a class.
We say that this code is executed at *Image-Build Time*.
Values written to static fields by this code are saved in the [Image Heap](#the-native-image-heap).
*Image-Run Time* refers to code and state in the binary when it is executed.

The following example shows the difference between the two:

### Image-Build Time vs Image-Run Time Example

The easiest way to see the difference between these two concepts is through [configurable class initialization](ClassInitialization.md).
In Java, a class is initialized when it is first used.
Every Java class used during the image build is said to be *build-time initialized*.
Note that merely loading a class does not necessarily initialize it.
The static class initializer of build time initialized classes executes **on the JVM running the image build**.
If a class is initialized at image build time, its static fields are saved in the produced binary.
At runtime, using such a class for the first time does not trigger class initialization.

Users can trigger class initialization at build-time in different ways:
 - By passing `--initialize-at-build-time=<class>` to Native Image.
 - By using the class in the static initializer of a build-time-initialized class.

Native Image will initialize frequently used JDK classes at image build time: `java.lang.String`, `java.util.**`, etc.
Note that build-time class initialization is an expert feature.
Not all classes are suitable for build time initialization.

A small example that demonstrates the difference between build-time and run-time executed code:
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

If we compile and run the above application on the JVM, we get:
```bash
$ javac HelloWorld.java
$ java HelloWorld 
Greeter is getting ready!
Hello, World!
```

We can now build an image out of it and then execute it:
```bash
$ native-image HelloWorld
========================================================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
========================================================================================================================
...
Finished generating 'helloworld' in 14.9s.
$ ./helloworld 
Greeter is getting ready!
Hello, World!
```

`HelloWorld` started up and invoked `Greeter.greet`.
This caused `Greeter` to initialize, printing the message `Greeter is getting ready!`.
We say the class initializer of `Greeter` executed at *Image-Run Time*.

What happens when we tell Native Image to initialize `Greeter` at build time:
```bash
$ native-image HelloWorld --initialize-at-build-time=HelloWorld\$Greeter
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
$ ./helloworld 
Hello, World!
```

We saw `Greeter is getting ready!` printed during the image build.
We say the class initializer of `Greeter` executed at *Image-Build Time*.
At runtime, when `HelloWorld` invoked `Greeter.greet`, `Greeter` was already initialized.
The static fields of classes initialized during the image build are stored in [the image heap](#the-native-image-heap)

## The Native Image Heap
The Native Image Heap, also called the Image Heap, contains:
 - Objects created during the image build that are reachable from application code.
 - `java.lang.Class` objects of classes used in the image.
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

We can now compile and run the application on the JVM:
```bash
$ javac Example.java
$ java -Dmessage=hi Example
Hello, World! My message is: hi
$ java -Dmessage=hello Example 
Hello, World! My message is: hello
$ java Example
Hello, World! My message is: null
```

Let's see what happens when we build a native image in which the `Example` class is initialized at build time:
```bash
$ native-image Example --initialize-at-build-time=Example -Dmessage=native
================================================================================
GraalVM Native Image: Generating 'example' (executable)...
================================================================================
...
Finished generating 'example' in 19.0s.
$ ./example 
Hello, World! My message is: native
$ ./example -Dmessage=aNewMessage
Hello, World! My message is: native
```

The class initializer of the `Example` class was executed during the image build.
This created a `String` object for the `message` field and stored it inside the image heap.

## Static Analysis, Reachability, and the Closed-World Assumption

Static analysis is a process that determines which program elements (i.e., classes, methods and fields) are used by an application.
These elements are also referred to as *reachable* elements.
The analysis itself has two parts:
 - Scanning the bytecodes of a method to determine what other elements are reachable from it.
 - Scanning the root objects in the native-image heap (i.e., static fields) to determine which classes are reachable from them.
It starts from the entry points of the application (i.e., the `main` method).
The newly discovered elements are iteratively scanned until further scanning yields no additional changes in element reachability.

Only *reachable* elements are included in the final image.
Once an image is built, no new elements can be added at runtime, for example through class loading.
We refer to this constraint as the *Closed-World Assumption*.
