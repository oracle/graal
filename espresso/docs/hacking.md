# How to work on Espresso

## Building

### Using a pre-built GraalVM

The simplest way to build espresso is to use a pre-built GraalVM.

Download and unpack the latest GraalVM [release](https://www.graalvm.org/downloads/) or [ea build](https://github.com/graalvm/oracle-graalvm-ea-builds/releases).

Set the `JAVA_HOME` environment variable to the extracted result. This will be used during the build and as a host JDK.

Espresso is only a JVM and it needs a JDK to use as a guest. Set the `ESPRESSO_JAVA_HOME` to a JDK to be used for the guest.

To build, run:

```bash
$ mx build
```

### Using native-image from the graal repository

It is also possible to build using native-image built from sources from the current graal repository.

Set your (JVMCI-enabled) JDK via `mx` argument  e.g. `mx --java-home /path/to/java/home ...` or via `export JAVA_HOME=/path/to/java/home`. Or (easiest) run `mx fetch-jdk` to download one.

Set the `ESPRESSO_JAVA_HOME` to a JDK to be used for the guest.

Build using one of the provided configuration:
```bash
$ mx --env jvm build       # GraalVM CE + Espresso jars (interpreter only)
$ mx --env jvm-ce build    # GraalVM CE + Espresso jars (JIT)
$ mx --env native-ce build # GraalVM CE + Espresso native (JIT)
```

`mx build`-ing Espresso creates "espresso standalones" which are JDK-like directories.

If you are only trying to build a specific one it's possible to specify it while building:
```bash
$ mx --env native-ce build --targets=ESPRESSO_NATIVE_STANDALONE
```

Configuration files: `mx.espresso/{jvm,jvm-ce,native-ce}`

## Running Espresso

You can find out where the espresso standalones are by running `mx path --output ...`:
```bash
$ mx path --output ESPRESSO_NATIVE_STANDALONE
$ mx path --output ESPRESSO_JVM_STANDALONE
```
> Note: If you used options like `--env ...` or `--dynamicimports ...` while building, you should also use them with `mx path`: e.g., `mx --env native-ce path ...`.

`mx espresso` runs Espresso from a standalone (jvm or native). It mimics the `java` command.

```bash
$ mx --env jvm-ce build # Always build first
$ mx --env jvm-ce espresso -cp my.jar HelloWorld
```

To build and run Espresso native image:

```bash
$ mx --env native-ce build # Always build first
$ mx --env native-ce espresso -cp my.jar HelloWorld
```

The `mx espresso` launcher adds some overhead, to execute Espresso native image directly use:

```bash
$ mx --env native-ce build # Always build first
$ export ESPRESSO=`mx --quiet --no-warning --env native-ce path --output ESPRESSO_NATIVE_STANDALONE`/bin/java
$ time $ESPRESSO -cp my.jar HelloWorld
```

## Installing JARs to Maven Local

This is useful if you want to depend on your branch of Espresso as a regular Maven dependency, for example to compile it into a custom native image.

```bash
$ cd ../vm
$ mx --dy /espresso,/sulong maven-deploy --tags=public --all-suites --all-distribution-types --version-suite=sdk --suppress-javadoc
```

You can now depend on the jars using a version like `24.2.0-SNAPSHOT`.

## `mx espresso-embedded ...`

To run Espresso on a vanilla JDK and/or not within a standalone use `mx espresso-embedded ...`, it mimics the `java` command. The launcher adds all jars and properties required to run Espresso on any vanilla JDK.

To debug Espresso:

```bash
$ mx build
$ mx -d espresso-embedded -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.HelloWorld
```

## Dumping IGV graphs

```bash
$ mx --env jvm-ce espresso --vm.Djdk.graal.Dump=Truffle:2 --vm.Dpolyglot.engine.TraceCompilation=true -cp  mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.TestMain
```

## Running Espresso cross-versions

By default, Espresso runs within a GraalVM and it reuses the jars and native libraries shipped with GraalVM. But it's possible to specify a different Java home, even with a different version; Espresso will automatically switch versions regardless of the host JVM.
```bash
$ mx build
$ mx espresso -version
$ mx espresso --java.JavaHome=/path/to/java/8/home -version
$ mx espresso --java.JavaHome=/path/to/java/11/home -version
```

## Limitations

Espresso relies on glibc's [dlmopen](https://man7.org/linux/man-pages/man3/dlopen.3.html) to run on HotSpot, but this approach has limitations that lead to crashes e.g. `libnio.so: undefined symbol: fstatat64` . Some of these limitations can be by avoided by defining `LD_DEBUG=unused` e.g.

```bash
$ LD_DEBUG=unused mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

## _Espressoⁿ_ Java-ception

Espresso can run itself. **Self-hosting requires a Linux distribution with an up-to-date glibc.**

Use `mx espresso-meta` to run programs on Espresso². Ensure to prepend `LD_DEBUG=unused` to overcome a known **glibc** bug.

To run HelloWorld on Espresso² execute the following:

```bash
$ mx build
$ LD_DEBUG=unused mx --dy /compiler espresso-meta -cp my.jar HelloWorld
```

It takes some time for both (nested) VMs to boot, only the base layer is blessed with JIT compilation. Enjoy!
