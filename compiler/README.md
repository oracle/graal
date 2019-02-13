Graal is a dynamic compiler written in Java that integrates with the HotSpot JVM. It has a focus on high performance and extensibility.
In addition, it provides optimized performance for [Truffle](https://github.com/graalvm/graal/tree/master/truffle)-based languages running on the JVM.

## Setup

Working with Graal will mean cloning more than one repository and so it's
recommended to create and use a separate directory:

```
mkdir graal
cd graal
```

## Building Graal

To simplify Graal development, a separate Python tool called [mx](https://github.com/graalvm/mx) has been co-developed.
This tool must be downloaded and put onto your PATH:

```
git clone https://github.com/graalvm/mx.git
export PATH=$PWD/mx:$PATH
```

Graal depends on a JDK that supports a compatible version of JVMCI ([JVM Compiler Interface](https://bugs.openjdk.java.net/browse/JDK-8062493)).
There is a JVMCI [port](https://github.com/graalvm/graal-jvmci-8) for JDK 8 and the required JVMCI version is built into the JDK as of JDK 11.
To develop Graal you need either a JVMCI-enabled JDK 8 (download from [OTN](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) or [build](#building-jvmci-jdk8) yourself)
or JDK 11 (build 20 or later).

Most Graal sources are compliant with Java 8. Some sources use API specific to JDK 8 or only introduced in JDK 9.
These sources are in [versioned projects](https://github.com/graalvm/mx#versioning-sources-for-different-jdk-releases).
If you don't have a JDK that satisfies the requirement of a versioned project, the project is ignored by mx.

If you only want to develop Graal for a single JDK version, you only need to define `JAVA_HOME`. For example:
```
export JAVA_HOME=/usr/lib/jvm/labsjdk1.8.0_172-jvmci-0.46
```
or:
```
export JAVA_HOME=/usr/lib/jvm/jdk-11
```

If you want to ensure your changes will pass both JDK 8 and JDK 11 gates, you can specify the secondary JDK(s) in `EXTRA_JAVA_HOMES`.
For example, to develop Graal for JDK 8 while ensuring `mx build` still works with the JDK 11 specific sources:

```
export JAVA_HOME=/usr/lib/jvm/labsjdk1.8.0_172-jvmci-0.46
export EXTRA_JAVA_HOMES=/usr/lib/jvm/jdk-11
```
And on macOS:
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/labsjdk1.8.0_172-jvmci-0.46/Contents/Home
export EXTRA_JAVA_HOMES=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
```
If you omit `EXTRA_JAVA_HOMES` in the above examples, versioned projects depending on the specified JDK(s) will be ignored.
Note that `JAVA_HOME` defines the *primary* JDK for development. For instance, when running `mx vm`, this is the JDK that will be used so if you want to run on JDK 11, swap JDK 8 and JDK 11 in `JAVA_HOME` and `EXTRA_JAVA_HOMES`.

Now change to the `graal/compiler` directory:
```
cd graal/compiler
```

Changing to the `graal/compiler` directory informs mx that the focus of development (called the _primary suite_) is Graal.
All subsequent mx commands should be executed from this directory.

Here's the recipe for building and running Graal:

```
mx build
mx vm
```

By default, Graal is only used for hosted compilation (i.e., the VM still uses C2 for compilation).
To make the VM use Graal as the top tier JIT compiler, add the `-XX:+UseJVMCICompiler` option to the command line.
To disable use of Graal altogether, use `-XX:-EnableJVMCI`.

### Windows Specifics

When applying above steps on Windows, replace `export` with `set`.

## IDE Configuration

You can generate IDE project configurations by running:

```
mx ideinit
```

This will generate Eclipse, IntelliJ, and NetBeans project configurations.
Further information on how to import these project configurations into individual IDEs can be found on the [IDEs](docs/IDEs.md) page.

The Graal code base includes the [Ideal Graph Visualizer](http://ssw.jku.at/General/Staff/TW/igv.html) which is very useful in terms of visualizing Graal's intermediate representation (IR).
You can get a quick insight into this tool by running the commands below.
The first command launches the tool and the second runs one of the unit tests included in the Graal code base with extra options to make Graal dump the IR for all methods it compiles.
You should wait for the GUI to appear before running the second command.

```
mx igv &
mx unittest -Dgraal.Dump BC_athrow0
```

If you added `-XX:+UseJVMCICompiler` as described above, you will see IR for compilations requested by the VM itself in addition to compilations requested by the unit test.
The former are those with a prefix in the UI denoting the compiler thread and id of the compilation (e.g., `JVMCI CompilerThread0:390`).

Further information can be found on the [Debugging](docs/Debugging.md) page.

## libgraal

Building Graal as described above enables it to be used in HotSpot as Java code
called from the VM. In this mode, Graal is executed in the same way as any
other Java code in the VM; it allocates in the HotSpot heap and it starts execution
in the interpreter with hot parts being subsequently JIT compiled.
The advantage of this mode is that Graal can be debugged with a Java debugger.

However, it has some disadvantages. Firstly, since Graal uses the object heap, it can
reduce application object locality and increase GC pause times. Additionally, it can
complicate fine tuning options such as `-Xmx` and `Xms` which now need to take the
heap usage of Graal needs to be taken into account. Secondly, Graal will initially be executed
in the interpreter and only get faster over time as its hot methods are JIT
compiled. This is mitigated to some degree by forcing Graal (and JVMCI)
to only be compiled by C1 but this comes at the cost of lower peak performance for Graal.

To address these issues, Graal can be deployed as a native shared library. The shared
library is produced using [SubstrateVM](../substratevm/README.md) to ahead-of-time compile Graal. In this mode,
Graal uses memory separate from the HotSpot heap and it runs compiled
from the start. That is, it has execution properties similar to other native HotSpot
compilers such as C1 and C2.

To build libgraal, you need to use the `native-image` tool in the `substratevm` suite.

```
cd graal/substratevm
mx build
mx buildlibgraal
```

This will produce a shared library in the current working directory whose name is
compliant with the shared library naming conventions for the platform;
`libjvmcicompiler.dylib` (macOS), `libjvmcicompiler.so` (Linux, Solaris, etc), `graal.dll` (Windows).

To use this library, copy it to the same directory as `libjava.dylib`/`libjava.so`/`java.dll`
in your JVMCI JDK8 installation and use the options `-XX:+UseJVMCICompiler -XX:+UseJVMCINativeLibrary`. Alternatively,
you can directly specify the path to the library as the value to `-XX:JVMCILibPath=`.
For example:
```
mx vm -XX:JVMCILibPath=/path/to/libjvmcicompiler.dylib -XX:+UseJVMCICompiler -XX:+UseJVMCINativeLibrary ...
```

## Publications and Presentations

For video tutorials, presentations and publications on Graal visit the [Publications](../docs/Publications.md) page.

## Building JVMCI JDK 8

To create a JVMCI enabled JDK 8 on other platforms (e.g., Windows):

```
git clone https://github.com/graalvm/graal-jvmci-8
cd graal-jvmci-8
mx --java-home /path/to/jdk8 build
mx --java-home /path/to/jdk8 unittest
export JAVA_HOME=$(mx --java-home /path/to/jdk8 jdkhome)
```

You need to use the same JDK the [OTN](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) downloads are based on as the argument to `--java-home` in the above commands.
The build step above should work on all [supported JDK 8 build platforms](https://wiki.openjdk.java.net/display/Build/Supported+Build+Platforms).
It should also work on other platforms (such as Oracle Linux, CentOS and Fedora as described [here](http://mail.openjdk.java.net/pipermail/graal-dev/2015-December/004050.html)).
If you run into build problems, send a message to the [Graal mailing list](http://mail.openjdk.java.net/mailman/listinfo/graal-dev).

### Windows Specifics

Building JDK requires some bash-like environment. Fortunately, the one that comes as a part of the standard
*Git for Windows* installation will suffice, in which case you will just have to set `MKS_HOME` to point
to the directory with Linux tools, e.g.:

```
set MKS_HOME=<GIT_DIR>\usr\bin
```

where `<GIT_DIR>` is a path to your Git installation directory. It is important that there are **NO**
spaces in the path, otherwise the build will fail.

You will also need an *MSVC 2010 SP1* compiler. The following tool chain is recommended:

1. [Microsoft Windows SDK for Windows 7 and .NET Framework 4 (ISO)](https://www.microsoft.com/en-us/download/details.aspx?id=8442)
2. [Microsoft Visual C++ 2010 Service Pack 1 Compiler Update for the Windows SDK 7.1](https://www.microsoft.com/en-us/download/details.aspx?id=4422)

## License

The Graal compiler is licensed under the [GPL 2](LICENSE.md).
