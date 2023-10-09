---
layout: docs
toc_group: build-overview
link_title: Build Output
permalink: /reference-manual/native-image/overview/BuildOutput/
redirect_from: /reference-manual/native-image/BuildOutput/
---

# Native Image Build Output

* [Build Stages](#build-stages)
* [Resource Usage Statistics](#resource-usage-statistics)
* [Machine-Readable Build Output](#machine-readable-build-output)

Here you will find information about the build output of GraalVM Native Image.
Below is the example output when building a native executable of the `HelloWorld` class:

```shell
================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
================================================================================
[1/8] Initializing...                                            (2.8s @ 0.15GB)
 Java version: 20+34, vendor version: GraalVM CE 20-dev+34.1
 Graal compiler: optimization level: 2, target machine: x86-64-v3
 C compiler: gcc (linux, x86_64, 12.2.0)
 Garbage collector: Serial GC (max heap size: 80% of RAM)
--------------------------------------------------------------------------------
 Build resources:
 - 13.24GB of memory (42.7% of 31.00GB system memory, determined at start)
 - 16 thread(s) (100.0% of 16 available processor(s), determined at start)
[2/8] Performing analysis...  [****]                             (4.5s @ 0.54GB)
    3,163 reachable types   (72.5% of    4,364 total)
    3,801 reachable fields  (50.3% of    7,553 total)
   15,183 reachable methods (45.5% of   33,405 total)
      957 types,    81 fields, and   480 methods registered for reflection
       57 types,    55 fields, and    52 methods registered for JNI access
        4 native libraries: dl, pthread, rt, z
[3/8] Building universe...                                       (0.8s @ 0.99GB)
[4/8] Parsing methods...      [*]                                (0.6s @ 0.75GB)
[5/8] Inlining methods...     [***]                              (0.3s @ 0.32GB)
[6/8] Compiling methods...    [**]                               (3.7s @ 0.60GB)
[7/8] Laying out methods...   [*]                                (0.8s @ 0.83GB)
[8/8] Creating image...       [**]                               (3.1s @ 0.58GB)
   5.32MB (24.22%) for code area:     8,702 compilation units
   7.03MB (32.02%) for image heap:   93,301 objects and 5 resources
   8.96MB (40.83%) for debug info generated in 1.0s
 659.13kB ( 2.93%) for other data
  21.96MB in total
--------------------------------------------------------------------------------
Top 10 origins of code area:            Top 10 object types in image heap:
   4.03MB java.base                        1.14MB byte[] for code metadata
 927.05kB svm.jar (Native Image)         927.31kB java.lang.String
 111.71kB java.logging                   839.68kB byte[] for general heap data
  63.38kB org.graalvm.nativeimage.base   736.91kB java.lang.Class
  47.59kB jdk.proxy1                     713.13kB byte[] for java.lang.String
  35.85kB jdk.proxy3                     272.85kB c.o.s.c.h.DynamicHubCompanion
  27.06kB jdk.internal.vm.ci             250.83kB java.util.HashMap$Node
  23.44kB org.graalvm.sdk                196.52kB java.lang.Object[]
  11.42kB jdk.proxy2                     182.77kB java.lang.String[]
   8.07kB jdk.internal.vm.compiler       154.26kB byte[] for embedded resources
   1.39kB for 2 more packages              1.38MB for 884 more object types
--------------------------------------------------------------------------------
Recommendations:
 HEAP: Set max heap for improved and more predictable memory usage.
 CPU:  Enable more CPU features with '-march=native' for improved performance.
--------------------------------------------------------------------------------
    0.8s (4.6% of total time) in 35 GCs | Peak RSS: 1.93GB | CPU load: 9.61
--------------------------------------------------------------------------------
Produced artifacts:
 /home/janedoe/helloworld/helloworld (executable)
 /home/janedoe/helloworld/helloworld.debug (debug_info)
 /home/janedoe/helloworld/sources (debug_info)
================================================================================
Finished generating 'helloworld' in 17.0s.
```

## Build Stages

### <a name="stage-initializing"></a>Initializing
In this stage, the Native Image build process is set up and [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) are initialized.

#### <a name="glossary-imagekind"></a>Native Image Kind
By default, Native Image generates *executables* but it can also generate [*native shared libraries*](InteropWithNativeCode.md) and [*static executables*](guides/build-static-and-mostly-static-executable.md).

#### <a name="glossary-java-info"></a>Java Version Info
The Java and vendor version of the Native Image process.
Both are also used for the `java.vm.version` and `java.vendor.version` properties within the generated native binary.
Please report version and vendor when you [file issues](https://github.com/oracle/graal/issues/new).

#### <a name="glossary-graal-compiler"></a>Graal Compiler
The selected optimization level and targeted machine type used by the Graal compiler.
The optimization level can be controlled with the `-O` option and defaults to `2`, which enables aggressive optimizations.
Use `-Ob` to enable quick build mode, which speeds up the [compilation stage](#stage-compiling).
This is useful during development, or when peak throughput is less important and you would like to optimize for size.
The targeted machine type can be selected with the `-march` option and defaults to `x86-64-v3` on AMD64 and `armv8-a` on AArch64.
See [here](#recommendation-cpu) for recommendations on how to use this option.

On Oracle GraalVM, the line also shows information about [Profile-Guided Optimizations (PGO)](#recommendation-pgo).
- `off`: PGO is not used
- `instrument`: The generated executable or shared library is instrumented to collect data for PGO (`--pgo-instrument`)
- `user-provided`: PGO is enabled and uses a user-provided profile (for example `--pgo default.iprof`)
- `ML-inferred`: A machine learning (ML) model is used to infer profiles for control split branches statically.

#### <a name="glossary-ccompiler"></a>C Compiler
The C compiler executable, vendor, target architecture, and version info used by the Native Image build process.

#### <a name="glossary-gc"></a>Garbage Collector
The garbage collector used within the generated executable:
- The *Serial GC* is the default GC and optimized for low memory footprint and small Java heap sizes.
- The *G1 GC* (not available in GraalVM Community Edition) is a multi-threaded GC that is optimized to reduce stop-the-world pauses and therefore improve latency while achieving high throughput.
- The *Epsilon GC* does not perform any garbage collection and is designed for very short-running applications that only allocate a small amount of memory.

For more information see the [docs on Memory Management](MemoryManagement.md).

#### <a name="glossary-gc-max-heap-size"></a>Maximum Heap Size
By default, the heap size is limited to a certain percentage of your system memory, allowing the garbage collector to freely allocate memory according to its policy.
Use the `-Xmx` option when invoking your native executable (for example `./myapp -Xmx64m` for 64MB) to limit the maximum heap size for a lower and more predictable memory footprint.
This can also improve latency in some cases.
Use the `-R:MaxHeapSize` option when building with Native Image to pre-configure the maximum heap size.

#### <a name="glossary-user-specific-features"></a>User-Specific Features
All [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) that are either provided or specifically enabled by the user, or implicitly registered for the user, for example, by a framework.
GraalVM Native Image deploys a number of internal features, which are excluded from this list.

#### <a name="glossary-experimental-options"></a>Experimental Options
A list of all active experimental options, including their origin and possible API option alternatives if available.

Using experimental options should be avoided in production and can change in any release.
If you rely on experimental features and would like an option to be considered stable, please file an issue.

#### <a name="glossary-build-resources"></a>Build Resources
The memory limit and number of threads used by the build process.

More precisely, the memory limit of the Java heap, so actual memory consumption can be even higher.
Please check the [peak RSS](#glossary-peak-rss) reported at the end of the build to understand how much memory was actually used.
By default, the build process tries to only use free memory (to avoid memory pressure on the build machine), and never more than 32GB of memory.
If less than 8GB of memory are free, the build process falls back to use 85% of total memory.
Therefore, consider freeing up memory if your machine is slow during a build, for example, by closing applications that you do not need.

By default, the build process uses all available CPU cores to maximize speed.
Use the `--parallelism` option to set the number of threads explicitly (for example, `--parallelism=4`).
Use fewer threads to reduce load on your system as well as memory consumption (at the cost of a slower build process).

### <a name="stage-analysis"></a>Performing Analysis
In this stage, a [points-to analysis](https://dl.acm.org/doi/10.1145/3360610) is performed.
The progress indicator visualizes the number of analysis iterations.
A large number of iterations can indicate problems in the analysis likely caused by misconfiguration or a misbehaving feature.

#### <a name="glossary-reachability"></a>Reachable Types, Fields, and Methods
The number of types (primitives, classes, interfaces, and arrays), fields, and methods that are reachable versus the total number of types, fields, and methods loaded as part of the build process.
A significantly larger number of loaded elements that are not reachable can indicate a configuration problem.
To reduce overhead, please ensure that your class path and module path only contain entries that are needed for building the application.

#### <a name="glossary-reflection-registrations"></a>Reflection Registrations
The number of types, fields, and methods that are registered for reflection.
Large numbers can cause significant reflection overheads, slow down the build process, and increase the size of the native binary (see [reflection metadata](#glossary-reflection-metadata)).

#### <a name="glossary-jni-access-registrations"></a>JNI Access Registrations
The number of types, fields, and methods that are registered for [JNI](JNI.md) access.

#### <a name="glossary-foreign-downcall-registrations"></a>Foreign functions stubs
The number of downcalls registered for [foreign](ForeignInterface.md) function access.

#### <a name="glossary-runtime-methods"></a>Runtime Compiled Methods
The number of methods marked for runtime compilation.
This number is only shown if runtime compilation is built into the executable, for example, when building a [Truffle](https://github.com/oracle/graal/tree/master/truffle) language.
Runtime-compiled methods account for [graph encodings](#glossary-graph-encodings) in the heap.

### <a name="stage-universe"></a>Building Universe
In this stage, a universe with all types, fields, and methods is built, which is then used to create the native binary.

### <a name="stage-parsing"></a>Parsing Methods
In this stage, the Graal compiler parses all reachable methods.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-inlining"></a>Inlining Methods
In this stage, trivial method inlining is performed.
The progress indicator visualizes the number of inlining iterations.

### <a name="stage-compiling"></a>Compiling Methods
In this stage, the Graal compiler compiles all reachable methods to machine code.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-layouting"></a>Layouting Methods
In this stage, compiled methods are layouted.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-creating"></a>Creating Image
In this stage, the native binary is created and written to disk.
Debug info is also generated as part of this stage (if requested).

#### <a name="glossary-code-area"></a>Code Area
The code area contains machine code produced by the Graal compiler for all reachable methods.
Therefore, reducing the number of [reachable methods](#glossary-reachability) also reduces the size of the code area.

##### <a name="glossary-code-area-origins"></a>Origins of Code Area
To help users understand where the machine code of the code area comes from, the build output shows a breakdown of the top origins.
An origin is a group of Java sources and can be a JAR file, a package name, or a class name, depending on the information available.
The [`java.base` module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/module-summary.html), for example, contains base classes from the JDK.
The `svm.jar` file, the `org.graalvm.nativeimage.base` module, and similar origins contain internal sources for the Native Image runtime.
To reduce the size of the code area and with that, the total size of the native executable, re-evaluate the dependencies of your application based on the code area breakdown.
Some libraries and frameworks are better prepared for Native Image than others, and newer versions of a library or framework may improve (or worsen) their code footprint. 

#### <a name="glossary-image-heap"></a>Image Heap
The heap contains reachable objects such as static application data, metadata, and `byte[]` for different purposes (see below).

##### <a name="glossary-general-heap-data"></a>General Heap Data Stored in `byte[]`
The total size of all `byte[]` objects that are neither used for `java.lang.String`, nor [code metadata](#glossary-code-metadata), nor [reflection metadata](#glossary-reflection-metadata), nor [graph encodings](#glossary-graph-encodings).
Therefore, this can also include `byte[]` objects from application code.

##### <a name="glossary-embedded-resources"></a>Embedded Resources Stored in `byte[]`
The total size of all `byte[]` objects used for storing resources (for example, files accessed via `Class.getResource()`) within the native binary.
The number of resources is shown in the [Heap](#glossary-image-heap) section.

##### <a name="glossary-code-metadata"></a>Code Metadata Stored in `byte[]`
The total size of all `byte[]` objects used for metadata for the [code area](#glossary-code-area).
Therefore, reducing the number of [reachable methods](#glossary-reachability) also reduces the size of this metadata.

##### <a name="glossary-reflection-metadata"></a>Reflection Metadata Stored in `byte[]`
The total size of all `byte[]` objects used for reflection metadata, including types, field, method, and constructor data.
To reduce the amount of reflection metadata, reduce the number of [elements registered for reflection](#glossary-reflection-registrations).

##### <a name="glossary-graph-encodings"></a>Graph Encodings Stored in `byte[]`
The total size of all `byte[]` objects used for graph encodings.
These encodings are a result of [runtime compiled methods](#glossary-runtime-methods).
Therefore, reducing the number of such methods also reduces the size of corresponding graph encodings.

##### <a name="glossary-heap-alignment"></a>Heap Alignment
Additional space reserved to align the heap for the [selected garbage collector](#glossary-gc).
The heap alignment may also contain GC-specific data structures.
Its size can therefore only be influenced by switching to a different garbage collector.

#### <a name="glossary-debug-info"></a>Debug Info
The total size of generated debug information (if enabled).

#### <a name="glossary-other-data"></a>Other Data
The amount of data in the binary that is neither in the [code area](#glossary-code-area), nor in the [heap](#glossary-image-heap), nor [debug info](#glossary-debug-info).
This data typically contains internal information for Native Image and should not be dominating.

## Security Report

*This section is not available in GraalVM Community Edition.*

#### <a name="glossary-deserialization"></a>Deserialization
This shows whether Java deserialization is included in the native executable or not.
If not included, the attack surface of the executable is reduced as the executable cannot be exploited with attacks based on Java deserialization.

#### <a name="glossary-embedded-sbom"></a>Embedded SBOM
Number of components and the size of the embedded Software Bill of Materials (SBOM).
Use `--enable-sbom` to include an SBOM in the native executable.
For more information, see [Inspection Tool](InspectTool.md)

#### <a name="glossary-backwards-edge-cfi"></a>Backwards-Edge Control-Flow Integrity (CFI)
Control-Flow Integrity (CFI) can be enforced with the experimental `-H:+EnableCFI` option.
This feature is currently only available for Linux AArch64 and leverages pointer authentication codes (PAC) to ensure integrity of a function's return address.

## Recommendations

The build output may contain one or more of the following recommendations that help you get the best out of Native Image.

#### <a name="recommendation-init"></a>`INIT`: Use the Strict Image Heap Configuration

Start using `--strict-image-heap` to reduce the amount of configuration and prepare for future GraalVM releases where this will be the default.
This mode requires only the classes that are stored in the image heap to be marked with `--initialize-at-build-time`. 
This effectively reduces the number of configuration entries necessary to achieve build-time initialization. 
When adopting the new mode it is best to start introducing build-time initialization from scratch.
During this process, it is best to select individual classes (as opposed to whole packages) for build time initialization.
Also, before migrating to the new flag make sure to update all framework dependencies to the latest versions as they might need to migrate too. 

#### <a name="recommendation-awt"></a>`AWT`: Missing Reachability Metadata for Abstract Window Toolkit

The Native Image analysis has included classes from the [`java.awt` package](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/package-summary.html) but could not find any reachability metadata for it.
Use the [tracing agent](AutomaticMetadataCollection.md) to collect such metadata for your application.
Otherwise, your application is unlikely to work properly.
If your application is not a desktop application (for example using Swing or AWT directly), you may want to re-evaluate whether the dependency on AWT is actually needed.

#### <a name="recommendation-cpu"></a>`CPU`: Enable More CPU Features for Improved Performance

The Native Image build process has determined that your CPU supports more features, such as AES or LSE, than currently enabled.
If you deploy your application on the same machine or a similar machine with support for the same CPU features, consider using `-march=native` at build time.
This option allows the Graal compiler to use all CPU features available, which in turn can significantly improve the performance of your application.
Use `-march=list` to list all available machine types that can be targeted explicitly.

#### <a name="recommendation-g1gc"></a>`G1GC`: Use G1 Garbage Collector for Improved Latency and Throughput

The G1 garbage collector is available for your platform.
Consider enabling it using `--gc=G1` at build time to improve the latency and throughput of your application.
For more information see the [docs on Memory Management](MemoryManagement.md).
For best peak performance, also consider using [Profile-Guided Optimizations](#recommendation-pgo).

#### <a name="recommendation-heap"></a>`HEAP`: Specify a Maximum Heap Size

Please refer to [Maximum Heap Size](#glossary-gc-max-heap-size).

#### <a name="recommendation-pgo"></a>`PGO`: Use Profile-Guided Optimizations for Improved Throughput

Consider using Profile-Guided Optimizations to optimize your application for improved throughput.
These optimizations allow the Graal compiler to leverage profiling information, similar to when it is running as a JIT compiler, when AOT-compiling your application.
For this, perform the following steps:

1. Build your application with `--pgo-instrument`.
2. Run your instrumented application with a representative workload to generate profiling information in the form of an `.iprof` file.
3. Re-build your application and pass in the profiling information with `--pgo=<your>.iprof` to generate an optimized version of your application.

Relevant guide: [Optimize a Native Executable with Profile-Guided Optimizations](guides/optimize-native-executable-with-pgo.md).

For best peak performance, also consider using the [G1 garbage collector](#recommendation-g1gc).

#### <a name="recommendation-qbm"></a>`QBM`: Use Quick Build Mode for Faster Builds

Consider using the quick build mode (`-Ob`) to speed up your builds during development.
More precisely, this mode reduces the number of optimizations performed by the Graal compiler and thus reduces the overall time of the [compilation stage](#stage-compiling).
The quick build mode is not only useful for development, it can also cause the generated executable file to be smaller in size.
Note, however, that the overall peak throughput of the executable may be lower due to the reduced number of optimizations.

## Resource Usage Statistics

#### <a name="glossary-garbage-collection"></a>Garbage Collections
The total time spent in all garbage collectors, total GC time divided by the total process time as a percentage, and the total number of garbage collections.
A large number of collections or time spent in collectors usually indicates that the system is under memory pressure.
Increase the amount of available memory to reduce the time to build the native binary.

#### <a name="glossary-peak-rss"></a>Peak RSS
Peak [resident set size](https://en.wikipedia.org/wiki/Resident_set_size) as reported by the operating system.
This value indicates the maximum amount of memory consumed by the build process.
You may want to compare this value to the memory limit reported in the [build resources section](#glossary-build-resources).
If there is enough headroom and the [GC statistics](#glossary-garbage-collection) do not show any problems, the amount of total memory of the system can be reduced to a value closer to the peak RSS to lower operational costs.

#### <a name="glossary-cpu-load"></a>CPU load
The CPU time used by the process divided by the total process time.
Increase the number of CPU cores to reduce the time to build the native binary.

## Machine-Readable Build Output

The build output produced by the `native-image` builder is designed for humans, can evolve with new releases, and should thus not be parsed in any way by tools.
Instead, use the `-H:BuildOutputJSONFile=<file.json>` option to instruct the builder to produce machine-readable build output in JSON format that can be used, for example, for building monitoring tools.
The JSON files validate against the JSON schema defined in [`build-output-schema-v0.9.2.json`](https://github.com/oracle/graal/tree/master/docs/reference-manual/native-image/assets/build-output-schema-v0.9.2.json).
Note that a JSON file is produced if and only if a build succeeds.

The following example illustrates how this could be used in a CI/CD build pipeline to check that the number of reachable methods does not exceed a certain threshold:

```bash
native-image -H:BuildOutputJSONFile=build.json HelloWorld
# ...
cat build.json | python3 -c "import json,sys;c = json.load(sys.stdin)['analysis_results']['methods']['reachable']; assert c < 12000, f'Too many reachable methods: {c}'"
Traceback (most recent call last):
  File "<string>", line 1, in <module>
AssertionError: Too many reachable methods: 12128
```

## Colorful Build Output

By default, the `native-image` builder colors the build output for better readability when it finds an appropriate terminal.
It also honors the <a href="https://no-color.org" target="_target">`NO_COLOR`</a>, `CI`, and `TERM` environment variables when checking for color support.
To explicitly control colorful output, set the `--color` option to `always`, `never`, or `auto` (default).

## Related Documentation

- [Build a Native Shared Library](guides/build-native-shared-library.md)
- [Build a Statically Linked or Mostly-Statically Linked Native Executable](guides/build-static-and-mostly-static-executable.md)
- [Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html)
- [Interoperability with Native Code](InteropWithNativeCode.md)
- [Java Native Interface (JNI) in Native Image](JNI.md)
- [Memory Management](MemoryManagement.md)
- [Native Image Build Overview](BuildOverview.md)
- [Native Image Build Configuration](BuildConfiguration.md)
