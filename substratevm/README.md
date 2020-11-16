# Native Image

Native Image is a technology to ahead-of-time compile Java code to a standalone executable, called a **native image**.
This executable includes the application classes, classes from its dependencies, runtime library classes, and statically linked native code from JDK.
It does not run on the Java VM, but includes necessary components like memory management, thread scheduling, and so on from a different runtime system, called "Substrate VM".
Substrate VM is the name for the runtime components (like the deoptimizer, garbage collector, thread scheduling etc.).
The resulting program has faster startup time and lower runtime memory overhead compared to a JVM.

The **Native Image builder** or `native-image` is a utility that processes all classes of an application and their dependencies, including those from the JDK.
It statically analyzes these data to determine which classes and methods are reachable during the application execution.
Then it ahead-of-time compiles that reachable code and data to a native executable for a specific operating system and architecture.
This entire process is called **building an image** (or the **image build time**) to clearly distinguish it from the compilation of Java source code to bytecode.

Native Image supports JVM-based languages, e.g., Java, Scala, Clojure, Kotlin.
The resulting image can, optionally, execute dynamic languages like
JavaScript, Ruby, R or Python. Polyglot embeddings can also be compiled
ahead-of-time. To inform `native-image` of a guest language used by an
application, specify `--language:<languageId>` for each guest language (e.g.,
`--language:js`).

