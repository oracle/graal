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
[1/8] Initializing...                                            (2.5s @ 0.21GB)
 Version info: 'GraalVM dev Java 11 CE'
 C compiler: gcc (linux, x86_64, 9.3.0)
 Garbage collector: Serial GC
[2/8] Performing analysis...  [*******]                          (5.6s @ 0.46GB)
   2,718 (72.93%) of  3,727 types reachable
   3,442 (53.43%) of  6,442 fields reachable
  12,128 (44.82%) of 27,058 methods reachable
      27 types,     0 fields, and   271 methods registered for reflection
      58 types,    59 fields, and    52 methods registered for JNI access
       4 native libraries: dl, pthread, rt, z
[3/8] Building universe...                                       (0.5s @ 0.61GB)
[4/8] Parsing methods...      [*]                                (0.5s @ 0.86GB)
[5/8] Inlining methods...     [****]                             (0.5s @ 0.73GB)
[6/8] Compiling methods...    [**]                               (3.7s @ 2.38GB)
[7/8] Layouting methods...    [*]                                (0.5s @ 0.71GB)
[8/8] Creating image...                                          (2.1s @ 1.04GB)
   4.00MB (28.31%) for code area:     7,073 compilation units
   5.90MB (41.70%) for image heap:   83,319 objects and 5 resources
   3.24MB (22.91%) for debug info generated in 1.0s
   1.00MB ( 7.08%) for other data
  14.15MB in total
--------------------------------------------------------------------------------
Top 10 packages in code area:           Top 10 object types in image heap:
 632.68KB java.util                      871.62KB byte[] for code metadata
 324.42KB java.lang                      798.53KB java.lang.String
 223.90KB java.util.regex                774.91KB byte[] for general heap data
 221.62KB java.text                      614.06KB java.lang.Class
 198.30KB com.oracle.svm.jni             492.51KB byte[] for java.lang.String
 166.02KB java.util.concurrent           314.81KB java.util.HashMap$Node
 115.44KB java.math                      233.58KB c.o.s.c.h.DynamicHubCompanion
  98.48KB sun.text.normalizer            154.84KB java.lang.String[]
  97.42KB java.util.logging              139.54KB byte[] for embedded resources
  95.18KB c.oracle.svm.core.genscavenge  139.04KB char[]
   1.83MB for 118 more packages            1.29MB for 753 more object types
--------------------------------------------------------------------------------
    0.9s (5.6% of total time) in 17 GCs | Peak RSS: 3.22GB | CPU load: 10.87
--------------------------------------------------------------------------------
Produced artifacts:
 /home/janedoe/helloworld/helloworld (executable)
 /home/janedoe/helloworld/sources (debug_info)
 /home/janedoe/helloworld/helloworld (debug_info)
 /home/janedoe/helloworld/helloworld.build_artifacts.txt
================================================================================
Finished generating 'helloworld' in 16.2s.
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

## Build Output Options

Run `native-image --expert-options-all | grep "BuildOutput"` to see all build output options:

```
-H:±BuildOutputBreakdowns    Show code and heap breakdowns as part of the build output. Default: + (enabled).
-H:±BuildOutputColorful      Colorize build output. Default: + (enabled).
-H:±BuildOutputGCWarnings    Print GC warnings as part of build output. Default: + (enabled).
-H:BuildOutputJSONFile=""    Print build output statistics as JSON to the specified file. The output is according to the JSON schema located at:
                             docs/reference-manual/native-image/assets/build-output-schema-v0.9.1.json.
-H:±BuildOutputLinks         Show links in build output. Default: + (enabled).
-H:±BuildOutputPrefix        Prefix build output with '<pid>:<name of binary>'. Default: - (disabled).
-H:±BuildOutputProgress      Report progress in build output. Default: + (enabled).
-H:±BuildOutputSilent        Silence build output. Default: - (disabled).
```

### Related Documentation

- [Build a Native Shared Library](guides/build-native-shared-library.md)
- [Build a Statically Linked or Mostly-Statically Linked Native Executable](guides/build-static-and-mostly-static-executable.md)
- [Feature](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html)
- [Interoperability with Native Code](InteropWithNativeCode.md)
- [Java Native Interface (JNI) in Native Image](JNI.md)
- [Memory Management](MemoryManagement.md)
- [Native Image Build Overview](BuildOverview.md)
- [Native Image Build Configuration](BuildConfiguration.md)
