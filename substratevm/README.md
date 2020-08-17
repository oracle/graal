# Native Image

GraalVM Native Image allows to ahead-of-time compile Java code to a standalone executable, called
a **native image**. This executable includes the application classes, classes
from its dependencies, runtime library classes from JDK and statically linked
native code from JDK. It does not run on the Java VM, but includes necessary
components like memory management and thread scheduling from a different virtual
machine, called "Substrate VM". Substrate VM is the name for the runtime
components (like the deoptimizer, garbage collector, thread scheduling etc.).
The resulting program has faster startup time and lower runtime memory overhead
compared to a Java VM.

The **Native Image builder** or `native-image` is a utility that processes all
the classes of your application and their dependencies, including those from the
JDK. It analyses these classes to determine which classes, methods and fields
are reachable during application execution. It then ahead-of-time compiles all
reachable code and data into a native executable for a specific operating system
and architecture. This entire process is called **image build time** to
clearly distinguish it from the compilation of Java source code to bytecode.

GraalVM Native Image supports JVM-based languages, e.g., Java, Scala, Clojure,
Kotlin. The resulting native image can, optionally, execute dynamic languages
like JavaScript, Ruby, R or Python. Polyglot embeddings can also be compiled
ahead-of-time. To inform `native-image` of guest languages used by an
application, specify `--language:<languageId>` for each guest language used
(e.g., `--language:js`).

### License
GraalVM Native Image is licensed under the GPL 2 with Classpath Exception.

## Install Native Image

Native Image is distributed as a separate installable and can be added to the core installation with the [GraalVM Updater](https://www.graalvm.org/docs/reference-manual/gu/) tool.

If you use GraalVM, run this command to install Native Image from GitHub:
```
gu install native-image
```

After this additional step, the `native-image` executable will become available in
the `bin` directory.

Take a look at the [native image generation](https://www.graalvm.org/docs/examples/native-list-dir/) or [compiling a Java and Kotlin app ahead-of-time](https://www.graalvm.org/docs/examples/java-kotlin-aot/) samples.

## Prerequisites

For compilation `native-image` depends on the local toolchain. Install
 `glibc-devel`, `zlib-devel` (header files for the C library and `zlib`)
and `gcc`, using a package manager available on your OS. Some Linux distributions may additionally require `libstdc++-static`.

On Oracle Linux use `yum` package manager:
```
sudo yum install gcc glibc-devel zlib-devel
```
You can still install `libstdc++-static` as long as the optional repositories are enabled (_ol7_optional_latest_ on Oracle Linux 7 and _ol8_codeready_builder_ on Oracle Linux 8).

On  Ubuntu Linux use `apt-get` package manager:
```
sudo apt-get install build-essential libz-dev zlib1g-dev
```
On other Linux distributions use `dnf` package manager:
```
sudo dnf install gcc glibc-devel zlib-devel libstdc++-static
```
On macOS use `xcode`:
```
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

### How to Determine What Version of GraalVM an Image Is Generated With?

Assuming you have a Java class file _EmptyHello.class_ containing an empty main method
and have generated an empty shared object `emptyhello` with GraalVM Native Image Generator utility of it:
```
native-image -cp hello EmptyHello
[emptyhello:11228]    classlist:     149.59 ms
...
```

If you do not know what GraalVM distribution is set to the `PATH` environment
variable, how to determine if a native image was compiled with Community or
Enterprise Edition? Run this command:

```
strings emptyhello | grep com.oracle.svm.core.VM
```

The expected output should match the following:
```
com.oracle.svm.core.VM GraalVM 20.2.0 Java 11 EE
```

**Note:**
Python source code or LLVM bitcode interpreted or compiled with GraalVM
Community Edition will not have the same security characteristics as the same
code interpreted or compiled using GraalVM Enterprise Edition. There is a
GraalVM string embedded in each image that allows to figure out the version and
variant of the base (Community or Enterprise) used to build an image.
The following command will query that information from an image:
```
strings <path to native-image exe or shared object> | grep com.oracle.svm.core.VM
```
Here is an example output:
```
com.oracle.svm.core.VM.Target.LibC=com.oracle.svm.core.posix.linux.libc.GLibC
com.oracle.svm.core.VM.Target.Platform=org.graalvm.nativeimage.Platform$LINUX_AMD64
com.oracle.svm.core.VM.Target.StaticLibraries=liblibchelper.a|libnet.a|libffi.a|libextnet.a|libnio.a|libjava.a|libfdlibm.a|libzip.a|libjvm.a
com.oracle.svm.core.VM=GraalVM 20.2.0 Java 11
com.oracle.svm.core.VM.Target.Libraries=pthread|dl|z|rt
com.oracle.svm.core.VM.Target.CCompiler=gcc|redhat|x86_64|10.2.1
```
If the image was build with Oracle GraalVM Enterprise Edition the output would instead contain:
```
com.oracle.svm.core.VM=GraalVM 20.2.0 Java 11 EE
```

## Ahead-of-time Compilation Limitations

There is a small portion of Java features are not susceptible to ahead-of-time
compilation, and will therefore miss out on the performance advantages. To be
able to build a highly optimized native executable, GraalVM runs an aggressive static
analysis that requires a closed-world assumption, which means that all classes
and all bytecodes that are reachable at run time must be known at build time.
Therefore, it is not possible to load new data that have not been available
during ahead-of-time compilation. Continue reading to the [GraalVM Native Image Compatibility and Optimization Guide](Limitations.md).
