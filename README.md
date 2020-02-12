# Espresso :coffee:
A meta-circular Java bytecode interpreter for the [GraalVM](https://github.com/oracle/graal).  
Espresso is a fully meta-circular implementation of [The Java Virtual Machine Specification, Java SE 8 Edition](https://docs.oracle.com/javase/specs/jvms/se8/html/index.html), written in Java, capable of running non-trivial programs at speed.  
A Java bytecode interpreter at its core, turned Just-In-Time (JIT) compiler by leveraging [Truffle](https://github.com/oracle/graal/tree/master/truffle) and the Graal compiler on the GraalVM.  
It highlights the sublime potential of the GraalVM as a platform for implementing high-performance languages and runtimes.

## Status
Espresso is still an early prototype, but it already passes **>99.99%** of the JCK 8b runtime suite.  
It can run some non-trivial applications:
  - Eclipse Neon (4.6)
  - Minecraft 1.2
  - Scala REPLs (2.11-13 + Dotty)
  - Nashorn
  - Groovy REPL
  - [Mochadoom](https://github.com/AXDOOMER/mochadoom) Doom Java port
  - [kotNES](https://github.com/suchaHassle/kotNES) NES emulator written in Kotlin
  - [coffee-gb](https://github.com/trekawek/coffee-gb) GB Color emulator
  - jEdit 5.5.0

Espresso can compile itself with both `javac` and the Eclipse Java Compiler `ecj`.  
It features complete meta-circularity: it can run itself any amount of layers deep, preserving all the capabilities (Unsafe, JNI, Reflection...) of the base layer. Running HelloWorld on three nested layers of Espresso takes **~15 minutes**.  

Espresso's development (Espresso on HotSpot) is supported only on Linux.  
**Espresso's native image runs on Linux and MacOS.**

## Setup
Espresso needs some patches (in the graal repo) to run; checkout the `slimbeans` branch on the graal repo (internal branch not available on GitHub):
```bash
cd ../graal
git checkout slimbeans
```
Always use `master` for Espresso and the `slimbeans` branch on the graal repo.

### Building _Espresso_
On the `espresso` repository:
```bash
mx build
mx unittest --suite espresso
```

### Building _Espresso_ native image
The Espresso native image is built as part of a GraalVM, it reuses all the jars and native libraries bundled with GraalVM. 
```bash
mx --env native-ce build
export ESPRESSO=`mx --env native-ce graalvm-home`/bin/espresso

# Run HelloWorld
mx build
time $ESPRESSO -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```
Configuration files: `mx.espresso/{jvm,jvm-ce,native-ce}` and `mx.espresso/native-image.properties`

### Running _Espresso_ unit tests
Espresso runs a sub-set of the Graal compiler tests. For performance reasons, most unit tests are executed in the same context.
```bash
mx unittest --suite espresso
```

## Running _Espresso_
`mx espresso` mimics `java` (8). By default `mx espresso` runs on interpreter-only mode.
```bash
mx espresso -help
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
# or just
mx espresso-playground Tetris
```

### _Espresso_ + compilation enabled
```bash
mx --dy /compiler --jdk jvmci espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
mx --dy /compiler --jdk jvmci espresso-playground TestMain
```

### Terminal tetris
`mx espresso-playground` is a handy shortcut to run test programs bundled with Espresso (espresso-playground distribution).
```bash
mx espresso-playground Tetris
# Or also
mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris

# MacOS does not support Espresso on HotSpot, use the native image instead.
$ESPRESSO -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

### Dumping IGV graphs
```bash
mx -v --dy /compiler --jdk jvmci -J"-Dgraal.Dump=:4 -Dgraal.TraceTruffleCompilation=true -Dgraal.TruffleBackgroundCompilation=false" espresso -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
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
