The GraalVM compiler is a dynamic compiler written in Java that integrates with the HotSpot JVM. It has a focus on high performance and extensibility.
In addition, it provides optimized performance for languages implemented with [Truffle Framework](https://github.com/graalvm/graal/tree/master/truffle)-based languages running on the JVM.
For brevity, the GraalVM compiler is often referred to as "the compiler" below.

## Setup

Working with the GraalVM compiler will mean cloning more than one repository and so it's
recommended to create and use a separate directory:

```
mkdir graal
cd graal
```

## Building the GraalVM compiler

To simplify development, a separate Python tool called [mx](https://github.com/graalvm/mx) has been co-developed.
This tool must be downloaded and put onto your PATH:

```
git clone https://github.com/graalvm/mx.git
export PATH=$PWD/mx:$PATH
```

The compiler depends on a JDK that supports a compatible version of JVMCI ([JVM Compiler Interface](https://bugs.openjdk.java.net/browse/JDK-8062493)).
There is a JVMCI [port](https://github.com/graalvm/graal-jvmci-8) for JDK 8 and the required JVMCI version is built into the JDK as of JDK 11.
A JVMCI-enabled JDK 8 can be downloaded from [GitHub](https://github.com/graalvm/openjdk8-jvmci-builder/releases)
or you can [build](#building-jvmci-jdk8) it yourself.

The JVMCI JDKs that Graal is currently tested against are specified in [common.json](../common.json).

Most compiler sources are compliant with Java 8. Some sources use API specific to JDK 8 or only introduced in JDK 9.
These sources are in [versioned projects](https://github.com/graalvm/mx#versioning-sources-for-different-jdk-releases).
If you don't have a JDK that satisfies the requirement of a versioned project, the project is ignored by mx.

If you want to develop on a single JDK version, you only need to define `JAVA_HOME`. For example:
```
export JAVA_HOME=/usr/lib/jvm/oraclejdk1.8.0_212-jvmci-20-b01
```
or:
```
export JAVA_HOME=/usr/lib/jvm/jdk-11
```

If you want to ensure your changes will pass both JDK 8 and JDK 11 gates, you can specify the secondary JDK(s) in `EXTRA_JAVA_HOMES`.
For example, to develop for JDK 8 while ensuring `mx build` still works with the JDK 11 specific sources:

```
export JAVA_HOME=/usr/lib/jvm/oraclejdk1.8.0_212-jvmci-20-b01
export EXTRA_JAVA_HOMES=/usr/lib/jvm/jdk-11
```
And on macOS:
```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/oraclejdk1.8.0_212-jvmci-20-b01/Contents/Home
export EXTRA_JAVA_HOMES=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
```
If you omit `EXTRA_JAVA_HOMES` in the above examples, versioned projects depending on the specified JDK(s) will be ignored.
Note that `JAVA_HOME` defines the *primary* JDK for development. For instance, when running `mx vm`, this is the JDK that will be used so if you want to run on JDK 11, swap JDK 8 and JDK 11 in `JAVA_HOME` and `EXTRA_JAVA_HOMES`.

Now change to the `graal/compiler` directory:
```
cd graal/compiler
```

Changing to the `graal/compiler` directory informs mx that the focus of development (called the _primary suite_) is the GraalVM compiler.
All subsequent mx commands should be executed from this directory.

Here's the recipe for building and running the GraalVM compiler:

```
mx build
mx vm
```

When running `mx vm`, the GraalVM compiler is used as the top tier JIT compiler by default. To revert to using C2 instead,
add the `-XX:-UseJVMCICompiler` option to the command line.
To disable use of the GraalVM compiler altogether (i.e. for hosted compilations as well), use `-XX:-EnableJVMCI`.

### Windows Specifics

When applying above steps on Windows, replace `export` with `set`.

## IDE Configuration

You can generate IDE project configurations by running:

```
mx ideinit
```

This will generate Eclipse, IntelliJ, and NetBeans project configurations.
Further information on how to import these project configurations into individual IDEs can be found on the [IDEs](docs/IDEs.md) page.

The [Ideal Graph Visualizer](https://www.graalvm.org/docs/reference-manual/tools/#ideal-graph-visualizer)(IGV) is very useful in terms of visualizing the compiler's intermediate representation (IR).
IGV is available on [OTN](https://www.oracle.com/downloads/graalvm-downloads.html).
You can get a quick insight into this tool by running the commands below.
The first command launches the tool and the second runs one of the unit tests included in the code base with extra options to dump the compiler IR for all methods compiled.
You should wait for the GUI to appear before running the second command.

```
$GRAALVM_EE_HOME/bin/idealgraphvisualizer &
mx unittest -Dgraal.Dump BC_athrow0
```

If you added `-XX:+UseJVMCICompiler` as described above, you will see IR for compilations requested by the VM itself in addition to compilations requested by the unit test.
The former are those with a prefix in the UI denoting the compiler thread and id of the compilation (e.g., `JVMCI CompilerThread0:390`).

Further information can be found on the [Debugging](docs/Debugging.md) page.

## libgraal

Building the GraalVM compiler as described above means it is executed in the same way as any
other Java code in the VM; it allocates in the HotSpot heap and it starts execution
in the interpreter with hot parts being subsequently JIT compiled.
The advantage of this mode is that it can be debugged with a Java debugger.

However, it has some disadvantages. Firstly, since it uses the object heap, it can
reduce application object locality and increase GC pause times. Additionally, it can
complicate fine tuning options such as `-Xmx` and `-Xms` which now need to take the
heap usage of the compiler into account. Secondly, the compiler will initially be executed
in the interpreter and only get faster over time as its hot methods are JIT
compiled. This is mitigated to some degree by forcing the GraalVM compiler
to only be compiled by C1 (i.e., `-Dgraal.CompileGraalWithC1Only=true`) but this comes at the cost
of slower compilation speed.

To address these issues, the GraalVM compiler can be deployed as a native shared library. The shared
library is a native image produced using [SubstrateVM](../substratevm/README.md). In this mode,
the GraalVM compiler uses memory separate from the HotSpot heap and it runs compiled
from the start. That is, it has execution properties similar to other native HotSpot
compilers such as C1 and C2.

To build libgraal:

```
cd graal/vm
mx --env libgraal build
```
The newly built GraalVM image containing libgraal is available at:
```
mx --env libgraal graalvm-home
```
or following this symlink:
```
./latest_graalvm_home
```
For more information about building GraalVM images, see the [README file of the vm suite](../vm/README.md).

Without leaving the `graal/vm` directory, you can now run libgraal as follows:

1. Use the GraalVM image that you just built:

    ```
    ./latest_graalvm_home/bin/java -XX:+UseJVMCICompiler -XX:+UseJVMCINativeLibrary ...
    ```

2. Use `mx`:
    - On linux:
        ```
        mx -p ../compiler vm -XX:JVMCILibPath=latest_graalvm_home/jre/lib/amd64 -XX:+UseJVMCICompiler -XX:+UseJVMCINativeLibrary ...
        ```
    - On macOS:
        ```
        mx -p ../compiler vm -XX:JVMCILibPath=latest_graalvm_home/jre/lib -XX:+UseJVMCICompiler -XX:+UseJVMCINativeLibrary ...
        ```

## Publications and Presentations

For video tutorials, presentations and publications on the GraalVM compiiler visit the [Publications](../docs/Publications.md) page.

## Building JVMCI JDK 8

For instructions for building a JVMCI enabled JDK 8, refer to the [`graal-jvmci-8` repository](https://github.com/graalvm/graal-jvmci-8).

## License

The GraalVM compiler is licensed under the [GPL 2](LICENSE.md).
