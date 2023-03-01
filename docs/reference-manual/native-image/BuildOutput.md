---
layout: ni-docs
toc_group: build-overview
link_title: Build Output
permalink: /reference-manual/native-image/overview/BuildOutput/
redirect_from: /$version/reference-manual/native-image/BuildOutput/
---

# Native Image Build Output

* [Build Stages](#build-stages)
* [Resource Usage Statistics](#resource-usage-statistics)
* [Machine-Readable Build Output](#machine-readable-build-output)
* [Build Output Options](#build-output-options)

Here you will find information about the build output of GraalVM Native Image.
Below is the example output when building a native executable of the `HelloWorld` class:

```shell
================================================================================
GraalVM Native Image: Generating 'helloworld' (executable)...
================================================================================
[1/8] Initializing...                                            (3.3s @ 0.25GB)
 Version info: 'GraalVM dev Java 19+36-jvmci-23.0-b01 CE'
 Java version info: '19+36-jvmci-23.0-b01'
 C compiler: gcc (linux, x86_64, 11.3.0)
 Garbage collector: Serial GC (max heap size: unlimited)
[2/8] Performing analysis...  [****]                             (6.2s @ 0.47GB)
   2,880 (71.50%) of  4,028 types reachable
   3,519 (51.06%) of  6,892 fields reachable
  13,339 (45.11%) of 29,570 methods reachable
     879 types,     0 fields, and   356 methods registered for reflection
      57 types,    56 fields, and    52 methods registered for JNI access
       4 native libraries: dl, pthread, rt, z
[3/8] Building universe...                                       (1.1s @ 2.26GB)
[4/8] Parsing methods...      [*]                                (1.0s @ 2.76GB)
[5/8] Inlining methods...     [***]                              (0.8s @ 0.99GB)
[6/8] Compiling methods...    [***]                              (6.4s @ 4.86GB)
[7/8] Layouting methods...    [**]                               (4.2s @ 3.98GB)
[8/8] Creating image...       [*]                                (4.0s @ 2.04GB)
   4.52MB (22.97%) for code area:     7,470 compilation units
   7.06MB (35.87%) for image heap:  101,764 objects and 5 resources
   7.52MB (38.24%) for debug info generated in 1.8s
 590.19KB ( 2.93%) for other data
  19.68MB in total
--------------------------------------------------------------------------------
Top 10 origins of code area:            Top 10 object types in image heap:
   3.43MB java.base                        1.01MB byte[] for code metadata
 760.98KB svm.jar (Native Image)        1000.72KB java.lang.String
 102.06KB java.logging                   884.18KB byte[] for general heap data
  48.03KB org.graalvm.nativeimage.base   686.91KB byte[] for java.lang.String
  40.49KB jdk.proxy1                     659.87KB java.lang.Class
  38.23KB jdk.proxy3                     247.50KB c.o.s.c.h.DynamicHubCompanion
  25.73KB jdk.internal.vm.ci             239.25KB java.lang.Object[]
  23.55KB org.graalvm.sdk                226.08KB java.util.HashMap$Node
  11.10KB jdk.proxy2                     173.15KB java.lang.String[]
   8.10KB jdk.internal.vm.compiler       163.22KB j.u.c.ConcurrentHashMap$Node
   1.39KB for 2 more origins               1.70MB for 808 more object types
--------------------------------------------------------------------------------
    0.5s (1.8% of total time) in 24 GCs | Peak RSS: 5.62GB | CPU load: 8.92
--------------------------------------------------------------------------------
Produced artifacts:
 /home/janedoe/helloworld/helloworld (executable, debug_info)
 /home/janedoe/helloworld/sources (debug_info)
================================================================================
Finished generating 'helloworld' in 27.4s.
```

## Build Stages

### <a name="stage-initializing"></a>Initializing
In this stage, the Native Image build process is set up and [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) are initialized.

#### <a name="glossary-imagekind"></a>Native Image Kind
By default, Native Image generates *executables* but it can also generate [*native shared libraries*](InteropWithNativeCode.md) and [*static executables*](guides/build-static-and-mostly-static-executable.md).

#### <a name="glossary-version-info"></a>Version Info
The version info of the Native Image process.
This string is also used for the `java.vm.version` property within the generated native binary.
Please report this version info when you [file issues](https://github.com/oracle/graal/issues/new).

#### <a name="glossary-java-version-info"></a>Java Version Info
The Java version info (`java.runtime.version` property) of the Native Image build process.
Please report this version info when you [file issues](https://github.com/oracle/graal/issues/new).

#### <a name="glossary-ccompiler"></a>C Compiler
The C compiler executable, vendor, target architecture, and version info used by the Native Image build process.

#### <a name="glossary-gc"></a>Garbage Collector
The garbage collector used within the generated executable:
- The *Serial GC* is the default GC and optimized for low memory footprint and small Java heap sizes.
- The *G1 GC* (only available with GraalVM Enterprise Edition) is a multi-threaded GC that is optimized to reduce stop-the-world pauses and therefore improve latency while achieving high throughput.
- The *Epsilon GC* does not perform any garbage collection and is designed for very short-running applications that only allocate a small amount of memory.

For more information see the [docs on Memory Management at Image Run Time](MemoryManagement.md).

#### <a name="glossary-gc-max-heap-size"></a>Maximum Heap Size
By default, the heap size is *unlimited*, allowing the garbage collector to freely allocate memory according to its policy.
Use the `-Xmx` option when invoking your native executable (for example `./myapp -Xmx64m` for 64MB) to limit the maximum heap size for a lower and more predictable memory footprint.
This can also improve latency in some cases.
Use the `-R:MaxHeapSize` option when building with Native Image to pre-configure the maximum heap size.

#### <a name="glossary-user-specific-features"></a>User-Specific Features
All [`Features`](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html) that are either provided or specifically enabled by the user, or implicitly registered for the user, for example, by a framework.
GraalVM Native Image deploys a number of internal features, which are excluded from this list.

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
The total size of all `byte[]` objects used for storing resources (for example, files accessed via `Class.getResource()`) within the native binary. The number of resources is shown in the [Heap](#glossary-image-heap) section.

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

#### <a name="glossary-debug-info"></a>Debug Info
The total size of generated debug information (if enabled).

#### <a name="glossary-other-data"></a>Other Data
The amount of data in the binary that is neither in the [code area](#glossary-code-area), nor in the [heap](#glossary-image-heap), nor [debug info](#glossary-debug-info).
This data typically contains internal information for Native Image and should not be dominating.

## Resource Usage Statistics

#### <a name="glossary-garbage-collection"></a>Garbage Collections
The total time spent in all garbage collectors, total GC time divided by the total process time as a percentage, and the total number of garbage collections.
A large number of collections or time spent in collectors usually indicates that the system is under memory pressure.
Increase the amount of available memory to reduce the time to build the native binary.

#### <a name="glossary-peak-rss"></a>Peak RSS
Peak [resident set size](https://en.wikipedia.org/wiki/Resident_set_size) as reported by the operating system.
This value indicates the maximum amount of memory consumed by the build process.
If the [GC statistics](#glossary-garbage-collection) do not show any problems, the amount of available memory of the system can be reduced to a value closer to the peak RSS.

#### <a name="glossary-cpu-load"></a>CPU load
The CPU time used by the process divided by the total process time.
Increase the number of CPU cores to reduce the time to build the native binary.

## Machine-Readable Build Output

The build output produced by the `native-image` builder is designed for humans, can evolve with new releases, and should thus not be parsed in any way by tools.
Instead, use the `-H:BuildOutputJSONFile=<file.json>` option to instruct the builder to produce machine-readable build output in JSON format that can be used, for example, for building monitoring tools.
The JSON files validate against the JSON schema defined in [`build-output-schema-v0.9.1.json`](https://github.com/oracle/graal/tree/master/docs/reference-manual/native-image/assets/build-output-schema-v0.9.1.json).
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

## Related Documentation

- [Build a Native Shared Library](guides/build-native-shared-library.md)
- [Build a Statically Linked or Mostly-Statically Linked Native Executable](guides/build-static-and-mostly-static-executable.md)
- [Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html)
- [Interoperability with Native Code](InteropWithNativeCode.md)
- [Java Native Interface (JNI) in Native Image](JNI.md)
- [Memory Management](MemoryManagement.md)
- [Native Image Build Overview](BuildOverview.md)
- [Native Image Build Configuration](BuildConfiguration.md)
