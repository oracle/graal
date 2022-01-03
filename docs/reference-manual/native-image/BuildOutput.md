---
layout: docs
toc_group: native-image
link_title: Build Output
permalink: /reference-manual/native-image/BuildOutput/
---
# Native Image Build Output

This page provides documentation for the build output of GraalVM Native Image.

## HelloWorld Example Output
```text
================================================================================
GraalVM Native Image: Generating 'helloworld'...
================================================================================
[1/7] Initializing...                                            (2.5s @ 0.21GB)
 Version info: 'GraalVM dev Java 11 CE'
[2/7] Performing analysis...  [*******]                          (5.6s @ 0.46GB)
   2,565 (82.61%) of  3,105 classes reachable
   3,216 (60.42%) of  5,323 fields reachable
  11,652 (72.44%) of 16,086 methods reachable
      27 classes,     0 fields, and   135 methods registered for reflection
      57 classes,    59 fields, and    51 methods registered for JNI access
[3/7] Building universe...                                       (0.5s @ 0.61GB)
[4/7] Parsing methods...      [*]                                (0.5s @ 0.86GB)
[5/7] Inlining methods...     [****]                             (0.5s @ 0.73GB)
[6/7] Compiling methods...    [**]                               (3.7s @ 2.38GB)
[7/7] Creating image...                                          (2.1s @ 1.04GB)
   3.69MB (27.19%) for code area:    6,955 compilation units
   5.86MB (43.18%) for image heap:   1,545 classes and 80,528 objects
   3.05MB (22.46%) for debug info generated in 1.0s
 997.25KB ( 7.18%) for other data
  13.57MB in total
--------------------------------------------------------------------------------
Top 10 packages in code area:           Top 10 object types in image heap:
 606.23KB java.util                        1.64MB byte[] for general heap data
 282.34KB java.lang                      715.56KB java.lang.String
 222.47KB java.util.regex                549.46KB java.lang.Class
 219.55KB java.text                      451.79KB byte[] for java.lang.String
 193.17KB com.oracle.svm.jni             363.23KB java.util.HashMap$Node
 149.80KB java.util.concurrent           192.00KB java.util.HashMap$Node[]
 118.07KB java.math                      139.83KB java.lang.String[]
 103.60KB com.oracle.svm.core.reflect    139.04KB char[]
  97.83KB sun.text.normalizer            130.59KB j.u.c.ConcurrentHashMap$Node
  88.78KB c.oracle.svm.core.genscavenge  103.92KB s.u.l.LocaleObjec~e$CacheEntry
      ... 111 additional packages             ... 723 additional object types
                       (use GraalVM Dashboard to see all)
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
In this stage, the Native Image build process is set up and [`Features`][jdoc_feature] are initialized.

#### <a name="glossary-version-info"></a>Version Info
The version info of the Native Image process.
This string is also used for the `java.vm.version` property within the generated image.
Please report this version info when you [file issues][new_issue].

#### <a name="glossary-user-provided-features"></a>User-provided Features
All [`Features`][jdoc_feature] that are provided by the user or implicitly registered for the user, for example, by a framework.
GraalVM Native Image deploys a number of internal features, which are excluded from this list.

### <a name="stage-analysis"></a>Performing Analysis
In this stage, a [points-to analysis][oopsla19_initialize_once_start_fast] is performed.
The progress indicator visualizes the number of analysis iterations.
A large number of iterations can indicate problems in the analysis likely caused by misconfiguration or a misbehaving feature.

#### <a name="glossary-reachability"></a>Reachable Classes, Fields, and Methods
The number of classes, fields, and methods that are reachable versus the total number of classes and methods loaded as part of the build process.
A significantly larger number of loaded classes that are not reachable can indicate a configuration problem.
To reduce overhead, please ensure that the classpath only contains entries that are needed for building the application.

#### <a name="glossary-reflection-registrations"></a>Reflection Registrations
The number of classes, fields, and methods that are registered for reflection.
Large numbers can cause significant reflection overheads, slow down the build process, and increase the size of the native image (see [method metadata](#glossary-method-metadata)).

#### <a name="glossary-jni-access-registrations"></a>JNI Access Registrations
The number of classes, fields, and methods that are registered for [JNI][doc_jni] access.

#### <a name="glossary-runtime-methods"></a>Runtime Compiled Methods
The number of methods marked for runtime compilation.
This number is only shown if runtime compilation is built into the image, for example, when building a [Truffle][truffle] language.
Runtime compiled methods account for [graph encodings](#glossary-graph-encodings) in the image heap.

### <a name="stage-universe"></a>Building Universe
In this stage, a universe with all classes, fields, and methods is built, which is then used to create the native image.

### <a name="stage-parsing"></a>Parsing Methods
In this stage, the Graal compiler parses all reachable methods.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-inlining"></a>Inlining Methods
In this stage, trivial method inlining is performed.
The progress indicator visualizes the number of inlining iterations.

### <a name="stage-compiling"></a>Compiling Methods
In this stage, the Graal compiler compiles all reachable methods to machine code.
The progress indicator is printed periodically at an increasing interval.

### <a name="stage-creating"></a>Creating Image
In this stage, the native image is created and written to disk.
Debug info is also generated as part of this stage (if requested).

#### <a name="glossary-code-area"></a>Code Area
The code area contains machine code produced by the Graal compiler for all reachable methods.
Therefore, reducing the number of reachable methods also reduces the size of the code area.

#### <a name="glossary-image-heap"></a>Image Heap
The image heap contains reachable objects such as static data, classes initialized at run-time, and `byte[]` for different purposes.

##### <a name="glossary-general-heap-data"></a>General Heap Data Stored in `byte[]`
The total size of all `byte[]` objects that are neither used for `java.lang.String`, nor [graph encodings](#glossary-graph-encodings), nor [method metadata](#glossary-method-metadata).
This typically dominates

##### <a name="glossary-graph-encodings"></a>Graph Encodings Stored in `byte[]`
The total size of all `byte[]` objects used for graph encodings.
These encodings are a result of [runtime compiled methods](#glossary-runtime-methods).
Therefore, reducing the number of such methods also reduces the size of corresponding graph encodings.

##### <a name="glossary-method-metadata"></a>Method Metadata Stored in `byte[]`
The total size of all `byte[]` objects used for method metadata, a type of reflection metadata.
To reduce the amount of method metadata, reduce the number of [classes registered for reflection](#glossary-reflection-classes).

#### <a name="glossary-debug-info"></a>Debug Info
The total size of generated debug information (if enabled).

#### <a name="glossary-other-data"></a>Other Data
The amount of data in the image that is neither in the [code area](#glossary-code-area), nor in the [image heap](#glossary-image-heap), nor [debug info](#glossary-debug-info).
This data typically contains internal information for Native Image and should not be dominating.

### Resource Usage Statistics

#### <a name="glossary-garbage-collection"></a>Garbage Collections
The total time spent in all garbage collectors, total GC time divided by the total process time in percent, and the total number of garbage collections.
A large number of collections or time spent in collectors usually indicates that the system is under memory pressure.
Increase the amount of available memory to reduce the time to build the image.

#### <a name="glossary-peak-rss"></a>Peak RSS
Peak [resident set size][rss_wiki] as reported by the operating system.
This value indicates the maximum amount of memory consumed by the build process.
If the [GC statistics](#glossary-garbage-collection) do not show any problems, the amount of available memory of the system can be reduced to a value closer to the peak RSS.

#### <a name="glossary-cpu-load"></a>CPU load
The CPU time used by the process divided by the total process time.
Increase the number of CPU threads to reduce the time to build the image.

## Build Output Options

Run `native-image --expert-options-all | grep "BuildOutput"` to see all build output options:

```
-H:±BuildOutputBreakdowns    Show code and heap breakdowns as part of the build output. Default: + (enabled).
-H:±BuildOutputColorful      Colorize build output. Default: + (enabled).
-H:±BuildOutputGCWarnings    Print GC warnings as part of build output. Default: + (enabled).
-H:±BuildOutputLinks         Show links in build output. Default: + (enabled).
-H:±BuildOutputPrefix        Prefix build output with '<pid>:<image name>'. Default: - (disabled).
-H:±BuildOutputProgress      Report progress in build output. Default: + (enabled).
-H:±BuildOutputUseNewStyle   Use new build output style. Default: + (enabled).
```


[jdoc_feature]: https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html
[doc_jni]: https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/JNI.md
[new_issue]: https://github.com/oracle/graal/issues/new
[oopsla19_initialize_once_start_fast]: https://dl.acm.org/doi/10.1145/3360610
[rss_wiki]: https://en.wikipedia.org/wiki/Resident_set_size
[truffle]: https://github.com/oracle/graal/tree/master/truffle