* [Install Native Image](#install-native-image)
* [Prerequisites](#prerequisites)
* [Build a Native Image](#build-a-native-image)
* [Build a Shared Library](#build-a-shared-library)
* [How to Determine What Version of GraalVM a Native Image is Generated with](#how-to-determine-what-version-of-graalvm-a-native-image-is-generated-with)
* [Ahead-of-time Compilation Limitations](#ahead-of-time-compilation-limitations)

### License

The Native Image technology is distributed as a separate installable to GraalVM.
Native Image for GraalVM Community Edition is licensed under the [GPL 2 with Classpath Exception](https://github.com/oracle/graal/blob/master/substratevm/LICENSE).

## Install Native Image

Native Image can be added to GraalVM with the [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/gu/) tool.

Run this command to install Native Image:
```shell
gu install native-image
```
After this additional step, the `native-image` executable will become available in
the `GRAALVM_HOME/bin` directory.

The above command will install Native Image from the GitHub catalog for GraalVM Community users.
For GraalVM Enterprise users, the [manual installation](https://www.graalvm.org/reference-manual/graalvm-updater/#manual-installation) is required.

## Prerequisites

For compilation `native-image` depends on the local toolchain. Install
 `glibc-devel`, `zlib-devel` (header files for the C library and `zlib`)
and `gcc`, using a package manager available on your OS. Some Linux distributions may additionally require `libstdc++-static`.

On Oracle Linux use `yum` package manager:
```shell
sudo yum install gcc glibc-devel zlib-devel
```
You can still install `libstdc++-static` as long as the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7 and _ol8_codeready_builder_ on Oracle Linux 8).

On  Ubuntu Linux use `apt-get` package manager:
```shell
sudo apt-get install build-essential libz-dev zlib1g-dev
```
On other Linux distributions use `dnf` package manager:
```shell
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```
On macOS use `xcode`:
```shell
xcode-select --install
```

#### Prerequisites for Using Native Image on Windows
To make use of Native Image on Windows, follow the further recommendations. The
required Microsoft Visual C++ (MSVC) version depends on the JDK version that
GraalVM is based on. For GraalVM distribution based on JDK 8, you will need MSVC
2010 SP1 version. The recommended installation method is using Microsoft Windows
SDK 7.1:
1. Download the SDK file `GRMSDKX_EN_DVD.iso` for from [Microsoft](https://www.microsoft.com/en-gb/download).
2. Mount the image by opening `F:\Setup\SDKSetup.exe` directly.

For GraalVM distribution based on JDK 11, you will need MSVC 2017 15.5.5 or later version.

The last prerequisite, common for both GraalVM distribution based on JDK 11 and JDK 8, is the proper [Developer Command Prompt](https://docs.microsoft.com/en-us/cpp/build/building-on-the-command-line?view=vs-2019#developer_command_prompt_shortcuts) for your version of [Visual Studio](https://visualstudio.microsoft.com/vs/). On Windows the `native-image` tool only works when it is executed from the **x64 Native Tools Command Prompt**.

## Build a Native Image

A native image can be built as a standalone executable, which is the default, or as a shared library (see [Build a Shared Library](#build-a-shared-library)).
For an image to be useful, it needs to have at least one entry point method.
For standalone executables, Native Image supports Java main methods with a signature that takes the command line arguments as an array of strings:
```java
public static void main(String[] arg) { /* ... */ }
```
The executable images can have an arbitrary number of entry points, for example, to implement callbacks or APIs.

To build a native image of a Java class file in the current working directory, use:
```shell
native-image [options] class [imagename] [options]
```

To build a native image of a JAR file, use:
```shell
native-image [options] -jar jarfile [imagename] [options]
```

The `native-image` command needs to provide the class path for all classes using
the familiar option from the java launcher: `-cp` followed by a list of
directories or JAR files, separated by `:` on Linux and macOS platforms, or `;` on Windows.
The name of the class containing the
main method is the last argument, or you can use `-jar` and provide a JAR
file that specifies the main method in its manifest.

As an example, take this small Java program that reverses a String using recursion:
```java
public class Example {

    public static void main(String[] args) {
        String str = "Native Image is awesome";
        String reversed = reverseString(str);
        System.out.println("The reversed string is: " + reversed);
    }

    public static String reverseString(String str) {
        if (str.isEmpty())
            return str;
        return reverseString(str.substring(1)) + str.charAt(0);
    }
}
```
Compile it and build a native image from the Java class:
```shell
javac Example.java
native-image Example
```
The native image builder ahead-of-time compiles the `Example` class into a
standalone executable, `example`, in the current working directory. Run the executable:
```shell
./example
```

Another option to the native image builder that might be helpful is
`--install-exit-handlers`. It is not recommended to register the default signal
handlers when building a shared library. However, it is desirable to include
signal handlers when building a native image for containerized environments, like
Docker containers. The `--install-exit-handlers` option gives you the same
signal handlers that a JVM does.

For more complex examples, visit the [native image generation](https://www.graalvm.org/docs/examples/native-list-dir/) or [compiling a Java and Kotlin app ahead-of-time](https://www.graalvm.org/docs/examples/java-kotlin-aot/) pages.

## Build a Shared Library

To build a native image as a shared library of a Java class file, pass  `--shared` to the native image builder.
The created shared library will have the main method of the given Java class as its entrypoint method.
```shell
native-image class [libraryname] --shared
```
To build a native image as a shared library of a JAR file, use:
```shell
native-image -jar jarfile [libraryname] --shared
```

Note: if you build a shared library where you do not specify a main class, you must append the `-H:Name=` flag to specify the library name: `-H:Name=libraryname`.

As mentioned in the previous section, you need to have at least one entry point method for a native image to be useful.
For shared libraries, Native Image provides the `@CEntryPoint` annotation to specify entry point methods that should be exported and callable from C.
Entry point methods must be static and may only have non-object parameters and return types – this includes Java primitives, but also Word types (including pointers). One of the parameters of an entry point method has to be of type `IsolateThread` or `Isolate`. This parameter provides the current thread's execution context for the call.

For example:

```java
@CEntryPoint static int add(IsolateThread thread, int a, int b) {
    return a + b;
}
```

When building a shared library, an additional C header file is generated.
This header file contains declarations for the [C API](C-API.md), which allows creating isolates and attaching threads from C code, as well as declarations for each entry point in the source code. The generated C declaration for the above example is:
```c
int add(graal_isolatethread_t* thread, int a, int b);
```

Shared library images and executable images alike can have an arbitrary number of entry points, for example, to implement callbacks or APIs.

## How to Determine What Version of GraalVM an Image Is Generated with

Assuming you have a Java class file, _EmptyHello.class_ , containing an empty main method and have generated an empty shared object emptyhello with the Native Image builder:
```shell
native-image -cp hello EmptyHello
[emptyhello:11228]    classlist:     149.59 ms
...
```

If you do not know what GraalVM distribution is set to the `PATH` environment
variable, how to determine if a native image was compiled with Community or
Enterprise Edition? Run this command:
```shell
strings emptyhello | grep com.oracle.svm.core.VM
```

The expected output should match the following:
```shell
com.oracle.svm.core.VM GraalVM <version> Java 11 EE
```

**Note:**
Python source code or LLVM bitcode interpreted or compiled with GraalVM Community Edition will not have the same security characteristics as the same code interpreted or compiled using GraalVM Enterprise Edition.
There is a GraalVM string embedded in each image that allows to figure out the version and variant of the base (Community or Enterprise) used to build an image.
The following command will query that information from an image:
```shell
strings <path to native-image exe or shared object> | grep com.oracle.svm.core.VM
```

Here is an example output:
```shell
com.oracle.svm.core.VM.Target.LibC=com.oracle.svm.core.posix.linux.libc.GLibC
com.oracle.svm.core.VM.Target.Platform=org.graalvm.nativeimage.Platform$LINUX_AMD64
com.oracle.svm.core.VM.Target.StaticLibraries=liblibchelper.a|libnet.a|libffi.a|libextnet.a|libnio.a|libjava.a|libfdlibm.a|libzip.a|libjvm.a
com.oracle.svm.core.VM=GraalVM <version> Java 11
com.oracle.svm.core.VM.Target.Libraries=pthread|dl|z|rt
com.oracle.svm.core.VM.Target.CCompiler=gcc|redhat|x86_64|10.2.1
```

If the image was build with Oracle GraalVM Enterprise Edition the output would instead contain:
```shell
com.oracle.svm.core.VM=GraalVM <version> Java 11 EE
```

## Ahead-of-time Compilation Limitations

There is a small portion of Java features are not susceptible to ahead-of-time
compilation, and will therefore miss out on the performance advantages. To be
able to build a highly optimized native executable, GraalVM runs an aggressive static
analysis that requires a closed-world assumption, which means that all classes
and all bytecodes that are reachable at run time must be known at build time.
Therefore, it is not possible to load new data that have not been available
during ahead-of-time compilation. Continue reading to [GraalVM Native Image Compatibility and Optimization](Limitations.md).
