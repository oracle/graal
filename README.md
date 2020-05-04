# Espresso :coffee:
A meta-circular Java bytecode interpreter for the [GraalVM](https://github.com/oracle/graal).  
Espresso is a fully meta-circular implementation of [The Java Virtual Machine Specification, Java SE 8 Edition](https://docs.oracle.com/javase/specs/jvms/se8/html/index.html), written in Java, capable of running non-trivial programs at speed.  
A Java bytecode interpreter at its core, turned Just-In-Time (JIT) compiler by leveraging [Truffle](https://github.com/oracle/graal/tree/master/truffle) and the Graal compiler on the GraalVM.  
It highlights the sublime potential of the GraalVM as a platform for implementing high-performance languages and runtimes.

## Status
Espresso is still an early prototype, it already passes **>99.99%** of the JCK 8b runtime suite.  
Espresso can compile itself with both `javac` and (the Eclipse Java Compiler) `ecj`.  
It features complete meta-circularity: it can run itself any amount of layers deep, preserving all the capabilities (Unsafe, JNI, Reflection...) of the base layer. Running HelloWorld on three nested layers of Espresso takes **~15 minutes**.  

Espresso's development (Espresso on HotSpot) is supported only on Linux.  
**Espresso's native image runs on Linux, MacOS and Windows.**

## Setup
Espresso needs some patches (in the graal repo) to run; checkout the `slimbeans` branch on the graal repo (internal branch not available on GitHub):
```bash
cd ../graal
git checkout slimbeans
```
Always use `master` for Espresso and the `slimbeans` branch on the graal repo.

### Building _Espresso_

Set your (JVMCI-enabled) JDK via `mx` argument  e.g. `mx --java-home /path/to/java/home ...` or via `export JAVA_HOME=/path/to/java/home`.

`mx build`-ing Espresso creates a GraalVM with Espresso included.

To build the default configuration (interpreter-only), on the `espresso` repository:
```bash
mx build
```

Other configurations are provided:  
```bash
mx --env jvm build       # GraalVM CE + Espresso jars (interpreter only)
mx --env jvm-ce build    # GraalVM CE + Espresso jars (JIT)
mx --env native-ce build # GraalVM CE + Espresso native (JIT)
mx --env jvm-ee build    # GraalVM EE + Espresso jars (JIT)
mx --env native-ee build # GraalVM EE + Espresso native (JIT)

# Use the same --env argument used to build.
export ESPRESSO=`mx --env native-ce graalvm-home`/bin/espresso
```

Configuration files: `mx.espresso/{jvm,jvm-ce,native-ce,jvm-ee,native-ee}` and `mx.espresso/native-image.properties`

### Running Espresso
`mx espresso` runs Espresso (from jars or native) from within a GraalVM. It mimics the `java` (8) command. Bare `mx espresso` runs Espresso on interpreter-only mode.

```bash
mx --env jvm-ce build # Always build first
mx --env jvm-ce espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

To build and run Espresso native image:
```bash
mx --env native-ce build # Always build first
mx --env native-ce espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

The `mx espresso` launcher adds some overhead, to execute Espresso native image directly use:
```bash
mx --env native-ce build # Always build first
export ESPRESSO=`mx --env native-ce graalvm-home`/bin/espresso
time $ESPRESSO -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

### `mx espresso-standalone ...`
To run Espresso on a vanilla JDK (8) and/or not within a GraalVM use `mx espresso-standalone ...`, it mimics the `java` (8) command. The launcher adds all jars and properties required to run Espresso on any vanilla JDK (8).

To debug Espresso:
```
mx build
mx -d espresso-standalone -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

It can also run on a GraalVM with JIT compilation:
```bash
mx build
mx --dy /compiler --jdk jvmci espresso-standalone -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

### Running _Espresso_ unit tests
Espresso runs a sub-set of the Graal compiler tests. For performance reasons, most unit tests are executed in the same context.
```bash
mx build
mx unittest --suite espresso
```

### Terminal tetris
`mx espresso-playground` is a handy shortcut to run test programs bundled with Espresso (espresso-playground distribution).
```bash
mx build
mx espresso-playground Tetris
# Or also
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris

# MacOS does not support Espresso on HotSpot, use the native image instead.
mx --env native-ce build
mx --env native-ce espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

### Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso-standalone -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```


## _Espressoⁿ_ Java-ception
**Self-hosting requires a Linux distribution with an up-to-date glibc.**
Use `mx espresso-meta` to run programs on Espresso². Be sure to prepend `LD_DEBUG=unused` to overcome a known **glibc** bug.  
To run Tetris on Espresso² execute the following:
```bash
mx build
LD_DEBUG=unused mx --dy /compiler --jdk jvmci espresso-meta -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```
It takes some time for both (nested) VMs to boot, only the base layer is blessed with JIT compilation. Enjoy!
