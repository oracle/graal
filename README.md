Graal is a dynamic compiler written in Java that integrates with the HotSpot JVM. It has a focus on high performance and extensibility. In addition, it provides optimized performance for [Truffle](https://github.com/graalvm/truffle) based languages running on the JVM.

## Building Graal

To simplify Graal development, a separate Python tool called [mx](https://github.com/graalvm/mx) has been co-developed. This tool must be downloaded and put onto your PATH:
```
git clone https://github.com/graalvm/mx.git
export PATH=$PWD/mx:$PATH
```
The Graal code depends on JVMCI ([JVM Compiler Interface](https://bugs.openjdk.java.net/browse/JDK-8062493)) and Truffle, both of which need to be cloned along with Graal. To do this, create a working directory (e.g. named `graal`) into which both Graal and JVMCI will be cloned and then use mx to clone both:
```
mkdir graal
cd graal
git clone https://github.com/graalvm/graal-core.git
cd graal-core
mx
```
The `mx` command ensures the JVMCI version in sync with Graal is cloned. Changing to the `graal-core` directory informs mx that the focus of development (called the _primary suite_) is Graal. All subsequent mx commands should be executed from this directory.

After pulling subsequent Graal changes, the `mx sforceimports` command should be run to bring dependencies up to date. 

To build and run Graal, JDK 8 is required. The first time you try to build Graal with mx, you will be asked for the location of the JDK (unless you already have the `JAVA_HOME` environment variable pointing to it).

Here's the simple recipe for building and running the GraalVM (If on Windows, replace mx with mx.cmd):
```
mx build
mx vm
```
The first time it's run, the `mx build` step above will present you with a dialogue asking which VM you will be using by default. The choices are described below. If you are using Graal with Truffle, you should select `server`, otherwise select `jvmci`.

## VM types

* The `server` configuration is a standard HotSpot VM that includes the runtime support for Graal but uses the existing compilers for normal compilation (e.g., when the interpreter threshold is hit for a method). Compilation with Graal is only done by explicit requests to the Graal API.
* The `jvmci` configuration is a VM where normal compilations are performed by Graal. Note that if tiered compilation is enabled (i.e., `-XX:+TieredCompilation`), Graal will be used at the last tier while C1 will be used for the first compiler tier.

The build step above should work on all [supported JDK 8 build platforms](https://wiki.openjdk.java.net/display/Build/Supported+Build+Platforms). It should also work on other platforms (such as Oracle Linux, CentOS and Fedora as described [here](http://mail.openjdk.java.net/pipermail/graal-dev/2015-December/004050.html)). If you run into build problems, send a message to the [Graal mailing list](http://mail.openjdk.java.net/mailman/listinfo/graal-dev).

## IDE Configuration

You can generate IDE project configurations by running:
```
mx ideinit
```
This will generate both Eclipse and NetBeans project configurations. Further information on how to import these project configurations into Eclipse can be found [here](docs/Eclipse.md).

The Graal code base includes the [Ideal Graph Visualizer](http://ssw.jku.at/General/Staff/TW/igv.html) which is very useful in terms of visualizing Graal's intermediate representation (IR). You can get a quick insight into this tool by running the commands below. The first command launches the tool and the second runs one of the unit tests included in the Graal code base with extra options to make Graal output the IR for all methods it compiles to the tool. You should wait for the GUI to appear before running the second command.
```
mx igv &
mx unittest -G:Dump= BC_athrow0
```
If you selected `jvmci` as the default VM above, you will see IR for compilations requested by the VM itself in addition to compilations requested by the unit test. The former are those with a prefix in the UI denoting the compiler thread and id of the compilation (e.g., `JVMCI CompilerThread0:390`).

The first time you run `mx igv`, the Ideal Graph Visualizer will be transparently built. This only works if `ant` has internet access because it needs to download the NetBeans platform packages. You therefore have to configure `ant` to use proxies if necessary (e.g., set `ANT_ARGS=-autoproxy` in your environment).

Further information can be found on the [Debugging](docs/Debugging.md) page.

### Publications and Presentations

For video tutorials, presentations and publications on Graal visit the [Publications](docs/Publications.md) page.
