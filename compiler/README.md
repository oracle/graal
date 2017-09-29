Graal is a dynamic compiler written in Java that integrates with the HotSpot JVM. It has a focus on high performance and extensibility.
In addition, it provides optimized performance for [Truffle](https://github.com/graalvm/truffle)-based languages running on the JVM.

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

Graal depends on a JDK that supports JVMCI ([JVM Compiler Interface](https://bugs.openjdk.java.net/browse/JDK-8062493)).
Graal works with build 181 or later of [JDK9](https://jdk9.java.net/download/).
JVMCI-enabled builds of JDK8 for selected platforms are available via [OTN](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html).
If you are not on one of these platforms (e.g., Windows), see [Building JVMCI JDK8](#building-jvmci-jdk8) below.

Once you have installed (or built) a JVMCI-enabled JDK, ensure `JAVA_HOME` is pointing at the JDK home directory (or at `<jdk_home>/Contents/Home` on Mac OS X if the JDK has this layout).

Graal also depends on Truffle which needs to be cloned along with Graal.

```
git clone https://github.com/graalvm/graal.git
cd graal/compiler
mx
```

Changing to the `graal/compiler` directory informs mx that the focus of development (called the _primary suite_) is Graal.
All subsequent mx commands should be executed from this directory.

Here's the recipe for building and running Graal (if on Windows, replace mx with mx.cmd):

```
mx build
mx vm
```

By default, Graal is only used for hosted compilation (i.e., the VM still uses C2 for compilation).
To make the VM use Graal as the top tier JIT compiler, add the `-XX:+UseJVMCICompiler` option to the command line.
To disable use of Graal altogether, use `-XX:-EnableJVMCI`.

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

## Publications and Presentations

For video tutorials, presentations and publications on Graal visit the [Publications](../docs/Publications.md) page.

## Building JVMCI JDK8

To create a JVMCI enabled JDK8 on other platforms (e.g., Windows):

```
hg clone http://hg.openjdk.java.net/graal/graal-jvmci-8
cd graal-jvmci-8
mx --java-home /path/to/jdk8 build
mx --java-home /path/to/jdk8 unittest
export JAVA_HOME=$(mx --java-home /path/to/jdk8 jdkhome)
```

You need to use the same JDK the [OTN](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) downloads are based on as the argument to `--java-home` in the above commands.
The build step above should work on all [supported JDK 8 build platforms](https://wiki.openjdk.java.net/display/Build/Supported+Build+Platforms).
It should also work on other platforms (such as Oracle Linux, CentOS and Fedora as described [here](http://mail.openjdk.java.net/pipermail/graal-dev/2015-December/004050.html)).
If you run into build problems, send a message to the [Graal mailing list](http://mail.openjdk.java.net/mailman/listinfo/graal-dev).
