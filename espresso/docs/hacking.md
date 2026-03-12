# How to work on Espresso

## Setup

### Using a pre-built GraalVM

The simplest way to build espresso is to use a pre-built GraalVM.

Download and unpack the latest GraalVM [release](https://www.graalvm.org/downloads/) or [ea build](https://github.com/graalvm/oracle-graalvm-ea-builds/releases).

Set the `JAVA_HOME` environment variable to the extracted result. This will be used during the build and as a host JDK.

Espresso is only a JVM and it needs a JDK to use as a guest. Set the `ESPRESSO_JAVA_HOME` to a JDK to be used for the guest.

### Building from the graal repository

It is also possible to build Espresso directly from the current Graal repository.

First you need a (JVMCI-enabled) JDK. If you don't already have one you can use e.g. `mx fetch-jdk` to download one.

Secondly, set your `JAVA_HOME` to the (JVMCI-enabled) JDK e.g. via `mx --java-home /path/to/java/home ...` or `export JAVA_HOME=/path/to/java/home`. 

Lastly, set the `ESPRESSO_JAVA_HOME` to a JDK to be used for the guest. By default, `JAVA_HOME` will be used.

## Building and Running Espresso

Espresso can be built in several configurations (Native, JVM, or Embedded). (You can find all configurations in `graal/espresso/mx.espresso`)
For a quick start you can find a short list below:

```bash
$ mx --env jvm build       # GraalVM CE + Espresso jars (interpreter only)
$ mx --env jvm-ce build    # GraalVM CE + Espresso jars (JIT)
$ mx --env native-ce build # GraalVM CE + Espresso native (JIT)
```
`mx build`-ing Espresso creates "espresso standalones" which are JDK-like directories.

Now you can use the `mx espresso` command, which mimics the `java` command. Under the hood it runs Espresso from a jvm or native standalone and prefers native if available.

```bash
$ mx --env jvm-ce build # Always build first
$ mx --env jvm-ce espresso -cp my.jar HelloWorld # Always use the same --env argument
```
Note this would run espresso as a jvm standalone as the native-standalone was not built! Use `native-ce` when building and running Espresso for using the native standalone.

### Tips

#### Building a Specific Standalone

If you are only trying to build a specific standalone it's possible to specify it while building:

```bash
$ mx --env native-ce build --targets=ESPRESSO_NATIVE_STANDALONE
```


#### Locating Build Artifacts
You can find out where the espresso standalones are by running `mx path --output ...`:
```bash
$ mx path --output ESPRESSO_NATIVE_STANDALONE
$ mx path --output ESPRESSO_JVM_STANDALONE
```
> Note: If you used options like `--env ...` or `--dynamicimports ...` while building, you should also use them with `mx path`: e.g., `mx --env native-ce path ...`.

#### Direct Execution (Zero-Overhead)
The `mx espresso` launcher adds some overhead, to execute Espresso native image directly use:

```bash
$ mx --env native-ce build # Always build first
$ export ESPRESSO=`mx --quiet --no-warning --env native-ce path --output ESPRESSO_NATIVE_STANDALONE`/bin/java
$ time $ESPRESSO -cp my.jar HelloWorld
```
#### Inspecting mx

Use the `-v` (verbose) flag with any mx command to see exactly what is happening under the hood. This is the best way to extract the raw underlying commands (like the final java or native-image call) for manual debugging.

### Other ways to launch Espresso

Besides the auto-selecting `mx espresso` there are more controlled ways to launch espresso:

#### Native standalone
`mx java-truffle` explicitly launches the Espresso native standalone through the standard java launcher.

```bash
$ mx --env native-ce build # Always build first
$ mx --env native-ce java-truffle -cp my.jar HelloWorld # Always use the same --env argument
```

For more information [see](how-espresso-works.md).

#### JVM standalone

To explicitly run espresso from a jvm standalone use `mx espresso-launcher`

```bash
$ mx --env jvm-ce build # Always build first
$ mx --env jvm-ce espresso-launcher -cp my.jar HelloWorld # Always use the same --env argument
```


#### Embedded on a vanilla JDK

`mx espresso-embedded` allows you to run Espresso on a vanilla JDK (not within a standalone and not part of GraalVM). The launcher adds all jars and properties required to run Espresso on any vanilla JDK.

Please note some truffle-level tooling such as the cpu-sampler is not easily available. To use it launch espresso as a standalone or add the required dependencies manually.

```bash
$ mx --env jvm-ce build # Always build first
$ mx --env jvm-ce espresso-embedded -cp my.jar HelloWorld # Always use the same --env argument
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

## Debug Espresso

To debug Espresso use `-d` on `espresso-embedded`

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

## No-Native Espresso

To run Espresso without native access, use the experimental option `java.NativeBackend=no-native` via the command line:
```bash
$ mx espresso --experimental-options --java.NativeBackend=no-native 
```

or on the context builder: 
```java
builder.allowExperimentalOptions(true).option("java.NativeBackend", "no-native") 
```

Disabling native access enhances security guarantees and sandboxing capabilities. In this mode, substitutions are used for Java's standard libraries, and virtualized memory is provided. However, some functionality might be limited (e.g. you will have no access to LibAWT).

## Limitations

### Linux

Espresso relies on glibc's [dlmopen](https://man7.org/linux/man-pages/man3/dlopen.3.html) to run on HotSpot, but this approach has limitations that lead to crashes e.g. `libnio.so: undefined symbol: fstatat64` . Some of these limitations can be by avoided by defining `LD_DEBUG=unused` e.g.

```bash
$ LD_DEBUG=unused mx espresso -cp mxbuild/dists/jdk1.8/espresso-playground.jar com.oracle.truffle.espresso.playground.Tetris
```

### macOS

Nothing like `dlmopen` is available on macOS. Instead we default to `nfi-staticlib` for the JVM mode, which statically links in (most) of the OpenJDK libraries into `libjvm.dylib`.
A notable exception is `libawt.dylib` and its related libraries, which only work via dynamic loading.
This means in practice only the host _or_ the guest can use AWT, but not both at the same time.


Currently `nfi-staticlib` only works for one Espresso context. Using `nfi-staticlib` with more than one context will likely result in a SIGSEGV in `libtrufflenfi.dylib`.
We are exploring how to implement support for multiple contexts (GR-71082).

## _Espressoⁿ_ Java-ception

Espresso can run itself. **Self-hosting requires a Linux distribution with an up-to-date glibc.**

Use `mx espresso-meta` to run programs on Espresso². Ensure to prepend `LD_DEBUG=unused` to overcome a known **glibc** bug.

To run HelloWorld on Espresso² execute the following:

```bash
$ mx --dy / compiler build # enable JIT for the base layer
$ LD_DEBUG=unused mx --dy /compiler espresso-meta -cp my.jar HelloWorld
```

You can pass flags to both the base Espresso VM (which runs another Espresso) and the inner VM (which runs your guest
program).

```bash
$ mx espresso-meta [base VM flags] -- [inner VM flags and program args]
```

It takes some time for both (nested) VMs to boot, only the base layer is blessed with JIT compilation. Enjoy!
