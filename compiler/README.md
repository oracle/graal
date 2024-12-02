# GraalVM Compiler

The GraalVM compiler is a dynamic compiler written in Java that integrates with the HotSpot JVM. It has a focus on high performance and extensibility.
In addition, it provides optimized performance for languages implemented with [Truffle Framework](../truffle)-based languages running on the JVM.
For brevity, the GraalVM compiler is often referred to as "the compiler" below.

## Setup

Working with the GraalVM compiler will mean cloning more than one repository and so it's
recommended to create and use a separate directory:

```bash
$ mkdir graal-ws
$ cd graal-ws
```

## Building the GraalVM compiler

To simplify development, a separate Python tool called [mx](https://github.com/graalvm/mx) has been co-developed.
This tool must be downloaded and put onto your PATH:

```bash
$ git clone https://github.com/graalvm/mx.git
$ export PATH=$PWD/mx:$PATH
```

Set up `JAVA_HOME` to point to a JDK compatible with Graal:
```bash
$ mx fetch-jdk labsjdk-ce-latest
# Follow instructions emitted by the above command to set JAVA_HOME
```

Change to the `graal/compiler` directory:
```bash
$ cd graal/compiler
```

Changing to the `graal/compiler` directory informs mx that the focus of development (called the _primary suite_) is the GraalVM compiler.
All subsequent mx commands should be executed from this directory.

Here's the recipe for building the VM and running a simple app with it:

```bash
$ mx build
$ mx vm -Djdk.graal.ShowConfiguration=info ../compiler/src/jdk.graal.compiler.test/src/jdk/graal/compiler/test/CountUppercase.java
```

When running `mx vm`, the GraalVM compiler is used as the top tier JIT compiler by default.

## IDE Configuration

You can generate IDE project configurations by running:

```bash
$ mx ideinit
```

This will generate Eclipse, IntelliJ, and NetBeans project configurations.
Further information on how to import these project configurations into individual IDEs can be found on the [IDEs](docs/IDEs.md) page.

## IGV

The [Ideal Graph Visualizer](https://www.graalvm.org/latest/tools/igv/)(IGV) is very useful in terms of visualizing the compiler's intermediate representation (IR).
You can get a quick insight into this tool by running the commands below.
The first command launches the tool and the second runs one of the unit tests included in the code base with extra options to dump the compiler IR for all methods compiled.
You should wait for the GUI to appear before running the second command.

```bash
$ mx igv &
$ mx unittest -Djdk.graal.Dump -Djdk.graal.PrintGraph=Network BC_athrow0
```

> Launching IGV may fail if the `JAVA_HOME` is not compatible with the version of the NetBeans
> platform on which IGV is based. Running `mx help igv` will provide more help in this context
> and you can use `mx fetch-jdk` to get a compatible JDK.

If you add `-XX:+UseJVMCICompiler` (after `unittest`), you will see IR for compilations requested by the VM itself in addition to compilations requested by the unit test.
The former are those with a prefix in the UI denoting the id of the compilation.

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
to only be compiled by C1 (i.e., `-Djdk.graal.CompileGraalWithC1Only=true`) but this comes at the cost
of slower compilation speed.

To address these issues, the GraalVM compiler can be deployed as a native shared library. The shared
library is a native image produced using [Native Image](../substratevm/README.md). In this mode,
the compiler uses memory separate from the HotSpot heap and it runs compiled
from the start. That is, it has execution properties similar to other native HotSpot
compilers such as C1 and C2.

To build libgraal:

```bash
$ cd graal/vm
$ mx --env libgraal build
```
The newly built GraalVM image containing libgraal is shown by:
```bash
$ mx --env libgraal graalvm-home
```
or by following this symlink:
```bash
$ ./latest_graalvm_home
```

For more information about building Native Images, see the [README file of the vm suite](../vm/README.md).

Without leaving the `graal/vm` directory, you can now run libgraal as follows:

1. Use the GraalVM image that you just built:

    ```bash
    $ ./latest_graalvm_home/bin/java ...
    ```

2. Use `mx`:
    ```bash
    $ mx -p ../compiler vm -XX:JVMCILibPath=latest_graalvm_home/lib  ...
    ```

## Publications and Presentations

For video tutorials, presentations and publications on the GraalVM compiler visit the [Publications](../docs/Publications.md) page.
