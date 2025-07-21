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

You can now depend on the jars using a version like `24.2.1-SNAPSHOT`.

For embedding, there is a jar containing all the necessary JDK resources (classes, libraries, config files, etc.).
This jar is made available to espresso through the truffle [resource API](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.Env.html#getInternalResource(java.lang.String)).
It is the mx distribution called `ESPRESSO_RUNTIME_RESOURCES` and is published on maven as `org.graalvm.espresso:espresso-runtime-resources-$RuntimeResourceId` where `$RuntimeResourceId` identifies the type and version of the included JDK.
For example `org.graalvm.espresso:espresso-runtime-resources-jdk21` contain Oracle JDK 21.
`ESPRESSO_RUNTIME_RESOURCES` contains the JDK specified through `ESPRESSO_JAVA_HOME` as well as the optional llvm bits specified in `ESPRESSO_LLVM_JAVA_HOME`.

Since we might want to distribute these resources for multiple JDK version, it is possible to produce additional runtime resource jars.
This is done by setting `EXTRA_ESPRESSO_JAVA_HOMES` and optionally `EXTRA_ESPRESSO_LLVM_JAVA_HOMES`.
Those are lists of java homes separated by a path separator.
If `EXTRA_ESPRESSO_LLVM_JAVA_HOMES` is specified it should contain the same number of entries and in the same order as `EXTRA_ESPRESSO_JAVA_HOMES`.
The JDKs set in `ESPRESSO_JAVA_HOME` and `EXTRA_ESPRESSO_JAVA_HOMES` should all have different versions.

For example to produce jdk21 and jk25 resource in addition to the version of `ESPRESSO_JAVA_HOME`:
```bash
$ export ESPRESSO_JAVA_HOME=/path/to/jdk26
$ export ESPRESSO_LLVM_JAVA_HOME=/path/to/jdk26-llvm
$ export EXTRA_ESPRESSO_JAVA_HOMES=/path/to/jdk21:/path/to/jdk25
$ export EXTRA_ESPRESSO_LLVM_JAVA_HOMES=/path/to/jdk21-llvm:/path/to/jdk25-llvm
# subsequent build and maven-deploy operation will now publish
# * org.graalvm.espresso:espresso-runtime-resources-jdk21
# * org.graalvm.espresso:espresso-runtime-resources-jdk25
# * org.graalvm.espresso:espresso-runtime-resources-jdk26
```

The `org.graalvm.espresso:java` maven dependency automatically depends on the "main" runtime resource (the one from `ESPRESSO_JAVA_HOME`).
In order to use a different version in an embedding, an explicit dependency to `org.graalvm.espresso:espresso-runtime-resources-$RuntimeResourceId` should be added.
The context should also be created with `java.RuntimeResourceId` set to the desired version (e.g., `"jdk21"`).

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

### Linux

Espresso relies on glibc's [dlmopen](https://man7.org/linux/man-pages/man3/dlopen.3.html) to run on HotSpot, but this approach has limitations that lead to crashes e.g. `libnio.so: undefined symbol: fstatat64` . Some of these limitations can be by avoided by defining `LD_DEBUG=unused` e.g.

```bash
$ LD_DEBUG=unused mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

### macOS

On macOS there is nothing like `dlmopen` available, therefore `jvm-ce` does not work. However, there is another mode available where libraries can be executed via Sulong (internally called `nfi-llvm`). This requires the OpenJDK libraries to be compiled with the Sulong toolchain, such builds are available through `mx fetch-jdk` with a `-llvm` suffix. Unfortunately this mode is only supported on `darwin-amd64`, so on an Apple Silicon machine the `x86_64` emulator Rosetta 2 must be used:

```bash
$ arch -arch x86_64 zsh

$ export MX_PYTHON=`xcode-select -p`/usr/bin/python3
$ file $MX_PTYHON
/Applications/Xcode16.2.app/Contents/Developer/usr/bin/python3: Mach-O universal binary with 2 architectures: [x86_64:Mach-O 64-bit executable x86_64] [arm64:Mach-O 64-bit executable arm64]
/Applications/Xcode16.2.app/Contents/Developer/usr/bin/python3 (for architecture x86_64):	Mach-O 64-bit executable x86_64
/Applications/Xcode16.2.app/Contents/Developer/usr/bin/python3 (for architecture arm64):	Mach-O 64-bit executable arm64

$ # the important part above is that there is also a Mach-O included for x86_64

$ cd $graal/espresso
$ mx fetch-jdk # fetch JDK latest, 21 and 21-llvm

$ export ESPRESSO_JAVA_HOME=<JDK21 path for amd64>
$ export LLVM_JAVA_HOME=<JDK21-llvm path for amd64>
$ export JAVA_HOME=<JDK-latest path for amd64>

$ # Note: ESPRESSO_JAVA_HOME and LLVM_JAVA_HOME must match regarding version.

$ mx --env jvm-ce-llvm build

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
